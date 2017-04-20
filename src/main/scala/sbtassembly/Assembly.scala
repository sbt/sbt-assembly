package sbtassembly

import sbt._
import Keys._
import java.security.MessageDigest
import java.io.{IOException, PrintWriter, FileOutputStream, File}
import scala.collection.mutable
import Def.Initialize

object Assembly {
  import AssemblyPlugin.autoImport.{ Assembly => _, _ }

  val defaultExcludedFiles: Seq[File] => Seq[File] = (base: Seq[File]) => Nil

  def apply(out0: File, ao: AssemblyOption, po: Seq[PackageOption], mappings: Seq[MappingSet],
      cacheDir: File, log: Logger): File = {
    import Tracked.inputChanged
    // This is a workaround for missing outputChanged
    import TrackedFix.outputChanged
    import Types.:+:
    import Cache._
    import FileInfo.{hash, exists}
    import java.util.jar.{Attributes, Manifest}

    lazy val (ms: Vector[(File, String)], stratMapping: List[(String, MergeStrategy)]) = {
      log.info("Merging files...")
      applyStrategies(mappings, ao.mergeStrategy, ao.assemblyDirectory, log)
    }
    def makeJar(outPath: File) {
      import Package._
      import collection.JavaConversions._
      val manifest = new Manifest
      val main = manifest.getMainAttributes
      for(option <- po) {
        option match {
          case JarManifest(mergeManifest)     => Package.mergeManifests(manifest, mergeManifest)
          case MainClass(mainClassName)       => main.put(Attributes.Name.MAIN_CLASS, mainClassName)
          case ManifestAttributes(attrs @ _*) => main ++= attrs
          case _                              => log.warn("Ignored unknown package option " + option)
        }
      }
      Package.makeJar(ms, outPath, manifest, log)
      ao.prependShellScript foreach { shellScript: Seq[String] =>
        val tmpFile = cacheDir / "assemblyExec.tmp"
        if (tmpFile.exists()) tmpFile.delete()
        val jarCopy = IO.copyFile(outPath, tmpFile)
        IO.write(outPath, shellScript.map(_+"\n").mkString, append = false)
        val jarBytes = IO.readBytes(tmpFile)
        tmpFile.delete()
        IO.append(outPath, jarBytes)
        try {
          sys.process.Process("chmod", Seq("+x", outPath.toString)).!
        }
        catch {
          case e: IOException => log.warn("Could not run 'chmod +x' on jarfile. Perhaps chmod command is not available?")
        }
      }
    }
    lazy val inputs = {
      log.info("Checking every *.class/*.jar file's SHA-1.")
      val rawHashBytes =
        (mappings.toVector.par flatMap { m =>
          m.sourcePackage match {
            case Some(x) => hash(x).hash
            case _       => (m.mappings map { x => hash(x._1).hash }).flatten
          }
        })
      val pathStratBytes =
        (stratMapping.par flatMap { case (path, strat) =>
          (path + strat.name).getBytes("UTF-8")
        })
      sha1.digest((rawHashBytes.seq ++ pathStratBytes.seq).toArray)
    }
    lazy val out = if (ao.appendContentHash) doAppendContentHash(inputs, out0, log)
                   else out0
    import CacheImplicits._
    val cachedMakeJar = inputChanged(cacheDir / "assembly-inputs") { (inChanged, inputs: Seq[Byte]) =>
      outputChanged(cacheDir / "assembly-outputs") { (outChanged, jar: PlainFileInfo) =>
        if (inChanged) {
          log.info("SHA-1: " + bytesToString(inputs))
        } // if
        if (inChanged || outChanged) makeJar(out)
        else log.info("Assembly up to date: " + jar.file)
      }
    }
    if (ao.cacheOutput) cachedMakeJar(inputs)(() => exists(out))
    else makeJar(out)
    out
  }

  private def doAppendContentHash(inputs: Seq[Byte], out0: File, log: Logger) = {
    val fullSha1 = bytesToString(inputs)
    val newName = out0.getName.replaceAll("\\.[^.]*$", "") + "-" +  fullSha1 + ".jar"
    new File(out0.getParentFile, newName)
  }

