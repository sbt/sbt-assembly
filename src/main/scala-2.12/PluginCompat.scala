package sbtassembly

import java.nio.file.{ Path => NioPath }
import java.util.jar.{ Manifest => JManifest }
import sbt.*
import Keys.test
import sbt.internal.util.HNil
import sbt.internal.util.Types.:+:
import sbt.util.FileInfo.lastModified
import sbt.util.Tracked.{ inputChanged, lastOutput }
import xsbti.FileConverter

private[sbtassembly] object PluginCompat {
  type MainClass = sbt.Package.MainClass

  object CollectionConverters

  def baseTestSettings: Seq[sbt.Def.Setting[?]] = Seq(
    AssemblyKeys.assembly / test := (()),
    AssemblyKeys.assemblyPackageScala / test := (AssemblyKeys.assembly / test).value,
    AssemblyKeys.assemblyPackageDependency / test := (AssemblyKeys.assembly / test).value,
  )

  type CacheKey = FilesInfo[ModifiedFileInfo] :+:
    Map[String, (Boolean, String)] :+: // map of target paths that matched a merge strategy
    JManifest :+:
    // Assembly options...
    Boolean :+:
    Option[Seq[String]] :+:
    Option[Int] :+:
    Boolean :+:
    HNil

  val HListFormats = sbt.internal.util.HListFormats
  val Streamable = scala.tools.nsc.io.Streamable

  private[sbtassembly] def makeCacheKey(
    classes: Vector[NioPath],
    filteredJars: Vector[Attributed[File]],
    mergeStrategiesByPathList: Map[String, (Boolean, String)],
    jarManifest: JManifest,
    ao: AssemblyOption,
  ): CacheKey =
    lastModified(classes.map(_.toFile()).toSet ++ filteredJars.map(_.data).toSet) :+:
      mergeStrategiesByPathList :+:
      jarManifest :+:
      ao.repeatableBuild :+:
      ao.prependShellScript :+:
      ao.maxHashLength :+:
      ao.appendContentHash :+:
      HNil

  // sbt 1.x style disk cache
  private[sbtassembly] def cachedAssembly(inputs: CacheKey, cacheDir: File, scalaVersion: String, log: Logger)(
      buildAssembly: () => File
  ): File = {
    import CacheImplicits._
    import sbt.internal.util.HListFormats._
    import sbt.Package.manifestFormat
    val cacheBlock = inputChanged(cacheDir / s"assembly-cacheKey-$scalaVersion") { (inputChanged, _: CacheKey) =>
      lastOutput(cacheDir / s"assembly-outputs-$scalaVersion") { (_: Unit, previousOutput: Option[File]) =>
        val outputExists = previousOutput.exists(_.exists())
        (inputChanged, outputExists) match {
          case (false, true) =>
            log.info("Assembly jar up to date: " + previousOutput.get.toPath)
            previousOutput.get
          case (true, true) =>
            log.debug("Building assembly jar due to changed inputs...")
            IO.delete(previousOutput.get)
            buildAssembly()
          case (_, _) =>
            log.debug("Building assembly jar due to missing output...")
            buildAssembly()
        }
      }
    }
    cacheBlock(inputs)(Unit)
  }
}
