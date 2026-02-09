package sbtassembly

import java.io.File
import java.nio.file.{ Path => NioPath }
import java.util.jar.{ Manifest => JManifest }
import sbt.*
import Keys.test
import xsbti.{ FileConverter, HashedVirtualFileRef, VirtualFile }
import sbtcompat.PluginCompat.Out

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

  type CacheKey = Int
  private[sbtassembly] def makeCacheKey(
    classes: Vector[NioPath],
    filteredJars: Vector[Attributed[HashedVirtualFileRef]],
    mergeStrategiesByPathList: Map[String, (Boolean, String)],
    jarManifest: JManifest,
    ao: AssemblyOption,
  ): CacheKey = 0

  private[sbtassembly] def cachedAssembly(inputs: CacheKey, cacheDir: File, scalaVersion: String, log: Logger)(
      buildAssembly: () => Out
  ): Out =
    buildAssembly()
end PluginCompat
