package sbtassembly

import sbt._
import Keys._
import com.eed3si9n.jarjarabrams

trait AssemblyKeys {
  lazy val assembly                  = taskKey[File]("Builds a deployable fat jar.")
  lazy val assembleArtifact          = settingKey[Boolean]("Enables (true) or disables (false) assembling an artifact.")
  lazy val assemblyOption            = taskKey[AssemblyOption]("Configuration for making a deployable fat jar.")
  lazy val assembledMappings         = taskKey[Seq[MappingSet]]("Keeps track of jar origins for each source.")

  lazy val assemblyPackageScala      = taskKey[File]("Produces the scala artifact.")
  lazy val assemblyPackageDependency = taskKey[File]("Produces the dependency artifact.")
  lazy val assemblyJarName           = taskKey[String]("name of the fat jar")
  lazy val assemblyDefaultJarName    = taskKey[String]("default name of the fat jar")
  lazy val assemblyOutputPath        = taskKey[File]("output path of the fat jar")
  lazy val assemblyExcludedJars      = taskKey[Classpath]("list of excluded jars")
  lazy val assemblyMergeStrategy     = settingKey[String => MergeStrategy]("mapping from archive member path to merge strategy")
  lazy val assemblyShadeRules        = settingKey[Seq[jarjarabrams.ShadeRule]]("shading rules backed by jarjar")
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
