package sbtassembly

import java.io.File
import java.nio.file.{ Path => NioPath }
import java.util.jar.{ Manifest => JManifest }
import sbt.*
import sbt.Keys.test
import xsbti.{ FileConverter, HashedVirtualFileRef, VirtualFile }
import sbtcompat.PluginCompat.{ Out, toFile, toOutput }
import sbt.util.{ FilesInfo, ModifiedFileInfo }
import sbt.util.FileInfo.lastModified
import sbt.util.Tracked.{ inputChanged, lastOutput }
import sjsonnew.{ BasicJsonProtocol, JsonFormat }

object PluginCompat:
  type JarManifest = PackageOption.JarManifest
  type MainClass = PackageOption.MainClass
  type ManifestAttributes = PackageOption.ManifestAttributes
  type FixedTimestamp = PackageOption.FixedTimestamp

  val CollectionConverters = scala.collection.parallel.CollectionConverters

  def baseTestSettings: Seq[sbt.Def.Setting[?]] = Seq(
    AssemblyKeys.assembly / test := TestResult.Empty,
    AssemblyKeys.assemblyPackageScala / test := (AssemblyKeys.assembly / test).evaluated,
    AssemblyKeys.assemblyPackageDependency / test := (AssemblyKeys.assembly / test).evaluated,
  )

  object HListFormats
  val Streamable = scala.reflect.io.Streamable

  extension [A1](init: Def.Initialize[Task[A1]])
    def ? : Def.Initialize[Task[Option[A1]]] = Def.optional(init) {
      case None    => sbt.std.TaskExtra.task { None }
      case Some(t) => t.map(Some.apply)
    }

    def or[A2 >: A1](i: Def.Initialize[Task[A2]]): Def.Initialize[Task[A2]] =
      init.?.zipWith(i) { (toa1: Task[Option[A1]], ta2: Task[A2]) =>
        (toa1, ta2).mapN {
          case (oa1: Option[A1], a2: A2) => oa1.getOrElse(a2)
        }
      }

  val inTask = Project.inTask

  /**
   * Input state used to determine whether an SBT 2 assembly output can be reused.
   *
   * This is a direct port of the SBT 1 cache key in `src/main/scala-2.12/PluginCompat.scala`
   *
   * @param assemblyInputFileStamps modification-time information for compiled class/resource
   *   outputs and selected JARs on the resolved assembly classpath; source files are not watched directly
   * @param resolvedMergeStrategyInfoByTargetPath resolved merge-strategy information, indexed by target path
   * @param jarManifest manifest written to the output JAR
   * @param repeatableBuild whether output JAR entries are sorted by target path before writing to
   *   make the output bytes deterministic for reproducible builds
   * @param prependShellScript optional script prepended to the output JAR
   * @param maxHashLength optional maximum length of the SHA-1 appended to the output file name
   * @param appendContentHash whether the content hash is appended to the output file name.
   *   When `appendContentHash` is enabled, the output name changes from, for example,
   *   `app-assembly.jar` to `app-assembly-a1b2c3.jar`; `maxHashLength` selects how much of the
   *   SHA-1 is appended. Changing it therefore requires a rebuild for the requested output path.
   *   When content-hash appending is disabled, `maxHashLength` has no output effect, but retaining
   *   it in the key is a safe, redundant invalidation.
   */
  private[sbtassembly] final case class CacheKey(
    assemblyInputFileStamps: FilesInfo[ModifiedFileInfo],
    resolvedMergeStrategyInfoByTargetPath: Map[String, (Boolean, String)],
    jarManifest: JManifest,
    repeatableBuild: Boolean,
    prependShellScript: Option[Seq[String]],
    maxHashLength: Option[Int],
    appendContentHash: Boolean,
  )

  private[sbtassembly] object CacheKey:
    import BasicJsonProtocol.given
    import sbt.Package.manifestFormat

    given JsonFormat[CacheKey] = BasicJsonProtocol.caseClass7(
      CacheKey.apply,
      key => Some((
        key.assemblyInputFileStamps,
        key.resolvedMergeStrategyInfoByTargetPath,
        key.jarManifest,
        key.repeatableBuild,
        key.prependShellScript,
        key.maxHashLength,
        key.appendContentHash,
      )),
    )(
      "assemblyInputFileStamps",
      "resolvedMergeStrategyInfoByTargetPath",
      "jarManifest",
      "repeatableBuild",
      "prependShellScript",
      "maxHashLength",
      "appendContentHash",
    )

  private[sbtassembly] def makeCacheKey(
    classes: Vector[NioPath],
    filteredJars: Vector[Attributed[HashedVirtualFileRef]],
    mergeStrategiesByPathList: Map[String, (Boolean, String)],
    jarManifest: JManifest,
    ao: AssemblyOption,
  )(using conv: FileConverter): CacheKey =
    CacheKey(
      assemblyInputFileStamps = lastModified(classes.map(_.toFile).toSet ++ filteredJars.map(toFile(_)).toSet),
      resolvedMergeStrategyInfoByTargetPath = mergeStrategiesByPathList,
      jarManifest = jarManifest,
      repeatableBuild = ao.repeatableBuild,
      prependShellScript = ao.prependShellScript,
      maxHashLength = ao.maxHashLength,
      appendContentHash = ao.appendContentHash,
    )

  private[sbtassembly] def cachedAssembly(inputs: CacheKey, cacheDir: File, scalaVersion: String, log: Logger)(
      buildAssembly: () => Out
  )(using conv: FileConverter): Out = {
    import CacheKey.given

    val cacheBlock = inputChanged(cacheDir / s"assembly-cacheKey-$scalaVersion") { (inputChanged, _: CacheKey) =>
      lastOutput(cacheDir / s"assembly-outputs-$scalaVersion") { (_: Unit, previousOutput: Option[String]) =>
        val outputExists = previousOutput.exists(path => new File(path).exists)
        (inputChanged, outputExists) match {
          case (false, true) =>
            log.info(s"Assembly jar up to date: ${previousOutput.get}")
            previousOutput.get
          case (true, true) =>
            log.debug("Building assembly jar due to changed inputs...")
            sbt.io.IO.delete(new File(previousOutput.get))
            toFile(buildAssembly()).getAbsolutePath
          case (_, _) =>
            log.debug("Building assembly jar due to missing output...")
            toFile(buildAssembly()).getAbsolutePath
        }
      }
    }
    toOutput(new File(cacheBlock(inputs)(())))
  }
end PluginCompat
