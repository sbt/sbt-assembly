package sbtassembly

import sbt._
import Keys._
import scala.collection.mutable
import scala.io.Source
import Def.Initialize
import java.io.{IOException, PrintWriter, FileOutputStream, File}
import java.security.MessageDigest

object Plugin extends sbt.Plugin {
  import AssemblyKeys._
    
  object AssemblyKeys {
    lazy val assembly          = TaskKey[File]("assembly", "Builds a single-file deployable jar.")
    lazy val packageScala      = TaskKey[File]("assembly-package-scala", "Produces the scala artifact.")
    lazy val packageDependency = TaskKey[File]("assembly-package-dependency", "Produces the dependency artifact.")
  
    lazy val assembleArtifact  = SettingKey[Boolean]("assembly-assemble-artifact", "Enables (true) or disables (false) assembling an artifact.")
    lazy val assemblyOption    = TaskKey[AssemblyOption]("assembly-option")
    lazy val jarName           = TaskKey[String]("assembly-jar-name")
    lazy val defaultJarName    = TaskKey[String]("assembly-default-jar-name")
    lazy val outputPath        = TaskKey[File]("assembly-output-path")
    lazy val excludedJars      = TaskKey[Classpath]("assembly-excluded-jars")
    lazy val assembledMappings = TaskKey[Seq[MappingSet]]("assembly-assembled-mappings")
    lazy val mergeStrategy     = SettingKey[String => MergeStrategy]("assembly-merge-strategy", "mapping from archive member path to merge strategy")
  }

  // Keep track of the source package of mappings that come from a jar, so we can
  // sha1 the jar instead of the unpacked packages when determining whether to rebuild
  case class MappingSet(sourcePackage : Option[File], mappings : Vector[(File, String)]) {
    def dependencyFiles: Vector[File] =
      sourcePackage match {
        case Some(f)  => Vector(f)
        case None     => mappings.map(_._1)
      }
  }
  
