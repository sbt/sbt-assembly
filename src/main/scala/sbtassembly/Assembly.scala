package sbtassembly

import sbt._
import Keys._
import Path.relativeTo

import java.security.MessageDigest
import java.io.{File, IOException}
import Def.Initialize
import PluginCompat._
import com.eed3si9n.jarjarabrams._

import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.collection.parallel.immutable.ParVector


object Assembly {
  import AssemblyPlugin.autoImport.{ Assembly => _, _ }

  // used for contraband
  type SeqFileToSeqFile = Seq[File] => Seq[File]
  type SeqString = Seq[String]
  type SeqShadeRules = Seq[com.eed3si9n.jarjarabrams.ShadeRule]

  private val scalaPre213Libraries = Vector(
    "scala-actors",
    "scala-compiler",
    "scala-continuations",
    "scala-library",
    "scala-parser-combinators",
    "scala-reflect",
    "scala-swing",
    "scala-xml")
  private val scala213AndLaterLibraries = Vector(
    "scala-actors",
    "scala-compiler",
    "scala-continuations",
    "scala-library",
    "scala-reflect")

  val defaultExcludedFiles: Seq[File] => Seq[File] = (base: Seq[File]) => Nil
  val defaultShadeRules: Seq[com.eed3si9n.jarjarabrams.ShadeRule] = Nil

