package sbtassembly

import sbt._
import Keys._
import java.io.PrintWriter
import scala.collection.mutable
import scala.io.Source
import Project.Initialize

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
  }
  
  private def assemblyTask(out: File, po: Seq[PackageOption], ao: AssemblyOption,
      classpath: Classpath, dependencies: Classpath, excludedJars: Classpath,
      cacheDir: File, log: Logger): File =
    IO.withTemporaryDirectory { tempDir =>
      val srcs = assemblyPaths(tempDir, classpath, dependencies, ao, excludedJars, log)
      val config = new Package.Configuration(srcs, out, po)
      Package(config, cacheDir, log)
      out
    }
  
  private def assemblyExcludedFiles(bases: Seq[File]): Seq[File] =
    bases flatMap { base =>
      (base / "META-INF" * "*").get collect {
        case f if f.getName.toLowerCase == "license" => f
        case f if f.getName.toLowerCase == "manifest.mf" => f
      }
    }

  // even though fullClasspath includes deps, dependencyClasspath is needed to figure out
  // which jars exactly belong to the deps for packageDependency option.
  private def assemblyPaths(tempDir: File, classpath: Classpath, dependencies: Classpath,
      ao: AssemblyOption, ej: Classpath, log: Logger) = {
    import sbt.classpath.ClasspathUtilities

    val (libs, dirs) = classpath.map(_.data).partition(ClasspathUtilities.isArchive)
    val (depLibs, depDirs) = dependencies.map(_.data).partition(ClasspathUtilities.isArchive)
    val services = mutable.Map[String, mutable.ArrayBuffer[String]]()
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
    
    for(jar <- libsFiltered) {
      val jarName = jar.asFile.getName
      log.info("Including %s".format(jarName))
      IO.unzip(jar, tempDir)
      IO.delete(ao.exclude(Seq(tempDir)))
      val servicesDir = tempDir / "META-INF" / "services"
      if (servicesDir.asFile.exists) {
       for (service <- (servicesDir * "*").get) {
         val serviceFile = service.asFile
         if (serviceFile.exists && serviceFile.isFile) {
           val entries = services.getOrElseUpdate(serviceFile.getName, new mutable.ArrayBuffer[String]())
           for (provider <- IO.readLines(serviceFile, IO.utf8)) {
             if (!entries.contains(provider)) {
               entries += provider
             }
           }
         }
       }
     }
    }

    for ((service, providers) <- services) {
      log.info("Merging providers for %s".format(service))
      val serviceFile = (tempDir / "META-INF" / "services" / service).asFile
      val writer = new PrintWriter(serviceFile)
      for (provider <- providers.map { _.trim }.filter { !_.isEmpty }) {
        log.debug("-  %s".format(provider))
        writer.println(provider)
      }
      writer.close()
    }

    val base = tempDir +: dirsFiltered
    val descendants = ((base ** (-DirectoryFilter)) --- ao.exclude(base)).get
    descendants x relativeTo(base)
  }
  
  implicit def wrapTaskKey[T](key: TaskKey[T]): WrappedTaskKey[T] = WrappedTaskKey(key) 
  case class WrappedTaskKey[A](key: TaskKey[A]) {
    def orr[T >: A](rhs: Initialize[Task[T]]): Initialize[Task[T]] =
      (key.? zipWith rhs)( (x,y) => (x :^: y :^: KNil) map Scoped.hf2( _ getOrElse _ ))
  }
  
  lazy val baseAssemblySettings: Seq[sbt.Project.Setting[_]] = Seq(
    assembly <<= (test in assembly, outputPath in assembly, packageOptions in assembly, assemblyOption in assembly, 
        fullClasspath in assembly, dependencyClasspath in assembly, excludedJars in assembly, cacheDirectory, streams) map {
      (test, out, po, ao, cp, deps, ej, cacheDir, s) =>
        assemblyTask(out, po, ao, cp, deps, ej, cacheDir, s.log) },

    packageScala <<= (outputPath in assembly, packageOptions, assemblyOption in assembly, 
        fullClasspath in assembly, dependencyClasspath in assembly, excludedJars in assembly, cacheDirectory, streams) map {
      (out, po, ao, cp, deps, ej, cacheDir, s) =>
        assemblyTask(out, po,
          ao.copy(includeBin = false, includeScala = true, includeDependency = false),
          cp, deps, ej, cacheDir, s.log) },

    packageDependency <<= (outputPath in assembly, packageOptions in assembly, assemblyOption in assembly, 
        fullClasspath in assembly, dependencyClasspath in assembly, excludedJars in assembly, cacheDirectory, streams) map {
      (out, po, ao, cp, deps, ej, cacheDir, s) =>
        assemblyTask(out, po,
          ao.copy(includeBin = false, includeScala = false, includeDependency = true),
          cp, deps, ej, cacheDir, s.log) },
    
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
