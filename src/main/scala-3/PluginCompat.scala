package sbtassembly

import java.io.File
import sbt.*
import sbt.Keys.test
import xsbti.FileConverter
import sbtcompat.PluginCompat.{ Out, toFile, toOutput }
import sjsonnew.BasicJsonProtocol

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

  private[sbtassembly] def cachedAssembly(inputs: CacheKey, cacheDir: File, scalaVersion: String, log: Logger)(
      buildAssembly: () => Out
  )(using conv: FileConverter): Out = {
    import BasicJsonProtocol.given

    AssemblyCache.cachedAssembly[Out, String](inputs, cacheDir, scalaVersion, log)(
      toCachedOutput = {  output =>
        toFile(output).getAbsolutePath
      },
      fromCachedOutput = { path =>
        toOutput(new File(path))
      },
      cachedOutputFile = { path =>
        new File(path)
      },
    )(buildAssembly)
  }
end PluginCompat