  def applyStrategies(srcSets: Seq[MappingSet], strats: String => MergeStrategy,
      tempDir: File, log: Logger): (Vector[(File, String)], List[(String, MergeStrategy)]) = {
    import org.scalactic._
    import org.scalactic.Accumulation._

    val srcs = srcSets.flatMap( _.mappings )
    val counts = scala.collection.mutable.Map[MergeStrategy, Int]().withDefaultValue(0)
    (tempDir * "sbtMergeTarget*").get foreach { x => IO.delete(x) }
    def applyStrategy(strategy: MergeStrategy, name: String, files: Seq[(File, String)]): Seq[(File, String)] Or ErrorMessage = {
      if (files.size >= strategy.notifyThreshold) {
        log.log(strategy.detailLogLevel, "Merging '%s' with strategy '%s'".format(name, strategy.name))
        counts(strategy) += 1
      }
      strategy((tempDir, name, files map (_._1))) match {
        case Right(f) => Good(f)
        case Left(err) => Bad(strategy.name + ": " + err)
      }
    }
    val renamed: Seq[(File, String)] = srcs.groupBy(_._2).toVector.map { case (name, files) =>
      val strategy = strats(name)
      if (strategy == MergeStrategy.rename) applyStrategy(strategy, name, files).accumulating
      else Good(files)
    }.combined match {
      case Good(g) => g.flatten
      case Bad(errs) =>
        val numErrs = errs.size
        val message = numErrs + (if (numErrs > 1) " errors were " else " error was ") + "encountered during renaming"
        log.error(message)
        throw new RuntimeException(errs.mkString("\n"))
    }
    // this step is necessary because some dirs may have been renamed above
    val cleaned: Seq[(File, String)] = renamed filter { pair =>
      (!pair._1.isDirectory) && pair._1.exists
    }
    val stratMapping = new mutable.ListBuffer[(String, MergeStrategy)]
    val mod: Seq[(File, String)] = cleaned.groupBy(_._2).toVector.sortBy(_._1).map { case (name, files) =>
      val strategy = strats(name)
      stratMapping append (name -> strategy)
      if (strategy != MergeStrategy.rename) applyStrategy(strategy, name, files).accumulating
      else Good(files)
    }.combined match {
      case Good(g) => g.flatten
      case Bad(errs) =>
        val numErrs = errs.size
        val message = numErrs + (if (numErrs > 1) " errors were " else " error was ") + "encountered during merge"
        log.error(message)
        throw new RuntimeException(errs.mkString("\n"))
    }
    counts.keysIterator.toSeq.sortBy(_.name) foreach { strat =>
      val count = counts(strat)
      log.log(strat.summaryLogLevel, "Strategy '%s' was applied to ".format(strat.name) + (count match {
        case 1 => "a file"
        case n => n.toString + " files"
      }) + (strat.detailLogLevel match {
        case Level.Debug => " (Run the task at debug level to see details)"
        case _ => ""
      }))
    }
    (mod.toVector, stratMapping.toList)
  }

  // even though fullClasspath includes deps, dependencyClasspath is needed to figure out
  // which jars exactly belong to the deps for packageDependency option.
  def assembleMappings(classpath: Classpath, dependencies: Classpath,
      ao: AssemblyOption, log: Logger): Vector[MappingSet] = {
    import sbt.internal.inc.classpath.ClasspathUtilities
    val tempDir = ao.assemblyDirectory
    if (!ao.cacheUnzip) IO.delete(tempDir)
    if (!tempDir.exists) tempDir.mkdir()

    val shadeRules = ao.shadeRules

    val (libs, dirs) = classpath.toVector.partition(c => ClasspathUtilities.isArchive(c.data))

    val depLibs      = dependencies.map(_.data).toSet.filter(ClasspathUtilities.isArchive)
    val excludedJars = ao.excludedJars map {_.data}
    val libsFiltered = (libs flatMap {
      case jar if excludedJars contains jar.data.asFile => None
      case jar if isScalaLibraryFile(jar.data.asFile) =>
        if (ao.includeScala) Some(jar) else None
      case jar if depLibs contains jar.data.asFile =>
        if (ao.includeDependency) Some(jar) else None
      case jar =>
        if (ao.includeBin) Some(jar) else None
    })
    val dirRules = shadeRules.filter(_.isApplicableToCompiling)
    val dirsFiltered =
      dirs.par flatMap {
        case dir =>
          if (ao.includeBin) Some(dir)
          else None
      } map { dir =>
        val hash = sha1name(ao, dir.data)
        IO.write(tempDir / (hash + "_dir.dir"), dir.data.getCanonicalPath, IO.utf8, false)
        val dest = tempDir / (hash + "_dir")
        if (dest.exists) {
          IO.delete(dest)
        }
        dest.mkdir()
        IO.copyDirectory(dir.data, dest)
        if (dirRules.nonEmpty) {
          Shader.shadeDirectory(dirRules, dest, log, ao.level)
        }
        dest
      }
    val jarDirs =
      (for(jar <- libsFiltered.par) yield {
        val jarName = jar.data.asFile.getName
        val jarRules = shadeRules
          .filter(r => (r.isApplicableToAll || jar.metadata.get(moduleID.key).exists(r.isApplicableTo)))
        val hash = {
          val original = sha1name(ao, jar.data) + "_" + sha1content(jar.data) + "_" + sha1rules(ao, jarRules)
          ao.maxHashLength match {
            case Some(_) => sha1SafeString(ao, original)
            case None => original
          }
        }
        val jarNamePath = tempDir / (hash + ".jarName")
        val dest = tempDir / hash
        // If the jar name path does not exist, or is not for this jar, unzip the jar
        if (!ao.cacheUnzip || !jarNamePath.exists || IO.read(jarNamePath) != jar.data.getCanonicalPath )
        {
          log.info("Including: %s".format(jarName))
          IO.delete(dest)
          dest.mkdir()
          AssemblyUtils.unzip(jar.data, dest, log)
          IO.delete(ao.excludedFiles(Seq(dest)))
          if (jarRules.nonEmpty) {
            Shader.shadeDirectory(jarRules, dest, log, ao.level)
          }

          // Write the jarNamePath at the end to minimise the chance of having a
          // corrupt cache if the user aborts the build midway through
          IO.write(jarNamePath, jar.data.getCanonicalPath, IO.utf8, false)
        }
        else log.info("Including from cache: %s".format(jarName))

        (dest, jar.data)
      })

    log.debug("Calculate mappings...")
    val base: Vector[File] = dirsFiltered.seq ++ (jarDirs map { _._1 })
    val excluded = (ao.excludedFiles(base) ++ base).toSet
    val retval = (dirsFiltered map { d => MappingSet(None, AssemblyUtils.getMappings(d, excluded)) }).seq ++
                 (jarDirs map { case (d, j) => MappingSet(Some(j), AssemblyUtils.getMappings(d, excluded)) })
    retval.toVector
  }

