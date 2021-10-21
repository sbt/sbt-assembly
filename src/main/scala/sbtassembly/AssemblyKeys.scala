package sbtassembly

import sbt._
import Keys._
import com.eed3si9n.jarjarabrams

trait AssemblyKeys {
  lazy val assembly                   = taskKey[File]("Builds a deployable über JAR")
  lazy val assembleArtifact           = settingKey[Boolean]("Enables (true) or disables (false) assembling an artifact")
  lazy val assemblyOption             = taskKey[AssemblyOption]("Configuration for making a deployable über JAR")
  lazy val assembledMappings          = taskKey[Seq[MappingSet]]("Keeps track of jar origins for each source")
  lazy val assemblyCacheDependency    = taskKey[Boolean]("Caches the unzipped products of the dependency JAR files.  Requires assemblyCacheUnzip (true) and AssemblyOption.assemblyUnzipCacheDir to be provided.")

  lazy val assemblyUnzipDirectory     = settingKey[Option[File]]("Specify a directory to unzip the products of dependency JAR files (e.g. assemblyUnzipDirectory := Some(localCacheDirectory.value / \"sbt-assembly\" / \"dependencies\").  Default None (uses default assembly directory).")
  lazy val assemblyPackageScala       = taskKey[File]("Produces the Scala artifact")
  lazy val assemblyPackageDependency  = taskKey[File]("Produces the dependency artifact")
  lazy val assemblyJarName            = taskKey[String]("name of the über jar")
  lazy val assemblyDefaultJarName     = taskKey[String]("default name of the über jar")
  lazy val assemblyOutputPath         = taskKey[File]("output path of the über jar")
  lazy val assemblyExcludedJars       = taskKey[Classpath]("list of excluded jars")
  lazy val assemblyMergeStrategy      = settingKey[String => MergeStrategy]("mapping from archive member path to merge strategy")
  lazy val assemblyShadeRules         = settingKey[Seq[jarjarabrams.ShadeRule]]("shading rules backed by jarjar")
  lazy val assemblyAppendContentHash  = settingKey[Boolean]("Appends SHA-1 fingerprint to the assembly file name")
  lazy val assemblyMaxHashLength      = settingKey[Int]("Length of SHA-1 fingerprint used for the assembly file name")
  lazy val assemblyCacheUnzip         = settingKey[Boolean]("Enables (true) or disables (false) cacheing the unzipped products of the dependency JAR files")
  lazy val assemblyCacheOutput        = settingKey[Boolean]("Enables (true) or disables (false) cacheing the output if the content has not changed")
  lazy val assemblyCacheUseHardLinks  = settingKey[Boolean]("Experimental. Enables (true) or disables (false) using Files.createLink from the unzipped dependency cache to the assembly directory.  Requires both paths to be on the same physical filesystem.  Default false.")
  lazy val assemblyPrependShellScript = settingKey[Option[Seq[String]]]("A launch script to prepend to the über JAR")
}

object AssemblyKeys extends AssemblyKeys

// Keep track of the source package of mappings that come from a jar, so we can
// sha1 the jar instead of the unpacked packages when determining whether to rebuild
case class MappingSet(sourcePackage : Option[File], mappings : Vector[(File, String)]) {
  def dependencyFiles: Vector[File] =
    sourcePackage match {
      case Some(f)  => Vector(f)
      case None     => mappings.map(_._1)
    }
}
