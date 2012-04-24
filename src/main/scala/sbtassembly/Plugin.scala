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
  type MergeStrategy = (File, Seq[File]) => (Either[String, File], String)
  object MergeStrategy {

    val first: MergeStrategy = (tmp, files) => (Right(files.head), "first")
    
    val last: MergeStrategy = (tmp, files) => (Right(files.last), "last")
    
    val error: MergeStrategy = (tmp, files) =>
      (Left("found multiple files for same target path:" + files.map(filename(tmp, _)).mkString("\n", "\n", "")), "error")
    
    val concat: MergeStrategy = { (tmp, files) =>
      val file = File.createTempFile("sbtMergeTarget", ".tmp", tmp)
      val out = new FileOutputStream(file)
      try {
        files foreach (f => IO.transfer(f, out))
        (Right(file), "concat")
      } finally {
        out.close()
      }
    }
    
    val filterDistinctLines: MergeStrategy = { (tmp, files) =>
      val lines = files flatMap (IO.readLines(_, IO.utf8))
      val unique = (Vector.empty[String] /: lines)((v, l) => if (v contains l) v else v :+ l)
      val file = File.createTempFile("sbtMergeTarget", ".tmp", tmp)
      IO.writeLines(file, unique, IO.utf8)
      (Right(file), "filterDistinctLines")
    }
    
    val deduplicate: MergeStrategy = { (tmp, files) =>
      val fingerprints = Set() ++ (files map (sha1content))
      val result =
        if (fingerprints.size == 1) Right(files.head)
        else Left("different file contents found in the following:" + files.map(filename(tmp, _)).mkString("\n", "\n", ""))
      (result, "deduplicate")
    }
  }
  
  private val PathRE = "([^/]+)/(.*)".r
  private def filename(tempDir: File, f: File): String = {
    val baseURI = tempDir.getCanonicalFile.toURI
    val otherURI = f.getCanonicalFile.toURI
    baseURI.relativize(otherURI) match {
      case x if x.isAbsolute => f.getCanonicalPath
      case relative =>
        val PathRE(head, tail) = relative.getPath
        val jarName = IO.read(tempDir / (head + ".jarName"), IO.utf8)
        jarName + ":" + tail
    }
  }
  
  private def assemblyTask(out: File, po: Seq[PackageOption], mappings: File => Seq[(File, String)],
      mergeStrategy: String => MergeStrategy, cacheDir: File, log: Logger): File =
    IO.withTemporaryDirectory { tempDir =>
      val srcs: Seq[(File, String)] = mappings(tempDir).groupBy(_._2).map{
        case (_, files) if files.size == 1 => files.head
        case (name, files) =>
          val result = mergeStrategy(name)(tempDir, files map (_._1)) match {
            case (Right(f), n) =>
              log.info("merging '%s' with strategy '%s'".format(name, n))
              f
            case (Left(err), n) =>
              throw new RuntimeException(n + ": " + err)
          }
          (result, name)
      }(scala.collection.breakOut)
      val config = new Package.Configuration(srcs, out, po)
      Package(config, cacheDir, log)
      out
    }

  private def isLicenseFile(f: File): Boolean =
    f.getName.toLowerCase match {
      case "licence" | "licence.txt" | "notice" | "notice.txt" => true
      case _ => false
    }

  private def isReadme(f: File): Boolean =
    f.getName.toLowerCase match {
      case "readme" | "readme.txt" => true
      case _ => false
    }

  private def isSimplyExcludedFile(f: File): Boolean =
    isLicenseFile(f) || isReadme(f)

  private def isMetaInfJunk(f: File): Boolean =
    f.getName.toLowerCase match {
      case "manifest.mf" | "index.list" | "dependencies" => true
      case name if name.endsWith(".sf") || name.endsWith(".dsa") => true // came from a signed jar; discard the signature
      case _ => false
    }

  private def assemblyExcludedFiles(bases: Seq[File]): Seq[File] =
    bases flatMap { base =>
      (base * "*").get.filter(isSimplyExcludedFile) ++
      (base / "META-INF" / "plexus" ** "*").get ++ // maven-specific stuff
      (base / "META-INF" * "*").get.filter { f => isSimplyExcludedFile(f) || isMetaInfJunk(f) }
    }
  
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
    val dirsFiltered = dirs flatMap {
      case dir if depLibs contains dir.asFile =>
        if (ao.includeDependency) Some(dir) else None
      case dir =>
        if (ao.includeBin) Some(dir) else None
    }
    
    def sha1name(f: File): String = {
      val bytes = f.getCanonicalPath.getBytes
      val digest = sha1.digest(bytes)
      ("" /: digest)(_ + "%02x".format(_))
    }
    
    val jarDirs = for(jar <- libsFiltered) yield {
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

    val base = jarDirs ++ dirsFiltered
    val descendants = ((base ** (-DirectoryFilter)) --- ao.exclude(base)).get filter { _.exists }
    
    descendants x relativeTo(base)
  }
  
  implicit def wrapTaskKey[T](key: TaskKey[T]): WrappedTaskKey[T] = WrappedTaskKey(key) 
  case class WrappedTaskKey[A](key: TaskKey[A]) {
    def orr[T >: A](rhs: Initialize[Task[T]]): Initialize[Task[T]] =
      (key.? zipWith rhs)( (x,y) => (x :^: y :^: KNil) map Scoped.hf2( _ getOrElse _ ))
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
        case "reference.conf" => MergeStrategy.concat
        case n if n.startsWith("META-INF/services/") => MergeStrategy.filterDistinctLines
        case "META-INF/spring.schemas" | "META-INF/spring.handlers" => MergeStrategy.filterDistinctLines
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
