package sbtassembly

import sbt._
import Keys._
import scala.collection.mutable
import scala.io.Source
import Project.Initialize
import java.io.{ PrintWriter, FileOutputStream, File }
import java.security.MessageDigest

object Plugin extends sbt.Plugin {
  import AssemblyKeys._
    
  object AssemblyKeys {
    lazy val assembly = TaskKey[File]("assembly", "Builds a single-file deployable jar.")
    lazy val packageScala      = TaskKey[File]("assembly-package-scala", "Produces the scala artifact.")
    lazy val packageDependency = TaskKey[File]("assembly-package-dependency", "Produces the dependency artifact.")
  
    lazy val assembleArtifact  = SettingKey[Boolean]("assembly-assemble-artifact", "Enables (true) or disables (false) assembling an artifact.")
    lazy val assemblyOption    = SettingKey[AssemblyOption]("assembly-option")
    lazy val jarName           = SettingKey[String]("assembly-jar-name")
    lazy val defaultJarName    = SettingKey[String]("assembly-default-jar-name")
    lazy val outputPath        = SettingKey[File]("assembly-output-path")
    lazy val excludedFiles     = SettingKey[Seq[File] => Seq[File]]("assembly-excluded-files")
    lazy val excludedJars      = TaskKey[Classpath]("assembly-excluded-jars")
    lazy val assembledMappings = TaskKey[File => Seq[(File, String)]]("assembly-assembled-mappings")
    lazy val mergeStrategy     = SettingKey[String => MergeStrategy]("assembly-merge-strategy", "mapping from archive member path to merge strategy")
  }
  
  /**
   * MergeStrategy is invoked if more than one source file is mapped to the 
   * same target path. Its arguments are the tempDir (which is deleted after
   * packaging) and the sequence of source files, and it shall return the
   * file to be included in the assembly (or throw an exception).
   */
  abstract class MergeStrategy extends Function1[(File, String, Seq[File]), Either[String, Seq[(File, String)]]] {
    def name: String
    def notifyThreshold = 2
  }

