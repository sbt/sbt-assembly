package sbtassembly

import com.eed3si9n.jarjarabrams
import sbt.Keys._
import sbt._

trait AssemblyKeys {
  lazy val assembly                  = taskKey[File]("Builds a deployable über JAR")
  lazy val assembleArtifact          = settingKey[Boolean]("Enables (true) or disables (false) assembling an artifact")
  lazy val assemblyOption            = taskKey[AssemblyOption]("Configuration for making a deployable über JAR")
  lazy val assemblyPackageScala      = taskKey[File]("Produces the Scala artifact")
  lazy val assemblyPackageDependency = taskKey[File]("Produces the dependency artifact")
  lazy val assemblyJarName           = taskKey[String]("name of the über jar")
  lazy val assemblyDefaultJarName    = taskKey[String]("default name of the über jar")
  lazy val assemblyOutputPath        = taskKey[File]("output path of the über jar")
  lazy val assemblyExcludedJars      = taskKey[Classpath]("list of excluded jars")
  lazy val assemblyMergeStrategy     = settingKey[String => MergeStrategy]("mapping from archive member path to merge strategy")
  lazy val assemblyShadeRules        = settingKey[Seq[jarjarabrams.ShadeRule]]("shading rules backed by jarjar")
  lazy val assemblyKeepRules         = settingKey[Seq[String]]("Keep rules backed by jarjar to run on the final assembled JAR")
  lazy val assemblyAppendContentHash = settingKey[Boolean]("Appends SHA-1 fingerprint to the assembly file name")
  lazy val assemblyMaxHashLength     = settingKey[Int]("Length of SHA-1 fingerprint used for the assembly file name")
  lazy val assemblyCacheOutput       = settingKey[Boolean]("Enables (true) or disables (false) cacheing the output if the content has not changed")
  lazy val assemblyPrependShellScript = settingKey[Option[Seq[String]]]("A launch script to prepend to the über JAR")
  lazy val assemblyRepeatableBuild   = settingKey[Boolean]("If (true), builds the jar with a consistent hash (given the same inputs/assembly configuration) so it can be cached, but loses parallelism optimization. " +
                                                                       "If (false), builds the jar faster via parallelization, but loses hash consistency, and hence, cannot be cached")
}

object AssemblyKeys extends AssemblyKeys
