package sbtassembly

import com.eed3si9n.jarjarabrams._
import sbt.Def.Initialize
import sbt.Keys._
import sbt.Package.{ manifestFormat, JarManifest, MainClass, ManifestAttributes, FixedTimestamp }
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.io.{ DirectoryFilter => _, IO => _, Path => _, Using }
import sbt.util.{ FilesInfo, Level, ModifiedFileInfo }
import sbt.{ File, Logger, _ }
import sbt.Tags.Tag
import CacheImplicits._
import sbtassembly.AssemblyPlugin.autoImport.{ Assembly => _, _ }

import java.io.{ BufferedInputStream, ByteArrayInputStream, FileInputStream, InputStream }
import java.net.URI
import java.nio.file.attribute.{ BasicFileAttributeView, FileTime, PosixFilePermission }
import java.nio.file.{ Path => NioPath, _ }
import java.security.MessageDigest
import java.time.Instant
import java.util.jar.{ Attributes => JAttributes, JarFile, Manifest => JManifest }
import scala.annotation.tailrec
import scala.collection.GenSeq
import scala.collection.JavaConverters._
import scala.language.postfixOps
import xsbti.FileConverter
import PluginCompat.*
import CollectionConverters.{ given, * }

object Assembly {
  // used for contraband
  type SeqString = Seq[String]
  type SeqShadeRules = Seq[com.eed3si9n.jarjarabrams.ShadeRule]
  type LazyInputStream = () => InputStream

  val defaultShadeRules: Seq[com.eed3si9n.jarjarabrams.ShadeRule] = Nil
  val newLine: String = "\n"
  val indent: String = " " * 2
  val newLineIndented: String = newLine + indent

  val assemblyTag = Tag("assembly")

  private[sbtassembly] val scalaPre213Libraries = Vector(
    "scala-actors",
    "scala-compiler",
    "scala-continuations",
    "scala-library",
    "scala-parser-combinators",
    "scala-reflect",
    "scala-swing",
    "scala-xml"
  )
  private[sbtassembly] val scala213AndLaterLibraries =
    Vector("scala-actors", "scala-compiler", "scala-continuations", "scala-library", "scala-reflect")