  def apply(out0: File, ao: AssemblyOption, po: Seq[PackageOption], mappings: Seq[MappingSet],
      cacheDir: File, log: Logger): File = {
    import Tracked.{ inputChanged, outputChanged }
    import Cache._
    import FileInfo.{hash, exists}
    import java.util.jar.{Attributes, Manifest}

    lazy val (ms: Vector[(File, String)], stratMapping: List[(String, MergeStrategy)]) = {
      log.debug("Merging files...")
      applyStrategies(mappings, ao.mergeStrategy, ao.assemblyDirectory.get, log)
    }
    def makeJar(outPath: File): Unit = {
      import Package._
      import collection.JavaConverters._
      import scala.language.reflectiveCalls
      val manifest = new Manifest
      val main = manifest.getMainAttributes.asScala
      var time: Option[Long] = None
      for(option <- po) {
        option match {
          case JarManifest(mergeManifest)     => Package.mergeManifests(manifest, mergeManifest)
          case MainClass(mainClassName)       => main.put(Attributes.Name.MAIN_CLASS, mainClassName)
          case ManifestAttributes(attrs @ _*) => main ++= attrs
          case _                              =>
            // use reflection for compatibility
            if (option.getClass.getName == "sbt.Package$FixedTimestamp") {
              try {
                // https://github.com/sbt/sbt/blob/59130d4703e9238e/main-actions/src/main/scala/sbt/Package.scala#L50
                time = option.asInstanceOf[{def value: Option[Long]}].value
              } catch {
                case e: Throwable =>
                  log.debug(e.toString)
              }
            } else {
              log.warn("Ignored unknown package option " + option)
            }
        }
      }
      if (time.isEmpty) {
        Package.makeJar(ms, outPath, manifest, log)
      } else {
        try {
          // https://github.com/sbt/sbt/blob/59130d4703e9238e/main-actions/src/main/scala/sbt/Package.scala#L213-L219
          Package.asInstanceOf[ {
            def makeJar(
              sources: Seq[(File, String)],
              jar: File,
              manifest: Manifest,
              log: Logger,
              time: Option[Long]
            ): Unit
          }].makeJar(
            sources = ms,
            jar = outPath,
            manifest = manifest,
            log = log,
            time = time
          )
        } catch {
          case e: Throwable =>
            log.debug(e.toString)
        }
      }
      ao.prependShellScript foreach { shellScript: Seq[String] =>
        val tmpFile = cacheDir / "assemblyExec.tmp"
        if (tmpFile.exists()) tmpFile.delete()
        val jarCopy = IO.copyFile(outPath, tmpFile)
        IO.write(outPath, shellScript.map(_+"\n").mkString, append = false)

        Using.fileOutputStream(true)(outPath) { out => IO.transfer(tmpFile, out) }
        tmpFile.delete()

        try {
          sys.process.Process("chmod", Seq("+x", outPath.toString)).!
        }
        catch {
          case e: IOException => log.warn("Could not run 'chmod +x' on jarfile. Perhaps chmod command is not available?")
        }
      }
    }
    lazy val inputs = {
      log.debug("Checking every *.class/*.jar file's SHA-1.")
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
    lazy val out = if (ao.appendContentHash) doAppendContentHash(inputs, out0, log, ao.maxHashLength)
                   else out0
    import CacheImplicits._
    val cachedMakeJar = inputChanged(cacheDir / "assembly-inputs") { (inChanged, inputs: Seq[Byte]) =>
      outputChanged(cacheDir / "assembly-outputs") { (outChanged, jar: PlainFileInfo) =>
        if (inChanged) {
          log.debug("SHA-1: " + bytesToString(inputs))
        } // if
        if (inChanged || outChanged) makeJar(out)
        else log.info("Assembly up to date: " + jar.file)
      }
    }
    if (ao.cacheOutput) cachedMakeJar(inputs)(() => exists(out))
    else makeJar(out)
    out
  }

  private def doAppendContentHash(inputs: Seq[Byte], out0: File, log: Logger, maxHashLength: Option[Int]) = {
    val fullSha1 = bytesToString(inputs)
    val sha1 = maxHashLength.fold(fullSha1)(length => fullSha1.take(length))
    val newName = out0.getName.replaceAll("\\.[^.]*$", "") + "-" +  sha1 + ".jar"
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
  def assembleMappings(
      classpath: Classpath,
      dependencies: Classpath,
      ao: AssemblyOption,
      log: Logger,
      state: State
  ): Vector[MappingSet] = {
    val assemblyDir = ao.assemblyDirectory.get
    val assemblyUnzipDir = ao.assemblyUnzipDirectory.getOrElse(assemblyDir)
    val projectIdMsg: String = getProjectIdMsg(state)

    if (!ao.cacheOutput && assemblyDir.exists) {
      log.info(s"AssemblyOption.cacheOutput set to false, deleting assemblyDirectory: $assemblyDir for project: $projectIdMsg")
      IO.delete(assemblyDir)
    }

    for {
      unzipDir <- ao.assemblyUnzipDirectory
      if !ao.cacheUnzip
      if unzipDir.exists
    } {
      log.info(s"AssemblyOption.cacheUnzip set to false, deleting assemblyUnzipDirectory: $unzipDir for project: $projectIdMsg")
      IO.delete(unzipDir)
    }

    if (!assemblyDir.exists) IO.createDirectory(assemblyDir)
    if (!assemblyUnzipDir.exists) IO.createDirectory(assemblyUnzipDir)

    val (libsFiltered: Vector[Attributed[File]], dirs: Vector[Attributed[File]]) = getFilteredLibsAndDirs(
      classpath = classpath,
      dependencies = dependencies,
      assemblyOption = ao
    )

    val dirRules: Seq[ShadeRule] = ao.shadeRules.filter(_.isApplicableToCompiling)
    val dirsFiltered: ParVector[File] =
      dirs.par flatMap {
        case dir =>
          if (ao.includeBin) Some(dir)
          else None
      } map { dir =>
        val hash = sha1name(dir.data)
        IO.write(assemblyDir / (hash + "_dir.dir"), dir.data.getCanonicalPath, IO.utf8, false)
        val dest = assemblyDir / (hash + "_dir")
        if (dest.exists) {
          IO.delete(dest)
        }
        IO.createDirectory(dest)
        IO.copyDirectory(dir.data, dest)
        if (dirRules.nonEmpty) {
          val mappings = ((dest ** (-DirectoryFilter)).get  pair relativeTo(dest)) map {
            case (k, v) => k.toPath -> v
          }
          Shader.shadeDirectory(dirRules, dest.toPath, mappings, ao.level == Level.Debug)
        }
        dest
      }

    val jarDirs: ParVector[(File, File)] = processDependencyJars(
      libsFiltered,
      ao,
      isCacheOnly = false,
      log,
      state
    )

    log.info("Calculate mappings...")
    val base: Vector[File] = dirsFiltered.seq ++ (jarDirs map { _._1 })
    val excluded: Set[File] = (ao.excludedFiles(base) ++ base).toSet
    val retval: Vector[MappingSet] = (dirsFiltered map { d => MappingSet(None, AssemblyUtils.getMappings(d, excluded)) }).seq ++
                 (jarDirs map { case (d, j) => MappingSet(Some(j), AssemblyUtils.getMappings(d, excluded)) })
    retval
  }

  def assemblyCacheDependency(
    classpath: Classpath,
    dependencies: Classpath,
    assemblyOption: AssemblyOption,
    log: Logger,
    state: State
  ): Boolean = {
    if (!assemblyOption.cacheUnzip) sys.error("AssemblyOption.cacheUnzip must be true")
    if (assemblyOption.assemblyUnzipDirectory.isEmpty) sys.error("AssemblyOption.assemblyUnzipDirectory must be supplied")

    val (libsFiltered: Vector[Attributed[File]], _) = getFilteredLibsAndDirs(
      classpath = classpath,
      dependencies = dependencies,
      assemblyOption = assemblyOption
    )

    processDependencyJars(libsFiltered, assemblyOption, isCacheOnly = true, log, state)

    true
  }

  def assemblyTask(key: TaskKey[File]): Initialize[Task[File]] = Def.task {
    // Run tests if enabled before assembly task
    val _ = (test in key).value

    val s = (streams in key).value
    Assembly(
      (assemblyOutputPath in key).value,
      (assemblyOption in key).value,
      (packageOptions in key).value,
      (assembledMappings in key).value,
      s.cacheDirectory,
      s.log
    )
  }

  def assembledMappingsTask(key: TaskKey[File]): Initialize[Task[Seq[MappingSet]]] = Def.task {
    val s = (streams in key).value
    assembleMappings(
      (fullClasspath in assembly).value,
      (externalDependencyClasspath in assembly).value,
      (assemblyOption in key).value,
      s.log,
      state.value
    )
  }

  def assemblyCacheDependencyTask(key: TaskKey[File]): Initialize[Task[Boolean]] = Def.task {
    val s = (streams in key).value
    val ao = (assemblyOption in key).value
    val cp = (fullClasspath in assembly).value
    val deps = (externalDependencyClasspath in assembly).value
    val currentState = state.value
    val projectIdMsg: String = getProjectIdMsg(currentState)

    if (!ao.cacheUnzip || ao.assemblyUnzipDirectory.isEmpty) {
      if (!ao.cacheUnzip) s.log.warn(s"AssemblyOption.cacheUnzip must be true. Skipping unzip task for projectID: $projectIdMsg.")
      if (ao.assemblyUnzipDirectory.isEmpty) s.log.warn(s"AssemblyOption.assemblyUnzipDirectory must be be supplied. Skipping cache unzip task for projectID: $projectIdMsg")
      false
    } else {
      assemblyCacheDependency(classpath = cp, dependencies = deps, ao, s.log, currentState)
    }
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
      case "reference.conf" | "reference-overrides.conf" | "application.conf" | "rootdoc.txt" | "play.plugins" => true
      case _ => false
    }

  def isScalaLibraryFile(scalaLibraries: Vector[String], file: File): Boolean =
    scalaLibraries exists { x => file.getName startsWith x }

  private[sbtassembly] def getProjectIdMsg(state: State): String = {
    val project = Project.extract(state)

    val projectName = project.get(Keys.projectID).name
    val currentRefProjectName = project.currentRef.project

    if (projectName != currentRefProjectName) s"$projectName/$currentRefProjectName"
    else projectName
  }

  private[sbtassembly] def processDependencyJars(
    libsFiltered: Vector[Attributed[File]],
    assemblyOption: AssemblyOption,
    isCacheOnly: Boolean,
    log: Logger,
    state: State
  ): ParVector[(File, File)] = {
    val defaultAssemblyDir = assemblyOption.assemblyDirectory.get
    val assemblyUnzipDir: File = assemblyOption.assemblyUnzipDirectory.getOrElse(defaultAssemblyDir)
    val assemblyDir: Option[File] = if (isCacheOnly) None else Some(defaultAssemblyDir)
    val isSameDir: Boolean = assemblyDir.exists{ _ == assemblyUnzipDir }

    if (!assemblyUnzipDir.exists) IO.createDirectory(assemblyUnzipDir)
    if (assemblyDir.isDefined && !assemblyDir.get.exists) IO.createDirectory(assemblyDir.get)

    state.locked(assemblyUnzipDir / "sbt-assembly.lock") {

      val projectIdMsg: String = getProjectIdMsg(state)

      if (!assemblyUnzipDir.exists) IO.createDirectory(assemblyUnzipDir)
      if (assemblyDir.isDefined && !assemblyDir.get.exists) IO.createDirectory(assemblyDir.get)

      val unzippingIntoMessage: String = if (isCacheOnly && !isSameDir) "unzip cache" else "output cache"

      val useHardLinks: Boolean = assemblyOption.cacheUseHardLinks && !isCacheOnly && {
        if (isSameDir) {
          log.warn(s"cacheUseHardLinks is enabled for project ($projectIdMsg), but assemblyUnzipDirectory is the same as assemblyDirectory ($assemblyUnzipDirectory)")
          false
        } else {
          val isHardLinkSupported = AssemblyUtils.isHardLinkSupported(sourceDir = assemblyUnzipDir, destDir = assemblyDir.get)
          if (!isHardLinkSupported) log.warn(s"cacheUseHardLinks is enabled for project ($projectIdMsg), but file system doesn't support hardlinks between from $assemblyUnzipDir to ${assemblyDir.get}")
          isHardLinkSupported
        }
      }

      // Ensure we are not processing the same File twice, retain original ordering
      val jarToAttributedFiles: ListMap[File, Vector[Attributed[File]]] =
        libsFiltered
          .foldLeft(ListMap.empty[File, Vector[Attributed[File]]]) {
            case (lm, f) =>
              val canonicalJar = f.data.getCanonicalFile
              lm.updated(canonicalJar, f +: lm.getOrElse(canonicalJar, Vector.empty))
          }

      for {
        jar: File <- jarToAttributedFiles.keys.toVector.par
        jarName = jar.asFile.getName
        jarRules = assemblyOption.shadeRules
          .filter { r =>
            r.isApplicableToAll ||
              jarToAttributedFiles.getOrElse(jar, Vector.empty)
                .flatMap(_.metadata.get(moduleID.key))
                .map(m => ModuleCoordinate(m.organization, m.name, m.revision))
                .exists(r.isApplicableTo)
          }
        hash = sha1name(jar) + "_" + sha1content(jar) + "_" + sha1rules(jarRules)
        jarOutputDir = (assemblyDir.getOrElse(assemblyUnzipDir) / hash).getCanonicalFile
      } yield {
        // TODO: Canonical path might be problematic if mount points inside docker are different
        val jarNameFinalPath = new File(jarOutputDir + ".jarName")

        val jarNameCachePath = assemblyUnzipDir / (hash + ".jarName")
        val jarCacheDir = assemblyUnzipDir / hash

        // If the jar name path does not exist, or is not for this jar, unzip the jar
        if (!jarNameFinalPath.exists || IO.read(jarNameFinalPath) != jar.getCanonicalPath) {
          log.info("Including: %s, for project: %s".format(jarName, projectIdMsg))

          // Copy/Link from cache location if cache exists and is current
          if (assemblyOption.cacheUnzip &&
            jarNameCachePath.exists && IO.read(jarNameCachePath) == jar.getCanonicalPath &&
            !jarNameFinalPath.exists
          //(!jarNameFinalPath.exists || IO.read(jarNameFinalPath) != jar.getCanonicalPath)
          ) {
            if (useHardLinks) {
              log.info("Creating hardlinks of %s from unzip cache: %s, to: %s, for project: %s".format(jarName, jarCacheDir, jarOutputDir, projectIdMsg))
              AssemblyUtils.copyDirectory(jarCacheDir, jarOutputDir, hardLink = true)
            } else {
              log.info("Copying %s from unzip cache: %s, to: %s, for project: %s".format(jarName, jarCacheDir, jarOutputDir, projectIdMsg))
              AssemblyUtils.copyDirectory(jarCacheDir, jarOutputDir, hardLink = false)
            }
            AssemblyUtils.copyDirectory(jarCacheDir, jarOutputDir, hardLink = useHardLinks)
            IO.delete(jarNameFinalPath) // write after merge/shade rules applied
            // Unzip into cache dir and copy over
          } else if (assemblyOption.cacheUnzip && jarNameFinalPath != jarNameCachePath) {
            IO.delete(jarCacheDir)
            IO.delete(jarOutputDir)

            IO.createDirectory(jarCacheDir)
            IO.createDirectory(jarOutputDir)

            log.info("Unzipping %s into unzip cache: %s for project: %s".format(jarName, jarCacheDir, projectIdMsg))
            val files = AssemblyUtils.unzip(jar, jarCacheDir, log)

            // TODO: This is kind of a hack, but doing it seems to prevent a docker file system issue preventing
            //       FileNotFound exception after unzipping
            files.foreach { f =>
              assert(f.exists(), s"File $f not found after unzipping $jar into $jarCacheDir!")
            }

            if (useHardLinks) log.info("Creating hardlinks of %s from unzip cache: %s, to: %s, for project: %s".format(jarName, jarCacheDir, jarOutputDir, projectIdMsg))
            else log.info("Copying %s from unzip cache: %s, to: %s, for project: %s".format(jarName, jarCacheDir, jarOutputDir, projectIdMsg))
            AssemblyUtils.copyDirectory(jarCacheDir, jarOutputDir, hardLink = useHardLinks)
            // Don't use cache dir, just unzip to output cache
          } else {
            IO.delete(jarOutputDir)
            IO.createDirectory(jarOutputDir)
            log.info("Unzipping %s into %s: %s, for project: %s".format(jarName, unzippingIntoMessage, jarOutputDir, projectIdMsg))
            AssemblyUtils.unzip(jar, jarOutputDir, log)
          }

          if (!isCacheOnly) {
            IO.delete(assemblyOption.excludedFiles(Seq(jarOutputDir)))
            if (jarRules.nonEmpty) {
              val mappings = ((jarOutputDir ** (-DirectoryFilter)).get pair relativeTo(jarOutputDir)) map {
                case (k, v) => k.toPath -> v
              }
              val dirRules: Seq[ShadeRule] = assemblyOption.shadeRules.filter(_.isApplicableToCompiling)
              Shader.shadeDirectory(dirRules, jarOutputDir.toPath, mappings, assemblyOption.level == Level.Debug)
            }
          }

          // Write the jarNamePath at the end to minimise the chance of having a
          // corrupt cache if the user aborts the build midway through
          if (jarNameFinalPath != jarNameCachePath && !jarNameCachePath.exists)
            IO.write(jarNameCachePath, jar.getCanonicalPath, IO.utf8, false)

          IO.write(jarNameFinalPath, jar.getCanonicalPath, IO.utf8, false)
        } else {
          if (isCacheOnly) log.info("Unzip cache of %s is up to date, for project: %s".format(jarName, projectIdMsg))
          else log.info("Including %s from output cache: %s, for project: %s".format(jarName, jarOutputDir, projectIdMsg))
        }
        (jarOutputDir, jar)
      }
    }
  }

  private[sbtassembly] def getFilteredLibsAndDirs(
    classpath: Classpath,
    dependencies: Classpath,
    assemblyOption: AssemblyOption
  ): (Vector[Attributed[File]], Vector[Attributed[File]]) = {
    val (libs: Vector[Attributed[File]], dirs: Vector[Attributed[File]]) =
      classpath.toVector.sortBy(_.data.getCanonicalPath).partition(c => ClasspathUtilities.isArchive(c.data))

    val depLibs: Set[File]      = dependencies.map(_.data).toSet.filter(ClasspathUtilities.isArchive)
    val excludedJars: Seq[File] = assemblyOption.excludedJars map {_.data}

    val scalaLibraries: Vector[String] = {
      val scalaVersionParts = VersionNumber(assemblyOption.scalaVersion)
      val isScala213AndLater = scalaVersionParts.numbers.length>=2 && scalaVersionParts._1.get>=2 && scalaVersionParts._2.get>=13
      if (isScala213AndLater) scala213AndLaterLibraries else scalaPre213Libraries
    }

    val libsFiltered: Vector[Attributed[File]] = libs flatMap {
      case jar if excludedJars contains jar.data.asFile => None
      case jar if isScalaLibraryFile(scalaLibraries, jar.data.asFile) =>
        if (assemblyOption.includeScala) Some(jar) else None
      case jar if depLibs contains jar.data.asFile =>
        if (assemblyOption.includeDependency) Some(jar) else None
      case jar =>
        if (assemblyOption.includeBin) Some(jar) else None
    }

    (libsFiltered, dirs)
  }

  private[sbtassembly] def sha1 = MessageDigest.getInstance("SHA-1")
  private[sbtassembly] def sha1content(f: File): String = {
    Using.fileInputStream(f) { in =>
      val messageDigest = sha1
      val buffer = new Array[Byte](8192)
      def read(): Unit = {
        val byteCount = in.read(buffer)
        if (byteCount >= 0) {
          messageDigest.update(buffer, 0, byteCount)
          read()
        }
      }
      read()
      bytesToString(messageDigest.digest())
    }
  }
  private[sbtassembly] def sha1name(f: File): String = sha1string(f.getCanonicalPath)
  private[sbtassembly] def sha1string(s: String): String = bytesToSha1String(s.getBytes("UTF-8"))
  private[sbtassembly] def sha1rules(rs: Seq[ShadeRule]): String = sha1string(rs.toList.mkString(":"))
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
