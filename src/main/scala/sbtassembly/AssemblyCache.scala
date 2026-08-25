package sbtassembly

import java.util.jar.Manifest as JManifest
import sbt.*
import sbt.util.{ FilesInfo, ModifiedFileInfo }
import sbt.util.FileInfo.lastModified
import sbt.util.Tracked
import sjsonnew.{ BasicJsonProtocol, JsonFormat }

/**
 * Input state used to determine whether an assembly output can be reused.
 *
 * @param assemblyInputFileStamps               modification-time information for compiled class/resource outputs
 *                                              and selected JARs on the resolved assembly classpath; source files are not watched directly
 * @param resolvedMergeStrategyInfoByTargetPath resolved merge-strategy information, indexed by target path
 * @param shadeRules                            configured shade rules, rendered as strings. Rules reach the target paths
 *                                              only when they rename an entry, so keep rules and changes to a rule's
 *                                              target scope are otherwise invisible to this key
 * @param jarManifest                           manifest written to the output JAR
 * @param repeatableBuild                       whether output JAR entries are sorted by target path before writing to
 *                                              make the output bytes deterministic for reproducible builds
 * @param prependShellScript                    optional script prepended to the output JAR
 * @param maxHashLength                         optional maximum length of the SHA-1 appended to the output file name
 * @param appendContentHash                     whether the content hash is appended to the output file name.
 *                                              When `appendContentHash` is enabled, the output name changes from, for example,
 *                                              `app-assembly.jar` to `app-assembly-a1b2c3.jar`; `maxHashLength` selects how much of the
 *                                              SHA-1 is appended. Changing it therefore requires a rebuild for the requested output path.
 *                                              When content-hash appending is disabled, `maxHashLength` has no output effect, but retaining
 *                                              it in the key is a safe, redundant invalidation.
 * @param fixedTimestamp                        optional fixed timestamp applied to entries written to the output JAR
 * @param assemblyOutputPath                    normalized absolute path requested for the output JAR
 */
private[sbtassembly] final case class CacheKey(
  assemblyInputFileStamps: FilesInfo[ModifiedFileInfo],
  resolvedMergeStrategyInfoByTargetPath: Map[String, (Boolean, String)],
  shadeRules: Seq[String],
  jarManifest: JManifest,
  repeatableBuild: Boolean,
  prependShellScript: Option[Seq[String]],
  maxHashLength: Option[Int],
  appendContentHash: Boolean,
  fixedTimestamp: Option[Long],
  assemblyOutputPath: String,
)

private[sbtassembly] object CacheKey {
  import CacheImplicits.*
  import sbt.Package.manifestFormat

  implicit val format: JsonFormat[CacheKey] = BasicJsonProtocol.caseClassArray10(
    CacheKey.apply,
    key => Some((
      key.assemblyInputFileStamps,
      key.resolvedMergeStrategyInfoByTargetPath,
      key.shadeRules,
      key.jarManifest,
      key.repeatableBuild,
      key.prependShellScript,
      key.maxHashLength,
      key.appendContentHash,
      key.fixedTimestamp,
      key.assemblyOutputPath,
    )),
  )
}

private[sbtassembly] object AssemblyCache {
  def makeCacheKey(
    inputFiles: Set[File],
    mergeStrategiesByPathList: Map[String, (Boolean, String)],
    jarManifest: JManifest,
    fixedTimestamp: Option[Long],
    assemblyOutputPath: File,
    ao: AssemblyOption,
  ): CacheKey =
    CacheKey(
      assemblyInputFileStamps = lastModified(inputFiles),
      resolvedMergeStrategyInfoByTargetPath = mergeStrategiesByPathList,
      shadeRules = ao.shadeRules.map(_.toString),
      jarManifest = jarManifest,
      repeatableBuild = ao.repeatableBuild,
      prependShellScript = ao.prependShellScript,
      maxHashLength = ao.maxHashLength,
      appendContentHash = ao.appendContentHash,
      fixedTimestamp = fixedTimestamp,
      assemblyOutputPath = assemblyOutputPath.getAbsoluteFile.toPath.normalize.toString,
    )

  def cachedAssembly[Out, CachedOut](
    inputs: CacheKey,
    cacheDir: File,
    scalaVersion: String,
    log: Logger,
  )(
    toCachedOutput: Out => CachedOut,
    fromCachedOutput: CachedOut => Out,
    cachedOutputFile: CachedOut => File,
  )(
    buildAssembly: () => Out,
  )(implicit cachedOutputFormat: JsonFormat[CachedOut]): Out = {
    import CacheKey.format

    val cacheBlock = Tracked.inputChanged[CacheKey, Unit => CachedOut](cacheDir / s"assembly-cacheKey-$scalaVersion") {
      (inputChanged, _: CacheKey) =>
        Tracked.lastOutput[Unit, CachedOut](cacheDir / s"assembly-outputs-$scalaVersion") { (_: Unit, previousOutput: Option[CachedOut]) =>
          val previousOutputFile = previousOutput.map(cachedOutputFile)
          val outputExists = previousOutputFile.exists(_.exists())
          (inputChanged, outputExists) match {
            case (false, true) =>
              log.info("Assembly jar up to date: " + previousOutputFile.get.toPath)
              previousOutput.get
            case (true, true) =>
              log.debug("Building assembly jar due to changed inputs...")
              IO.delete(previousOutputFile.get)
              toCachedOutput(buildAssembly())
            case (_, _) =>
              log.debug("Building assembly jar due to missing output...")
              toCachedOutput(buildAssembly())
          }
        }
    }
    fromCachedOutput(cacheBlock(inputs)(()))
  }
}