  /* Closeable resources */
  private[sbtassembly] val jarFileSystemResource =
    Using.resource((uri: URI) => FileSystems.newFileSystem(uri, Map("create" -> "true").asJava))
  private[sbtassembly] val jarEntryInputStreamResource = Using.resource((entry: JarEntry) => entry.stream())
  private[sbtassembly] val jarEntryOutputStreamResource = Using.resource((path: NioPath) =>
    Files.newOutputStream(path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
  )
  private[sbtassembly] val byteArrayInputStreamResource =
    Using.resource((bytes: Array[Byte]) => new ByteArrayInputStream(bytes))

  private[sbtassembly] val strategyDisplayName = (mergeStrategy: MergeStrategy) =>
    s"${mergeStrategy.name}${if (mergeStrategy.isBuiltIn) "" else " (Custom)"}"

  /**
   * Represents an assembly jar entry
   * @param target the path to write in the jar
   * @param stream the byte payload
   */
  case class JarEntry(target: String, stream: LazyInputStream) {
    override def toString = s"Jar entry = $target"
  }

  /**
   * Encapsulates the result of applying a merge strategy
   *
   * @param entries the resulting [[JarEntry]]s
   * @param origins the original [[Dependency]]s that were merged
   * @param mergeStrategy the [[MergeStrategy]] applied
   */
  case class MergedEntry(entries: Vector[JarEntry], origins: Vector[Dependency], mergeStrategy: MergeStrategy) {
    override def toString =
      s"""
         |Merge Strategy Used: ${strategyDisplayName(mergeStrategy)}
         |Resulting entries:
         |  ${if (entries.nonEmpty) entries.mkString(newLineIndented) else "None"}
         |Merge origins:
         |  ${origins.mkString(newLineIndented)}
         |""".stripMargin
  }

  /**
   * Represents a Dependency that is an assembly jar candidate
   */
  sealed trait Dependency {

    /**
     * @return the original source path, before applying assembly processing (shading, merging, etc)
     */
    def source: String

    /**
     * @return the target path to be written to the assembly jar, after applying assembly processing (shading, merging, etc)
     */
    def target: String

    /**
     * @return a lazy payload of bytes that represents the actual content to be written
     */
    def stream: LazyInputStream

    /**
     * @return the originating module/jar entry if present
     */
    def module: Option[ModuleCoordinate]

    /**
     * @return if the dependency is an internal or project dependency
     */
    def isProjectDependency: Boolean

    protected def renamedTargetInfo(source: String, target: String): String =
      if (source == target) s"target = $target"
      else s"target = $target (from original source = $source)"
  }

  /**
   * Represents an internal/project dependency
   *
   * @param name the sbt project name
   * @param source the original source path, before applying assembly processing (shading, merging, etc)
   * @param target the target path to be written to the assembly jar, after applying assembly processing (shading, merging, etc)
   * @param stream a lazy payload of bytes that represents the actual content to be written
   */
  case class Project(name: String, source: String, target: String, stream: LazyInputStream) extends Dependency {
    override def isProjectDependency = true
    override def module: Option[ModuleCoordinate] = Option.empty
    override def toString = s"Project name = $name, ${renamedTargetInfo(source, target)}"
  }

  /**
   * Represents an entry of an external/library dependency jar
   *
   * @param moduleCoord the library's organization, name and version (i.e. Maven GAV)
   * @param source the original source path in the jar, before applying assembly processing (shading, merging, etc)
   * @param target the target path to be written to the assembly jar, after applying assembly processing (shading, merging, etc)
   * @param stream a lazy payload of bytes that represents the actual content to be written
   */
  case class Library(moduleCoord: ModuleCoordinate, source: String, target: String, stream: LazyInputStream)
      extends Dependency {
    override def isProjectDependency = false
    override def module: Option[ModuleCoordinate] = Option(moduleCoord)
    override def toString = {
      val jarOrg = if (moduleCoord.organization.nonEmpty) s" jar org = ${moduleCoord.organization}," else ""
      s"Jar name = ${moduleCoord.jarName},$jarOrg entry ${renamedTargetInfo(source, target)}"
    }
  }

  /**
   * Represents an external/library JAR (i.e. Maven GAV)
   * @param organization jar org
   * @param name jar name
   * @param version jar version
   */
  case class ModuleCoordinate(organization: String = "", name: String, version: String = "") {
    val jarName: String = s"$name${if (version.nonEmpty) "-" else ""}$version.jar"
  }

  def assemblyTask(key: TaskKey[PluginCompat.FileRef]): Initialize[Task[PluginCompat.Out]] = Def.task {
    val t = (key / test).value
    val s = (key / streams).value
    val conv = fileConverter.value
    assemble(
      (key / assemblyJarName).value.replaceAll(".jar", ""),
      (key / assemblyOutputPath).value,
      (assembly / fullClasspath).value,
      (assembly / externalDependencyClasspath).value,
      (key / assemblyOption).value,
      (key / packageOptions).value,
      conv,
      s.cacheDirectory,
      s.log
    )
  }.tag(assemblyTag)

  /**
   * Builds an assembly jar
   *
   * @param targetJarName the name of the jar to build
   * @param output the path of the jar to build
   * @param classpath full classpath of the project and all its external dependencies
   * @param externalDepClasspath the external dependency classpath
   * @param ao assembly options configured via the build
   * @param po package options configured via the build
   * @param cacheDir the task cache directory
   * @param log the sbt logger
   * @return the built jar as an [[sbt.File]]
   */
  def assemble(
      targetJarName: String,
      output: File,
      classpath: Classpath,
      // even though fullClasspath includes all dependencies, externalDepClasspath is needed to figure out
      // which exact jars are "external"  when using the packageDependency option.
      externalDepClasspath: Classpath,
      ao: AssemblyOption,
      po: Seq[PackageOption],
      conv: FileConverter,
      cacheDir: File,
      log: Logger
  ): PluginCompat.Out = {
    def timed[A](level: Level.Value, desc: String)(f: => A): A = {
      log.log(level, desc + " start:")
      val start = Instant.now().toEpochMilli
      val res = f
      val end = Instant.now().toEpochMilli
      log.log(level, s"$desc end. Took ${end - start} ms")
      res
    }
    implicit val ev: FileConverter = conv // used by PluginCompat
    val (jars, dirs) = timed(Level.Debug, "Separate classpath projects and all dependencies") {
      classpath.toVector
        .sortBy(x => toNioPath(x).toAbsolutePath().toString())
        .partition(x => ClasspathUtil.isArchive(toNioPath(x)))
    }
    val externalDeps = timed(Level.Debug, "Collect only external dependencies") {
      externalDepClasspath.toSet
        .filter(x => ClasspathUtil.isArchive(toNioPath(x)))
    }
    val excludedJars: Vector[NioPath] = timed(Level.Debug, "Collect excluded jar names") {
      toNioPaths(ao.excludedJars)
    }
    val scalaLibraries = {
      val scalaVersionParts = VersionNumber(ao.scalaVersion)
      val isScala213AndLater =
        scalaVersionParts.numbers.length >= 2 && scalaVersionParts._1.get >= 2 && scalaVersionParts._2.get >= 13
      if (isScala213AndLater) scala213AndLaterLibraries else scalaPre213Libraries
    }

    val filteredJars = timed(Level.Debug, "Filter jars") {
      jars.flatMap {
        case jar if excludedJars.contains(jar) => None
        case jar if isScalaLibraryFile(scalaLibraries, toNioPath(jar)) =>
          if (ao.includeScala) Some(jar) else None
        case jar if externalDeps.contains(jar) =>
          if (ao.includeDependency) Some(jar) else None
        case jar =>
          if (ao.includeBin) Some(jar) else None
      }
    }

    val classShader = shader(ao.shadeRules.filter(_.isApplicableToCompiling), log)
    val classByParentDir: Vector[(NioPath, NioPath)] =
      if (!ao.includeBin) Vector.empty
      else dirs.flatMap { dir0 =>
        val dir = toNioPath(dir0)
        (dir.toFile ** (-DirectoryFilter)).get().map(dir -> _.toPath())
      }
    val classMappings =
      timed(Level.Debug, "Collect and shade project classes") {
        classByParentDir
          .flatMap { case (parentDir, file) =>
            val originalTarget = parentDir.relativize(file).toString
            classShader(originalTarget, () => new BufferedInputStream(new FileInputStream(file.toFile())))
              .map { case (shadedName, stream) =>
                Project(targetJarName, originalTarget, shadedName, stream)
              }
          }
      }

    val jarShader = (module: ModuleCoordinate) =>
      shader(
        ao.shadeRules
          .filter(rule =>
            rule.isApplicableToAll ||
              rule.isApplicableTo(
                com.eed3si9n.jarjarabrams.ModuleCoordinate(module.organization, module.name, module.version)
              )
          ),
        log
      )
    val (jarFiles, jarFileEntries) = timed(Level.Debug, "Collect and shade dependency entries") {
      filteredJars.par.map { jar =>
        val module = jar.metadata
          .get(PluginCompat.moduleIDStr)
          .map(PluginCompat.parseModuleIDStrAttribute)
          .map(m => ModuleCoordinate(m.organization, m.name, m.revision))
          .getOrElse(ModuleCoordinate("", jar.data.name.replaceAll(".jar", ""), ""))
        val jarFile = new JarFile(toFile(jar))
        jarFile -> jarFile
          .entries()
          .asScala
          .filterNot(_.isDirectory)
          .toVector
          .par
          .flatMap { entry =>
            jarShader(module)(entry.getName, () => jarFile.getInputStream(entry))
              .map { case (shadedName, stream) =>
                Library(module, entry.getName, shadedName, stream)
              }
          }
      }.unzip
    }
    try {
      val (mappingsToRename, others) = timed(Level.Debug, "Collect renames") {
        (classMappings ++ jarFileEntries.flatten)
          .partition(mapping => ao.mergeStrategy(mapping.target).name == MergeStrategy.rename.name)
      }
      val renamedEntries = timed(Level.Debug, "Process renames") {
        merge(mappingsToRename, path => Option(ao.mergeStrategy(path)), log)
      }
      // convert renames back to `Dependency`s for second-pass merge and cache-invalidation
      val renamedDependencies = convertToDependency(renamedEntries)
      val (jarManifest, timestamp) = createManifest(po, log)
      // exclude renames from the second pass
      val secondPassMergeStrategy = (path: String) => {
        val mergeStrategy = ao.mergeStrategy(path)
        if (mergeStrategy.name == MergeStrategy.rename.name) Option.empty
        else Option(mergeStrategy)
      }
      val buildAssembly: () => PluginCompat.Out  = () => {
        val mergedEntries = timed(Level.Debug, "Merge all conflicting jar entries (including those renamed)") {
          merge(renamedDependencies ++ others, secondPassMergeStrategy, log)
        }
        timed(Level.Debug, "Report merge results") {
          reportMergeResults(renamedEntries, log)
          reportMergeResults(mergedEntries, log)
        }
        timed(Level.Debug, "Finding remaining conflicts that were not merged") {
          reportConflictsMissedByTheMerge(mergedEntries, log)
        }
        val jarEntriesToWrite = timed(Level.Debug, "Sort/Parallelize merged entries") {
          if (ao.repeatableBuild) // we need the jars in a specific order to have a consistent hash
            mergedEntries.flatMap(_.entries)
              .seq.sortBy(_.target)
          else // we actually gain performance when creating the jar in parallel, but we won't have a consistent hash
            mergedEntries.flatMap(_.entries)
              .par.toVector
        }
        val localTime = timestamp
          .map(t => t - java.util.TimeZone.getDefault.getOffset(t))
          .getOrElse(System.currentTimeMillis())

        timed(Level.Debug, "Create jar") {
          if (output.isDirectory) {
            val invalidPath = output.toPath.toAbsolutePath.normalize
            log.error(s"expected a file name for assemblyOutputPath, but found a directory: ${invalidPath}; fix the setting or delete the directory")
            throw new RuntimeException("Exiting task")
          } else {
            IO.delete(output)
            val dest = output.getParentFile
            if (!dest.exists()) {
              dest.mkdirs()
            }
            createJar(output, jarEntriesToWrite, jarManifest, localTime)
          }
        }
        val fullSha1 = timed(Level.Debug, "Hash newly-built Jar") {
          hash(output)
        }
        val builtAssemblyJar =
          if (ao.appendContentHash) {
            val sha1 = ao.maxHashLength.fold(fullSha1)(length => fullSha1.take(length))
            val newName = output.getName.replaceAll("\\.[^.]*$", "") + "-" + sha1 + ".jar"
            val outputWithHash = new File(output.getParentFile, newName)
            IO.delete(outputWithHash)
            Files.move(output.toPath, outputWithHash.toPath, StandardCopyOption.REPLACE_EXISTING)
            outputWithHash
          } else output
        ao.prependShellScript
          .foreach { shellScript =>
            timed(Level.Info, "Prepend shell script") {
              val tmp = cacheDir / "assemblyExec.tmp"
              if (tmp.exists) IO.delete(tmp)
              Files.move(builtAssemblyJar.toPath, tmp.toPath)
              IO.write(builtAssemblyJar, shellScript.map(_ + "\n").mkString, append = false)
              Using.fileOutputStream(true)(builtAssemblyJar)(out => IO.transfer(tmp, out))
              IO.delete(tmp)
              if (!scala.util.Properties.isWin) {
                val posixPermissions = Files.getPosixFilePermissions(builtAssemblyJar.toPath)
                posixPermissions.add(PosixFilePermission.OWNER_EXECUTE)
                posixPermissions.add(PosixFilePermission.GROUP_EXECUTE)
                posixPermissions.add(PosixFilePermission.OTHERS_EXECUTE)
                Files.setPosixFilePermissions(builtAssemblyJar.toPath, posixPermissions)
              }
            }
          }
        log.info("Built: " + builtAssemblyJar.toPath)
        log.info("Jar hash: " + fullSha1)
        toOutput(builtAssemblyJar)
      }
      val mergeStrategiesByPathList = timed(Level.Debug, "Collect all merge strategies for cache check") {
        // collect all
        (renamedDependencies ++ others)
          .groupBy(_.target)
          .map { case (target, _) =>
            val mergeStrategy = secondPassMergeStrategy(target).getOrElse(MergeStrategy.deduplicate)
            target -> (mergeStrategy.isBuiltIn -> mergeStrategy.name)
          }
      }
      if (
        ao.cacheOutput &&
        !mergeStrategiesByPathList.values
          .exists { case (isBuiltIn, name) =>
            // if there is at least one custom merge strategy, we cannot predict what it does so we cannot use caching
            if (!isBuiltIn) log.warn(s"Caching disabled because of a custom merge strategy: '$name'")
            !isBuiltIn
          }
      ) {
        val (_, classes) = classByParentDir.unzip
        val cacheKey = makeCacheKey(
          classes,
          filteredJars,
          mergeStrategiesByPathList,
          jarManifest,
          ao,
        )
        cachedAssembly(cacheKey, cacheDir, ao.scalaVersion, log)(buildAssembly)
      } else buildAssembly()
    } finally
      timed(Level.Debug, "Close library jar references") {
        jarFiles.foreach(_.close())
      }
  }

  /**
   * Returns a flag if the given file is a junk file
   *
   * @param fileName the given file to check
   * @return flag representing if the file is a junk file
   */
  def isSystemJunkFile(fileName: String): Boolean =
    fileName.toLowerCase match {
      case ".ds_store" | "thumbs.db" => true
      case _                         => false
    }

  /**
   * Returns a flag if the given file is a license file
   *
   * @param fileName the given file to check
   * @return flag representing if the file is a license file
   */
  def isLicenseFile(fileName: String): Boolean = {
    val LicenseFile = """(\._license|license|licence|notice|copying)([.]\w+)?$""".r
    fileName.toLowerCase match {
      case LicenseFile(_, ext) if ext != ".class" => true // DISLIKE
      case _                                      => false
    }
  }

  /**
   * Returns a flag if the given file is a readme file
   *
   * @param fileName the given file to check
   * @return flag representing if the file is a readme file
   */
  def isReadme(fileName: String): Boolean = {
    val ReadMe = """(readme|about)([.]\w+)?$""".r
    fileName.toLowerCase match {
      case ReadMe(_, ext) if ext != ".class" => true
      case _                                 => false
    }
  }

  /**
   * Returns a flag if the given file is a config file
   *
   * @param fileName the given file to check
   * @return flag representing if the file is a config file
   */
  def isConfigFile(fileName: String): Boolean =
    fileName.toLowerCase match {
      case "reference.conf" | "reference-overrides.conf" | "application.conf" | "rootdoc.txt" | "play.plugins" => true
      case _                                                                                                   => false
    }

  /**
   * Returns a flag if the given file is part of the Scala library
   *
   * @param scalaLibraries the Scala library as a collection
   * @param file the given file to check
   * @return flag representing if the file is a Scala library
   */
  def isScalaLibraryFile(scalaLibraries: Vector[String], file: NioPath): Boolean =
    scalaLibraries exists { x => file.getFileName.toString.startsWith(x) }

  private[sbtassembly] def shader(
      shadeRules: SeqShadeRules,
      log: Logger
  ): (String, LazyInputStream) => Option[(String, LazyInputStream)] =
    if (shadeRules.isEmpty)
      (name: String, inputStream: LazyInputStream) => Some(name -> inputStream)
    else {
      val bytecodeShader = Shader.bytecodeShader(
        shadeRules,
        verbose = false,
        skipManifest = false,
      )
      (name: String, inputStream: LazyInputStream) => {
        val is = inputStream()
        val shadeResult = bytecodeShader(Streamable.bytes(is), name)
        if (shadeResult.isEmpty) log.debug(s"Shade discarded: $name")
        shadeResult.map { case (bytes, shadedName) =>
          if (name != shadedName) log.debug(s"Shaded: $name -> $shadedName")
          shadedName -> (() =>
            new ByteArrayInputStream(bytes) {
              override def close(): Unit = is.close()
            }
          )
        }
      }
    }

  private[sbtassembly] def createJar(
      output: File,
      entries: GenSeq[JarEntry],
      manifest: JManifest,
      localTime: Long
  ): Unit = {
    jarFileSystemResource(URI.create(s"jar:${output.toURI}")) { jarFs =>
      val manifestPath = jarFs.getPath("META-INF/MANIFEST.MF")
      Files.createDirectories(manifestPath.getParent)
      val manifestOut = Files.newOutputStream(
        manifestPath,
        StandardOpenOption.CREATE
      )
      manifest.write(manifestOut)
      manifestOut.close()

      entries
        .foreach { entry =>
          val path = jarFs.getPath(entry.target)
          if (path.getParent != null && !Files.exists(path.getParent)) Files.createDirectories(path.getParent)
          jarEntryInputStreamResource(entry) { inputStream =>
            jarEntryOutputStreamResource(path) { outputStream =>
              IO.transfer(inputStream, outputStream)
            }
          }
        }

      val fileTime = FileTime.fromMillis(localTime)
      Files
        .walk(jarFs.getPath("/"))
        .filter(_.toString != "/")
        .forEach { path =>
          val view = Files.getFileAttributeView(path, classOf[BasicFileAttributeView])
          view.setTimes(fileTime, fileTime, fileTime)
        }
    }
  }

  private[sbtassembly] def createManifest(po: Seq[PackageOption], log: Logger): (JManifest, Option[Long]) = {
    import scala.language.reflectiveCalls
    val manifest = new JManifest
    val main = manifest.getMainAttributes.asScala
    var time: Option[Long] = None
    for (option <- po)
      option match {
        case JarManifest(mergeManifest)     => Package.mergeManifests(manifest, mergeManifest)
        case MainClass(mainClassName)       => main.put(JAttributes.Name.MAIN_CLASS, mainClassName)
        case ManifestAttributes(attrs @ _*) => main ++= attrs
        case FixedTimestamp(value)          => time = value 
        case _                              =>
          log.warn("Ignored unknown package option " + option)
      }
    if (!main.contains(JAttributes.Name.MANIFEST_VERSION)) main.put(JAttributes.Name.MANIFEST_VERSION, "1.0")
    manifest -> time
  }

  private[sbtassembly] def convertToDependency(renames: Vector[MergedEntry]): Vector[Dependency] =
    renames
      .flatMap { renamedEntry =>
        renamedEntry.entries
          .zip(renamedEntry.origins)
          .map {
            case (entry, p @ Project(name, source, _, stream)) => p.copy(name, source, entry.target, stream)
            case (entry, l @ Library(moduleCoord, source, _, stream)) =>
              l.copy(moduleCoord, source, entry.target, stream)
          }
      }

  private[sbtassembly] def merge(
      mappings: Vector[Dependency],
      mergeStrategies: String => Option[MergeStrategy],
      log: Logger
  ): Vector[MergedEntry] = {
    val (successfullyMerged, failures) = mappings
      .groupBy(_.target)
      .par
      .map { case (target, mappings) =>
        mergeStrategies(target)
          .map(MergeStrategy.merge(_, mappings))
          .getOrElse(MergeStrategy.merge(MergeStrategy.deduplicate, mappings))
      }
      .partition(_.isRight)
    if (failures.nonEmpty) {
      log.error(s"${failures.size} error(s) were encountered during the merge:")
      throw new RuntimeException(failures.map(_.left.get).mkString(newLine, newLine, ""))
    }
    successfullyMerged.map(_.right.get).toVector
  }

  private[sbtassembly] def reportMergeResults(mergedEntries: Vector[MergedEntry], log: Logger): Unit =
    mergedEntries
      .groupBy(entry => entry.mergeStrategy.isBuiltIn -> entry.mergeStrategy.name)
      .values
      .seq // we need to switch to sequential here to not mess up the detail logs
      .foreach { entries => // TODO figure out how to use BufferedAppender so we can keep this parallel
        val mergeStrategy = entries.head.mergeStrategy
        val entriesToNotify = entries.filter(entry => entry.origins.size >= mergeStrategy.notifyThreshold)
        val totalMerged = entriesToNotify.map(_.origins.size).sum
        if (totalMerged > 0) {
          val notifyDetails =
            if (mergeStrategy.detailLogLevel == Level.Debug) " (Run the task at debug level to see the details)"
            else ""
          log.log(
            mergeStrategy.summaryLogLevel,
            s"$totalMerged file(s) merged using strategy '${strategyDisplayName(mergeStrategy)}'$notifyDetails"
          )
          log.log(mergeStrategy.detailLogLevel, entriesToNotify.seq.mkString(""))
        }
      }

  private[sbtassembly] def reportConflictsMissedByTheMerge(mergedEntries: Vector[MergedEntry], log: Logger): Unit = {
    val parentPaths = (entry: JarEntry) => {
      val target = Paths.get(entry.target)
      for {
        i <- 1 until target.getNameCount
      } yield target.subpath(0, i).toString
    }
    val parentDirToOrigins =
      (for {
        mergedEntry <- mergedEntries.par
        jarEntry <- mergedEntry.entries
        parentPath <- parentPaths(jarEntry)
      } yield parentPath -> mergedEntry.origins)
        .groupBy { case (path, _) => path }
        .mapValues(_.flatMap { case (_, origins) => origins })

    // Signal error on parent folders that conflict with files that didn't have a merge strategy, so the user can create a MergeStrategy for them
    val filesConflictingWithDirs =
      mergedEntries.par
        .flatMap { entry =>
          entry.entries
            .map { jarEntry =>
              val dirConflicts = parentDirToOrigins.getOrElse(jarEntry.target, Vector.empty)
              jarEntry.target -> entry.origins -> dirConflicts
            }
        }
        .filter { case ((_, _), conflictingDirs) =>
          conflictingDirs.nonEmpty
        }
        .map { case ((target, conflictingFiles), conflictingDirectories) =>
          val sources = conflictingFiles.mkString(newLineIndented)
          val directories = conflictingDirectories.mkString(newLineIndented)
          Left(
            s"Files to be written at '$target' have the same name as directories to be written:$newLineIndented$directories$newLineIndented$sources"
          )
        }

    if (filesConflictingWithDirs.nonEmpty) {
      log.error(s"${filesConflictingWithDirs.size} error(s) were still encountered after the merge:")
      throw new RuntimeException(filesConflictingWithDirs.map(_.left.get).mkString(newLine, newLine, ""))
    }
  }

  private[sbtassembly] def hash(file: File): String =
    bytesToString(sha1.digest(FileInfo.hash(file).hash.seq.toArray))

  private[sbtassembly] def sha1 = MessageDigest.getInstance("SHA-1")

  private[sbtassembly] def sha1Content(stream: InputStream): String =
    {
      val messageDigest = sha1
      val buffer = new Array[Byte](8192)

      @tailrec
      def read(): Unit = {
        val byteCount = stream.read(buffer)
        if (byteCount >= 0) {
          messageDigest.update(buffer, 0, byteCount)
          read()
        }
      }

      read()
      stream.close()
      bytesToString(messageDigest.digest())
    }

  private[sbtassembly] def bytesToString(bytes: Seq[Byte]): String =
    bytes map {
      "%02x".format(_)
    } mkString
}

object PathList {
  private val sysFileSep = "/"

  def unapplySeq(path: String): Option[Seq[String]] = {
    val sanitizedPath = if (path.contains('\\')) path.replace('\\', '/') else path
    val split = sanitizedPath.split(sysFileSep)
    if (split.isEmpty) Option.empty
    else Option(split.toList)
  }
}
