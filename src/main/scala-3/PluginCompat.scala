package sbtassembly

import java.io.File
import java.nio.file.{ Path => NioPath }
import java.util.jar.{ Manifest => JManifest }
import sbt.*
import sbt.util.cacheLevel
import sbt.Keys.*
import sbt.Tags.Tag
import sbt.librarymanagement.ModuleID
import sjsonnew.*
import xsbti.{ FileConverter, HashedVirtualFileRef, VirtualFile }

object PluginCompat:
  type FileRef = HashedVirtualFileRef
  type Out = VirtualFile
  type JarManifest = PackageOption.JarManifest
  type MainClass = PackageOption.MainClass
  type ManifestAttributes = PackageOption.ManifestAttributes
  type FixedTimestamp = PackageOption.FixedTimestamp

  val CollectionConverters = scala.collection.parallel.CollectionConverters

  val moduleIDStr = Keys.moduleIDStr
  def parseModuleIDStrAttribute(str: String): ModuleID =
    Classpaths.moduleIdJsonKeyFormat.read(str)

  def toNioPath(a: Attributed[HashedVirtualFileRef])(implicit conv: FileConverter): NioPath =
    conv.toPath(a.data)
  inline def toFile(a: Attributed[HashedVirtualFileRef])(implicit conv: FileConverter): File =
    toNioPath(a).toFile()
  def toFile(p: NioPath): File =
    p.toFile()
  def toOutput(x: File)(implicit conv: FileConverter): VirtualFile =
    conv.toVirtualFile(x.toPath())
  def toNioPaths(cp: Seq[Attributed[HashedVirtualFileRef]])(implicit conv: FileConverter): Vector[NioPath] =
    cp.map(toNioPath).toVector
  inline def toFiles(cp: Seq[Attributed[HashedVirtualFileRef]])(implicit conv: FileConverter): Vector[File] =
    toNioPaths(cp).map(_.toFile())

  trait AssemblyKeys0:
    @cacheLevel(include = Array.empty)
    lazy val assemblyOutputPath = taskKey[File]("output path of the über jar")
  end AssemblyKeys0

  val assemblyTag = Tag("assembly")
  import sbt.util.CacheImplicits.given

  private given forHashing: IsoLList.Aux[
    AssemblyOption,
    Boolean :*: Boolean :*: Boolean :*: Classpath :*: Boolean :*: Boolean :*:
      Vector[String] :*: Option[Int] :*: Vector[String] :*: String :*: LNil
  ] =
    LList.iso(
      { (v: AssemblyOption) =>
        ("includeBin", v.includeBin) :*:
          ("includeScala", v.includeScala) :*:
          ("includeDependency", v.includeDependency) :*:
          ("excludedJars", v.excludedJars) :*:
          ("repeatableBuild", v.repeatableBuild) :*:
          ("appendContentHash", v.appendContentHash) :*:
          ("prependShellScript", v.prependShellScript match
            case Some(xs) => xs.toVector
            case None     => Vector.empty) :*:
          ("maxHashLength", v.maxHashLength) :*:
          ("shadeRules", v.shadeRules.toVector.map(_.toString)) :*:
          ("scalaVersion", v.scalaVersion) :*:
          LNil
      },
      {
        case _ => ???
      }
    )

  def assemblyTask(key: TaskKey[FileRef])(
    f: (
      String,
      File,
      Classpath,
      Classpath,
      AssemblyOption,
      Seq[PackageOption],
      FileConverter,
      File,
      Logger
    ) => Out
  ): Def.Initialize[Task[HashedVirtualFileRef]] = Def.cachedTask {
    val s = (key / streams).value
    val conv = fileConverter.value
    val _ = AssemblyKeys.assemblyMetaBuildHash.value
    val out: VirtualFile = f(
      (key / AssemblyKeys.assemblyJarName).value.replaceAll(".jar", ""),
      (key / AssemblyKeys.assemblyOutputPath).value,
      (AssemblyKeys.assembly / fullClasspath).value,
      (AssemblyKeys.assembly / externalDependencyClasspath).value,
      (key / AssemblyKeys.assemblyOption).value,
      (key / packageOptions).value,
      conv,
      s.cacheDirectory,
      s.log
    )
    Def.declareOutput(out)
    out: HashedVirtualFileRef
  }.tag(assemblyTag)

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
      buildAssembly: () => PluginCompat.Out
  ): PluginCompat.Out =
    buildAssembly()
end PluginCompat