  /**
   * MergeStrategy is invoked if more than one source file is mapped to the 
   * same target path. Its arguments are the tempDir (which is deleted after
   * packaging) and the sequence of source files, and it shall return the
   * file to be included in the assembly (or throw an exception).
   */
  abstract class MergeStrategy extends Function1[(File, String, Seq[File]), Either[String, Seq[(File, String)]]] {
    def name: String
    def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]]
    def notifyThreshold = 2
    def detailLogLevel = Level.Warn
    def summaryLogLevel = Level.Warn
    final def apply(args: (File, String, Seq[File])): Either[String, Seq[(File, String)]] =
      apply(args._1, args._2, args._3)
  }

  object MergeStrategy {
    @inline def createMergeTarget(tempDir: File, path: String): File = {
      val file = new File(tempDir, "sbtMergeTarget-" + sha1string(path) + ".tmp")
      if (file.exists) {
        IO.delete(file)
      }
      file
    }
    val first: MergeStrategy = new MergeStrategy {
      val name = "first"
      def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
        Right(Seq(files.head -> path))
    }
    val last: MergeStrategy = new MergeStrategy {
      val name = "last"
      def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
        Right(Seq(files.last -> path))
    }
    val singleOrError: MergeStrategy = new MergeStrategy {
      val name = "singleOrError"
      def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
        if (files.size == 1) Right(Seq(files.head -> path))
        else Left("found multiple files for same target path:" +
          filenames(tempDir, files).mkString("\n", "\n", ""))
    }
    val concat: MergeStrategy = new MergeStrategy {
      val name = "concat"
      def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
        val file = createMergeTarget(tempDir, path)
        val out = new FileOutputStream(file)
        try {
          files foreach (f => IO.transfer(f, out))
          Right(Seq(file -> path))
        } finally {
          out.close()
        }
      }
    }
    val filterDistinctLines: MergeStrategy = new MergeStrategy {
      val name = "filterDistinctLines"
      def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
        val lines = files flatMap (IO.readLines(_, IO.utf8))
        val unique = (Vector.empty[String] /: lines)((v, l) => if (v contains l) v else v :+ l)
        val file = createMergeTarget(tempDir, path)
        IO.writeLines(file, unique, IO.utf8)
        Right(Seq(file -> path))
      }
    }
    val deduplicate: MergeStrategy = new MergeStrategy {
      val name = "deduplicate"
      def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
        if (files.size == 1) Right(Seq(files.head -> path))
        else {
          val fingerprints = Set() ++ (files map (sha1content))
          if (fingerprints.size == 1) Right(Seq(files.head -> path))
          else Left("different file contents found in the following:" +
              filenames(tempDir, files).mkString("\n", "\n", ""))
        }
      override def detailLogLevel = Level.Debug
      override def summaryLogLevel = Level.Info
    }
    val rename: MergeStrategy = new MergeStrategy {
      val name = "rename"
      def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
        Right(files flatMap { f =>
          if(!f.exists) Seq.empty
          else if(f.isDirectory && (f ** "*.class").get.nonEmpty) Seq(f -> path)
          else AssemblyUtils.sourceOfFileForMerge(tempDir, f) match {
            case (_, _, _, false) => Seq(f -> path)
            case (jar, base, p, true) =>
              val dest = new File(f.getParent, appendJarName(f.getName, jar))
              IO.move(f, dest)
              val result = Seq(dest -> appendJarName(path, jar))
              if (dest.isDirectory) ((dest ** (-DirectoryFilter))) x relativeTo(base)
              else result
          }
        })

      def appendJarName(source: String, jar: File): String =
        FileExtension.replaceFirstIn(source, "") +
          "_" + FileExtension.replaceFirstIn(jar.getName, "") +
          FileExtension.findFirstIn(source).getOrElse("")

      override def notifyThreshold = 1
    }
    val discard: MergeStrategy = new MergeStrategy {
      val name = "discard"
      def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
        Right(Nil)   
      override def notifyThreshold = 1
    }
  }
  
  private val FileExtension = """([.]\w+)$""".r

  private def filenames(tempDir: File, fs: Seq[File]): Seq[String] =
    for(f <- fs) yield {
      AssemblyUtils.sourceOfFileForMerge(tempDir, f) match {
        case (path, base, subDirPath, false) => subDirPath
        case (jar, base, subJarPath, true) => jar + ":" + subJarPath
      }
    }

  object Assembly {
    def apply(out0: File, ao: AssemblyOption, po: Seq[PackageOption], mappings: Seq[MappingSet],
        cacheDir: File, log: Logger): File = {
      import Tracked.{inputChanged, outputChanged}
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
          IO.writeLines(outPath, shellScript, append = false)
          val jarBytes = IO.readBytes(tmpFile)
          tmpFile.delete()
          IO.append(outPath, jarBytes)
          try {
            Seq("chmod", "+x", outPath.toString).!
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
      val srcs = srcSets.flatMap( _.mappings )
      val counts = scala.collection.mutable.Map[MergeStrategy, Int]().withDefaultValue(0)
      (tempDir * "sbtMergeTarget*").get foreach { x => IO.delete(x) }
      def applyStrategy(strategy: MergeStrategy, name: String, files: Seq[(File, String)]): Seq[(File, String)] = {
        if (files.size >= strategy.notifyThreshold) {
          log.log(strategy.detailLogLevel, "Merging '%s' with strategy '%s'".format(name, strategy.name))
          counts(strategy) += 1
        }
        strategy((tempDir, name, files map (_._1))) match {
          case Right(f)  => f
          case Left(err) => throw new RuntimeException(strategy.name + ": " + err)
        }
      }
      val renamed = srcs.groupBy(_._2).flatMap { case (name, files) =>
        val strategy = strats(name)
        if (strategy == MergeStrategy.rename) applyStrategy(strategy, name, files)
        else files
      } (scala.collection.breakOut)
      // this step is necessary because some dirs may have been renamed above
      val cleaned: Seq[(File, String)] = renamed filter { pair =>
        (!pair._1.isDirectory) && pair._1.exists
      }
      val stratMapping = new mutable.ListBuffer[(String, MergeStrategy)]
      val mod: Seq[(File, String)] =
        cleaned.groupBy(_._2).toVector.sortBy(_._1).flatMap { case (name, files) =>
          val strategy = strats(name)
          stratMapping append (name -> strategy)
          if (strategy != MergeStrategy.rename) applyStrategy(strategy, name, files)
          else files
        } (scala.collection.breakOut)
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
      import sbt.classpath.ClasspathUtilities

      val tempDir = ao.assemblyDirectory
      if (!ao.cacheUnzip) IO.delete(tempDir)
      if (!tempDir.exists) tempDir.mkdir()

      val (libs, dirs) = classpath.map(_.data).toVector.partition(ClasspathUtilities.isArchive)
      val depLibs      = dependencies.map(_.data).toSet.filter(ClasspathUtilities.isArchive)
      val excludedJars = ao.excludedJars map {_.data}
      val libsFiltered = (libs flatMap {
        case jar if excludedJars contains jar.asFile => None
        case jar if jar.asFile.getName startsWith "scala-" =>
          if (ao.includeScala) Some(jar) else None
        case jar if depLibs contains jar.asFile =>
          if (ao.includeDependency) Some(jar) else None
        case jar =>
          if (ao.includeBin) Some(jar) else None
      })
      val dirsFiltered =
        dirs.par flatMap {
          case dir =>
            if (ao.includeBin) Some(dir)
            else None
        } map { dir =>
          val hash = sha1name(dir)
          IO.write(tempDir / (hash + "_dir.dir"), dir.getCanonicalPath, IO.utf8, false)
          val dest = tempDir / (hash + "_dir")
          if (dest.exists) {
            IO.delete(dest)
          }
          dest.mkdir()
          IO.copyDirectory(dir, dest)
          dest
        }
      val jarDirs =
        (for(jar <- libsFiltered.par) yield {
          val jarName = jar.asFile.getName
          val hash = sha1name(jar) + "_" + sha1content(jar)
          val jarNamePath = tempDir / (hash + ".jarName")
          val dest = tempDir / hash
          // If the jar name path does not exist, or is not for this jar, unzip the jar
          if (!ao.cacheUnzip || !jarNamePath.exists || IO.read(jarNamePath) != jar.getCanonicalPath )
          {
            log.info("Including: %s".format(jarName))
            IO.delete(dest)
            dest.mkdir()
            AssemblyUtils.unzip(jar, dest, log)
            IO.delete(ao.excludedFiles(Seq(dest)))
            
            // Write the jarNamePath at the end to minimise the chance of having a
            // corrupt cache if the user aborts the build midway through
            IO.write(jarNamePath, jar.getCanonicalPath, IO.utf8, false)
          }
          else log.info("Including from cache: %s".format(jarName))

          (dest, jar)
        })

      log.debug("Calculate mappings...")
      val base: Vector[File] = dirsFiltered.seq ++ (jarDirs map { _._1 })
      def getMappings(rootDir : File): Vector[(File, String)] = {
        val descendendants = if (!rootDir.exists) Vector()
                             else ((rootDir ** "*") --- ao.excludedFiles(base) --- base).get
        (descendendants x relativeTo(base)).toVector
      }
      val retval = (dirsFiltered map { d => MappingSet(None, getMappings(d)) }).seq ++
                   (jarDirs map { case (d, j) => MappingSet(Some(j), getMappings(d)) })
      retval.toVector
    }

    def assemblyTask(key: TaskKey[File]): Initialize[Task[File]] = Def.task {
      val t = (test in key).value
      val s = (streams in key).value
      Assembly((outputPath in key).value, (assemblyOption in key).value,
        (packageOptions in key).value, (assembledMappings in key).value,
        s.cacheDirectory, s.log)
    }
    def assembledMappingsTask(key: TaskKey[File]): Initialize[Task[Seq[MappingSet]]] = Def.task {
      val s = (streams in key).value
      assembleMappings(
        (fullClasspath in assembly).value, (dependencyClasspath in assembly).value,
        (assemblyOption in key).value, s.log)
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
        case "reference.conf" | "rootdoc.txt" | "play.plugins" => true
        case _ => false
      }
  }

  val defaultExcludedFiles: Seq[File] => Seq[File] = (base: Seq[File]) => Nil  
  private def sha1 = MessageDigest.getInstance("SHA-1")
  private def sha1content(f: File): String  = bytesToSha1String(IO.readBytes(f))
  private def sha1name(f: File): String     = sha1string(f.getCanonicalPath)
  private def sha1string(s: String): String = bytesToSha1String(s.getBytes("UTF-8"))
  private def bytesToSha1String(bytes: Array[Byte]): String = 
    bytesToString(sha1.digest(bytes))
  private def bytesToString(bytes: Seq[Byte]): String =
    bytes map {"%02x".format(_)} mkString

  object PathList {
    private val sysFileSep = System.getProperty("file.separator")
    def unapplySeq(path: String): Option[Seq[String]] = {
      val split = path.split(if (sysFileSep.equals( """\""")) """\\""" else sysFileSep)
      if (split.size == 0) None
      else Some(split.toList)
    }
  }

  val defaultMergeStrategy: String => MergeStrategy = { 
    case x if Assembly.isConfigFile(x) =>
      MergeStrategy.concat
    case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
      MergeStrategy.rename
    case PathList("META-INF", xs @ _*) =>
      (xs map {_.toLowerCase}) match {
        case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
          MergeStrategy.discard
        case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
          MergeStrategy.discard
        case "plexus" :: xs =>
          MergeStrategy.discard
        case "services" :: xs =>
          MergeStrategy.filterDistinctLines
        case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
          MergeStrategy.filterDistinctLines
        case _ => MergeStrategy.deduplicate
      }
    case _ => MergeStrategy.deduplicate
  }

  val shellScriptHeader: String = "#!/usr/bin/env sh"
  val defaultShellScript: Seq[String] =  Seq(shellScriptHeader, """exec java -jar "$0" "$@"""") // "
  val javaOptsShellScript: Seq[String] = Seq(shellScriptHeader, """exec java $JAVA_OPTS -jar "$0" "$@"""") // "

  lazy val baseAssemblySettings: Seq[sbt.Def.Setting[_]] = Seq(
    assembly := Assembly.assemblyTask(assembly).value,
    assembledMappings in assembly          := Assembly.assembledMappingsTask(assembly).value,
    packageScala                           := Assembly.assemblyTask(packageScala).value,
    assembledMappings in packageScala      := Assembly.assembledMappingsTask(packageScala).value,
    packageDependency                      := Assembly.assemblyTask(packageDependency).value,
    assembledMappings in packageDependency := Assembly.assembledMappingsTask(packageDependency).value,

    // test
    test in assembly := (test in Test).value,
    test in packageScala := (test in assembly).value,
    test in packageDependency := (test in assembly).value,
    
    // assemblyOption
    assembleArtifact in packageBin := true,
    assembleArtifact in packageScala := true,
    assembleArtifact in packageDependency := true,
    mergeStrategy in assembly := defaultMergeStrategy,
    excludedJars in assembly := Nil,
    assemblyOption in assembly := {
      val s = streams.value
      AssemblyOption(
        assemblyDirectory  = s.cacheDirectory / "assembly",
        includeBin         = (assembleArtifact in packageBin).value,
        includeScala       = (assembleArtifact in packageScala).value,
        includeDependency  = (assembleArtifact in packageDependency).value,
        mergeStrategy      = (mergeStrategy in assembly).value,
        excludedJars       = (excludedJars in assembly).value,
        excludedFiles      = defaultExcludedFiles,
        cacheOutput        = true,
        cacheUnzip         = true,
        appendContentHash  = false,
        prependShellScript = None)
    },

    assemblyOption in packageScala := {
      val ao = (assemblyOption in assembly).value
      ao.copy(includeBin = false, includeScala = true, includeDependency = false)
    },
    assemblyOption in packageDependency := {
      val ao = (assemblyOption in assembly).value
      ao.copy(includeBin = false, includeScala = true, includeDependency = true)
    },

    // packageOptions
    packageOptions in assembly <<= (packageOptions in (Compile, packageBin), mainClass in assembly) map { (os, mainClass) =>
      mainClass map { s =>
        Package.MainClass(s) +: (os filterNot {_.isInstanceOf[Package.MainClass]})
      } getOrElse {os}
    },
    packageOptions in packageScala := (packageOptions in (Compile, packageBin)).value,
    packageOptions in packageDependency := (packageOptions in (Compile, packageBin)).value,

    // ouputPath
    outputPath in assembly <<= (target in assembly, jarName in assembly) map { (t, s) => t / s },
    outputPath in packageScala <<= (target in assembly, jarName in packageScala) map { (t, s) => t / s },
    outputPath in packageDependency <<= (target in assembly, jarName in packageDependency) map { (t, s) => t / s },
    target in assembly <<= crossTarget,

    jarName in assembly <<= (jarName in assembly) or (defaultJarName in assembly),
    jarName in packageScala <<= (jarName in packageScala) or (defaultJarName in packageScala),
    jarName in packageDependency <<= (jarName in packageDependency) or (defaultJarName in packageDependency),

    defaultJarName in packageScala <<= (scalaVersion) map { (scalaVersion) => "scala-library-" + scalaVersion + "-assembly.jar" },
    defaultJarName in packageDependency <<= (name, version) map { (name, version) => name + "-assembly-" + version + "-deps.jar" },
    defaultJarName in assembly <<= (name, version) map { (name, version) => name + "-assembly-" + version + ".jar" },
    
    mainClass in assembly <<= mainClass or (mainClass in Runtime),
    
    fullClasspath in assembly <<= fullClasspath or (fullClasspath in Runtime),
    
    dependencyClasspath in assembly <<= dependencyClasspath or (dependencyClasspath in Runtime)
  )
  
  lazy val assemblySettings: Seq[sbt.Def.Setting[_]] = baseAssemblySettings
}

case class AssemblyOption(assemblyDirectory: File,
  includeBin: Boolean = true,
  includeScala: Boolean = true,
  includeDependency: Boolean = true,
  excludedJars: Classpath = Nil,
  excludedFiles: Seq[File] => Seq[File] = Plugin.defaultExcludedFiles, // use mergeStrategy instead
  mergeStrategy: String => Plugin.MergeStrategy = Plugin.defaultMergeStrategy,
  cacheOutput: Boolean = true,
  cacheUnzip: Boolean = true,
  appendContentHash: Boolean = false,
  prependShellScript: Option[Seq[String]] = None)