  def assemblyTask(key: TaskKey[File]): Initialize[Task[File]] = Def.task {
    val t = (test in key).value
    val s = (streams in key).value
    Assembly(
      (assemblyOutputPath in key).value, (assemblyOption in key).value,
      (packageOptions in key).value, (assembledMappings in key).value,
      s.cacheDirectory, s.log)
  }
  def assembledMappingsTask(key: TaskKey[File]): Initialize[Task[Seq[MappingSet]]] = Def.task {
    val s = (streams in key).value
    assembleMappings(
      (fullClasspath in assembly).value, (externalDependencyClasspath in assembly).value,
      (assemblyOption in key).value, s.log)
  }

  def isSystemJunkFile(fileName: String): Boolean =
    fileName.toLowerCase match {
      case ".ds_store" | "thumbs.db" => true
      case _ => false
    }

  def isLicenseFile(fileName: String): Boolean = {
    val LicenseFile = """(license|licence|notice|copying)([.]\w+)?$""".r
    fileName.toLowerCase match {
      case LicenseFile(_, ext) if ext != ".class" => true // DISLIKE
      case _ => false
    }
  }

  def isReadme(fileName: String): Boolean = {
    val ReadMe = """(readme|about)([.]\w+)?$""".r
    fileName.toLowerCase match {
      case ReadMe(_, ext) if ext != ".class" => true
      case _ => false
    }
  }

  def isConfigFile(fileName: String): Boolean =
    fileName.toLowerCase match {
      case "reference.conf" | "application.conf" | "rootdoc.txt" | "play.plugins" => true
      case _ => false
    }

  def isScalaLibraryFile(file: File): Boolean =
    Vector("scala-actors",
      "scala-compiler",
      "scala-continuations",
      "scala-library",
      "scala-parser-combinators",
      "scala-reflect",
      "scala-swing",
      "scala-xml") exists { x =>
      file.getName startsWith x
    }

  private[sbtassembly] def sha1 = MessageDigest.getInstance("SHA-1")
  private[sbtassembly] def sha1content(f: File): String  = bytesToSha1String(IO.readBytes(f))
  private[sbtassembly] def sha1name(ao: AssemblyOption, f: File): String = sha1SafeString(ao, f.getCanonicalPath)
  private[sbtassembly] def sha1string(s: String): String = bytesToSha1String(s.getBytes("UTF-8"))
  private[sbtassembly] def sha1SafeString(ao: AssemblyOption, s: String): String = {
    val sha1 = sha1string(s)
    ao.maxHashLength match {
      case Some(length) => sha1.substring(0, Math.min(sha1.length, length))
      case None => sha1
    }
  }
  private[sbtassembly] def sha1rules(ao: AssemblyOption, rs: Seq[ShadeRule]): String = sha1SafeString(ao, rs.toList.mkString(":"))
  private[sbtassembly] def bytesToSha1String(bytes: Array[Byte]): String =
    bytesToString(sha1.digest(bytes))
  private[sbtassembly] def bytesToString(bytes: Seq[Byte]): String =
    bytes map {"%02x".format(_)} mkString
}

object PathList {
  private val sysFileSep = System.getProperty("file.separator")
  def unapplySeq(path: String): Option[Seq[String]] = {
    val split = path.split(if (sysFileSep.equals( """\""")) """\\""" else sysFileSep)
    if (split.size == 0) None
    else Some(split.toList)
  }
}
