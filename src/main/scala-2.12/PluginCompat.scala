package sbtassembly

import sbt.*
import sbt.Keys.test

private[sbtassembly] object PluginCompat {
  type MainClass = sbt.Package.MainClass

  object CollectionConverters

  def baseTestSettings: Seq[sbt.Def.Setting[?]] = Seq(
    AssemblyKeys.assembly / test := (()),
    AssemblyKeys.assemblyPackageScala / test := (AssemblyKeys.assembly / test).value,
    AssemblyKeys.assemblyPackageDependency / test := (AssemblyKeys.assembly / test).value,
  )

  val Streamable = scala.tools.nsc.io.Streamable

  private[sbtassembly] def cachedAssembly(inputs: CacheKey, cacheDir: File, scalaVersion: String, log: Logger)(
      buildAssembly: () => File
  ): File = {
    AssemblyCache.cachedAssembly[File, File](inputs, cacheDir, scalaVersion, log)(
      toCachedOutput = identity,
      fromCachedOutput = identity,
      cachedOutputFile = identity
    )(buildAssembly)
  }
}