  // (File, Seq[File]) => (Either[String, File], String)
  object MergeStrategy {
    val first: MergeStrategy = new MergeStrategy {
      val name = "first"
      def apply(args: (File, String, Seq[File])): Either[String, Seq[(File, String)]] =
        Right(Seq(args._3.head -> args._2))
    }
    val last: MergeStrategy = new MergeStrategy {
      val name = "last"
      def apply(args: (File, String, Seq[File])): Either[String, Seq[(File, String)]] =
        Right(Seq(args._3.last -> args._2))
    }
    val singleOrError: MergeStrategy = new MergeStrategy {
      val name = "singleOrError"
      def apply(args: (File, String, Seq[File])): Either[String, Seq[(File, String)]] =
        if (args._3.size == 1) Right(Seq(args._3.head -> args._2))
        else Left("found multiple files for same target path:" +
          filenames(args._1, args._3).mkString("\n", "\n", ""))
    }
    val concat: MergeStrategy = new MergeStrategy {
      val name = "concat"
      def apply(args: (File, String, Seq[File])): Either[String, Seq[(File, String)]] = {
        val file = File.createTempFile("sbtMergeTarget", ".tmp", args._1)
        val out = new FileOutputStream(file)
        try {
          args._3 foreach (f => IO.transfer(f, out))
          Right(Seq(file -> args._2))
        } finally {
          out.close()
        }
      }
    }
    val filterDistinctLines: MergeStrategy = new MergeStrategy {
      val name = "filterDistinctLines"
      def apply(args: (File, String, Seq[File])): Either[String, Seq[(File, String)]] = {
        val lines = args._3 flatMap (IO.readLines(_, IO.utf8))
        val unique = (Vector.empty[String] /: lines)((v, l) => if (v contains l) v else v :+ l)
        val file = File.createTempFile("sbtMergeTarget", ".tmp", args._1)
        IO.writeLines(file, unique, IO.utf8)
        Right(Seq(file -> args._2))
      }
    }
    val deduplicate: MergeStrategy = new MergeStrategy {
      val name = "deduplicate"
      def apply(args: (File, String, Seq[File])): Either[String, Seq[(File, String)]] =
        if (args._3.size == 1) Right(Seq(args._3.head -> args._2))
        else {
          val fingerprints = Set() ++ (args._3 map (sha1content))
          if (fingerprints.size == 1) Right(Seq(args._3.head -> args._2))
          else Left("different file contents found in the following:" +
              filenames(args._1, args._3).mkString("\n", "\n", ""))
        }
    }
    val rename: MergeStrategy = new MergeStrategy {
      val name = "rename"
      def apply(args: (File, String, Seq[File])): Either[String, Seq[(File, String)]] =
        Right(args._3 flatMap { f =>
          if(!f.exists) Seq.empty
          else if(f.isDirectory && (f ** "*.class").get.nonEmpty) Seq(f -> args._2)
          else AssemblyUtils.sourceOfFileForMerge(args._1, f) match {
            case (dir, base, path, false) => Seq(f -> args._2)
            case (jar, base, path, true) =>
              val dest = new File(f.getParent, appendJarName(f.getName, jar))
              IO.move(f, dest)
              val result = Seq(dest -> appendJarName(args._2, jar))
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
      def apply(args: (File, String, Seq[File])): Either[String, Seq[(File, String)]] =
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

  private def assemblyTask(out: File, po: Seq[PackageOption], mappings: File => Seq[(File, String)],
      strats: String => MergeStrategy, cacheDir: File, log: Logger): File =
    IO.withTemporaryDirectory { tempDir =>
      val srcs: Seq[(File, String)] = mappings(tempDir)
      val renamed = srcs.groupBy(_._2).flatMap {
        case (name, files) =>
          val strategy = strats(name)
          if (strategy == MergeStrategy.rename) {
            if (files.size >= strategy.notifyThreshold) {
              log.info("Merging '%s' with strategy '%s'".format(name, strategy.name))
            }
            strategy.apply(tempDir, name, files map (_._1)) match {
              case Right(f)  => f
              case Left(err) => throw new RuntimeException(strategy.name + ": " + err)
            }
          } else files
      } (scala.collection.breakOut)
      // this step is necessary because some dirs may have been renamed above
      val cleaned: Seq[(File, String)] = renamed filter { pair =>
        (!pair._1.isDirectory) && pair._1.exists
      }
      val mod: Seq[(File, String)] = cleaned.groupBy(_._2).flatMap {
        case (name, files) =>
          val strategy = strats(name)
          if (strategy != MergeStrategy.rename) {
            if (files.size >= strategy.notifyThreshold) {
              log.info("Merging '%s' with strategy '%s'".format(name, strategy.name))
            }
            strategy.apply((tempDir, name, files map (_._1))) match {
              case Right(f)  => f
              case Left(err) => throw new RuntimeException(strategy.name + ": " + err)
            }
          } else files
      }(scala.collection.breakOut)
      val config = new Package.Configuration(mod, out, po)
      Package(config, cacheDir, log)
      out
    }

  private def assemblyExcludedFiles(bases: Seq[File]): Seq[File] = Nil  
  private def sha1 = MessageDigest.getInstance("SHA-1")
  private def sha1content(f: File) = sha1.digest(IO.readBytes(f)).toSeq

  // even though fullClasspath includes deps, dependencyClasspath is needed to figure out
  // which jars exactly belong to the deps for packageDependency option.
  private def assemblyAssembledMappings(tempDir: File, classpath: Classpath, dependencies: Classpath,
      ao: AssemblyOption, ej: Classpath, log: Logger) = {
    import sbt.classpath.ClasspathUtilities

    val (libs, dirs) = classpath.map(_.data).partition(ClasspathUtilities.isArchive)
    val (depLibs, depDirs) = dependencies.map(_.data).partition(ClasspathUtilities.isArchive)
    val excludedJars = ej map {_.data}
    val libsFiltered = libs flatMap {
      case jar if excludedJars contains jar.asFile => None
      case jar if List("scala-library.jar", "scala-compiler.jar") contains jar.asFile.getName =>
        if (ao.includeScala) Some(jar) else None
      case jar if depLibs contains jar.asFile =>
        if (ao.includeDependency) Some(jar) else None
      case jar =>
        if (ao.includeBin) Some(jar) else None
    }

    def sha1name(f: File): String = {
      val bytes = f.getCanonicalPath.getBytes
      val digest = sha1.digest(bytes)
      ("" /: digest)(_ + "%02x".format(_))
    }

    val dirsFiltered = dirs flatMap {
      case dir if depLibs contains dir.asFile =>
        if (ao.includeDependency) Some(dir)
        else None
      case dir =>
        if (ao.includeBin) Some(dir)
        else None
    } map { dir =>
      val hash = sha1name(dir)
      IO.write(tempDir / (hash + "_dir.dir"), dir.getCanonicalPath, IO.utf8, false)
      val dest = tempDir / (hash + "_dir")
      dest.mkdir()
      IO.copyDirectory(dir, dest)
      dest
    }
    
    val jarDirs = for(jar <- libsFiltered.sorted) yield {
      val jarName = jar.asFile.getName
      log.info("Including %s".format(jarName))
      val hash = sha1name(jar)
      IO.write(tempDir / (hash + ".jarName"), jar.getCanonicalPath, IO.utf8, false)
      val dest = tempDir / hash
      dest.mkdir()
      IO.unzip(jar, dest)
      IO.delete(ao.exclude(Seq(dest)))
      dest
    }

    val base = dirsFiltered ++ jarDirs
    val descendants = ((base ** "*") --- ao.exclude(base) --- base).get filter { _.exists }
    
    descendants x relativeTo(base)
  }
  
  implicit def wrapTaskKey[T](key: TaskKey[T]): WrappedTaskKey[T] = WrappedTaskKey(key) 
  case class WrappedTaskKey[A](key: TaskKey[A]) {
    def orr[T >: A](rhs: Initialize[Task[T]]): Initialize[Task[T]] =
      (key.? zipWith rhs)( (x,y) => (x :^: y :^: KNil) map Scoped.hf2( _ getOrElse _ ))
  }

  private val LicenseFile = """(.*/)?(license|licence|notice|copying)([.]\w+)?$""".r
  private def isLicenseFile(fileName: String): Boolean =
    fileName.toLowerCase match {
      case LicenseFile(_, _, ext) if ext != ".class" => true // DISLIKE
      case _ => false
    }

  private val ReadMe = """(.*/)?(readme)([.]\w+)?$""".r
  private def isReadme(fileName: String): Boolean =
    fileName.toLowerCase match {
      case ReadMe(x, y, z) => true
      case _ => false
    }

  lazy val baseAssemblySettings: Seq[sbt.Project.Setting[_]] = Seq(
    assembly <<= (test in assembly, outputPath in assembly, packageOptions in assembly,
        assembledMappings in assembly, mergeStrategy in assembly, cacheDirectory, streams) map {
      (test, out, po, am, ms, cacheDir, s) =>
        assemblyTask(out, po, am, ms, cacheDir, s.log) },
    
    assembledMappings in assembly <<= (assemblyOption in assembly, fullClasspath in assembly, dependencyClasspath in assembly,
        excludedJars in assembly, streams) map {
      (ao, cp, deps, ej, s) => (tempDir: File) => assemblyAssembledMappings(tempDir, cp, deps, ao, ej, s.log) },
      
    mergeStrategy in assembly := { 
      case "reference.conf" =>
        MergeStrategy.concat
      case n if isReadme(n) || isLicenseFile(n) =>
        MergeStrategy.rename
      case inf if inf.startsWith("META-INF/") =>
        inf.slice("META-INF/".size, inf.size).toLowerCase match {
          case "manifest.mf" | "index.list" | "dependencies" =>
            MergeStrategy.discard
          case n if n.endsWith(".sf") || n.endsWith(".dsa") =>
            MergeStrategy.discard
          case n if n startsWith "plexus/" =>
            MergeStrategy.discard
          case n if n startsWith "services/" =>
            MergeStrategy.filterDistinctLines
          case "spring.schemas" | "spring.handlers" =>
            MergeStrategy.filterDistinctLines
          case _ => MergeStrategy.deduplicate
        }
      case _ => MergeStrategy.deduplicate
    },

    packageScala <<= (outputPath in assembly, packageOptions,
        assembledMappings in packageScala, mergeStrategy in assembly, cacheDirectory, streams) map {
      (out, po, am, ms, cacheDir, s) => assemblyTask(out, po, am, ms, cacheDir, s.log) },

    assembledMappings in packageScala <<= (assemblyOption in assembly, fullClasspath in assembly, dependencyClasspath in assembly,
        excludedJars in assembly, streams) map {
      (ao, cp, deps, ej, s) => (tempDir: File) =>
        assemblyAssembledMappings(tempDir, cp, deps,
          ao.copy(includeBin = false, includeScala = true, includeDependency = false),
          ej, s.log) },

    packageDependency <<= (outputPath in assembly, packageOptions in assembly,
        assembledMappings in packageDependency, mergeStrategy in assembly, cacheDirectory, streams) map {
      (out, po, am, ms, cacheDir, s) => assemblyTask(out, po, am, ms, cacheDir, s.log) },
    
    assembledMappings in packageDependency <<= (assemblyOption in assembly, fullClasspath in assembly, dependencyClasspath in assembly,
        excludedJars in assembly, streams) map {
      (ao, cp, deps, ej, s) => (tempDir: File) =>
        assemblyAssembledMappings(tempDir, cp, deps,
          ao.copy(includeBin = false, includeScala = false, includeDependency = true),
          ej, s.log) },

    test <<= test orr (test in Test),
    test in assembly <<= (test in Test),
    
    assemblyOption in assembly <<= (assembleArtifact in packageBin,
        assembleArtifact in packageScala, assembleArtifact in packageDependency, excludedFiles in assembly) {
      (includeBin, includeScala, includeDeps, exclude) =>   
      AssemblyOption(includeBin, includeScala, includeDeps, exclude) 
    },
    
    packageOptions in assembly <<= (packageOptions in Compile, mainClass in assembly) map {
      (os, mainClass) =>
        mainClass map { s =>
          os find { o => o.isInstanceOf[Package.MainClass] } map { _ => os
          } getOrElse { Package.MainClass(s) +: os }
        } getOrElse {os}      
    },
    
    outputPath in assembly <<= (target in assembly, jarName in assembly) { (t, s) => t / s },
    target in assembly <<= target,
    
    jarName in assembly <<= (jarName in assembly) or (defaultJarName in assembly),
    defaultJarName in assembly <<= (name, version) { (name, version) => name + "-assembly-" + version + ".jar" },
    
    mainClass in assembly <<= mainClass orr (mainClass in Runtime),
    
    fullClasspath in assembly <<= fullClasspath orr (fullClasspath in Runtime),
    
    dependencyClasspath in assembly <<= dependencyClasspath orr (dependencyClasspath in Runtime),
    
    excludedFiles in assembly := assemblyExcludedFiles _,
    excludedJars in assembly := Nil,
    assembleArtifact in packageBin := true,
    assembleArtifact in packageScala := true,
    assembleArtifact in packageDependency := true    
  )
  
  lazy val assemblySettings: Seq[sbt.Project.Setting[_]] = baseAssemblySettings
}

case class AssemblyOption(includeBin: Boolean,
  includeScala: Boolean,
  includeDependency: Boolean,
  exclude: Seq[File] => Seq[File])
