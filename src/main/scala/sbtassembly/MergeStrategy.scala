package sbtassembly

import sbt.io.{ IO, Using }
import sbt.util.Level
import sbtassembly.Assembly._
import sbtassembly.AssemblyUtils.AppendEofInputStream

import java.io.{ BufferedReader, ByteArrayInputStream, InputStreamReader, SequenceInputStream }
import java.nio.charset.Charset
import java.util.Collections
import scala.collection.JavaConverters._
import scala.reflect.io.Streamable

/**
 * Merges dependencies that share the same target path or transforms them before adding to the assembly
 *
 * Return a `Right[Vector[JarEntry]]]` for a successful merge or a `Left[String]` of an error message to fail the process
 */
sealed trait MergeStrategy extends (Vector[Dependency] => Either[String, Vector[JarEntry]]) {

  /**
   * A descriptive name of the merge strategy which will be used in logging the merge results
   *
   * @return
   */
  def name: String

  /**
   * Report the merge counts only if the number of files to be merged are >= this threshold
   *
   * @return the threshold
   */
  def notifyThreshold: Int

  /**
   * Log details of the merge only if assembly / logLevel is set to this or a less-restrictive level
   *
   * @return the log level threshold for logging the details
   */
  def detailLogLevel: Level.Value = Level.Debug

  /**
   * Log the summary message of the merge only if assembly / logLevel is set to this or a less-restrictive level
   *
   * @return the log level threshold for logging the summary
   */
  def summaryLogLevel: Level.Value = Level.Info

  /**
   * Namespaces built-in strategies from user-provided ones
   *
   * @return if this merge strategy is a built-in/internal instance provided by the plugin
   */
  def isBuiltIn: Boolean = true
}
abstract private[sbtassembly] class InternalMergeStrategy(val name: String, val notifyThreshold: Int)
    extends MergeStrategy
abstract private[sbtassembly] class CustomMergeStrategy(val name: String, val notifyThreshold: Int)
    extends MergeStrategy {
  override val isBuiltIn = false
}

object MergeStrategy {
  type StringToMergeStrategy = String => MergeStrategy
  private val FileExtension = """([.]\w+)$""".r
  private val dependencyStreamResource = Using.resource((dependency: Dependency) => dependency.stream())

  /**
   * Picks the first of the conflicting files in classpath order
   */
  val first: MergeStrategy = MergeStrategy("First") { conflicts =>
    Right(Vector(JarEntry(conflicts.head.target, conflicts.head.stream)))
  }

  /**
   * Picks the first of the conflicting files in classpath order
   */
  val last: MergeStrategy = MergeStrategy("Last") { conflicts =>
    Right(Vector(JarEntry(conflicts.last.target, conflicts.last.stream)))
  }

  /**
   * Picks the dependency that is the first project/internal dependency, otherwise picks the first library/external dependency
   */
  val preferProject: MergeStrategy = MergeStrategy("PreferProject") { conflicts =>
    val entry = conflicts.find(_.isProjectDependency).getOrElse(conflicts.head)
    Right(Vector(JarEntry(entry.target, entry.stream)))
  }

  /**
   * Fails the merge on conflict
   */
  val singleOrError: MergeStrategy = MergeStrategy("SingleOrError") { conflicts =>
    if (conflicts.size == 1) Right(conflicts.map(conflict => JarEntry(conflict.target, conflict.stream)))
    else
      Left(
        s"SingleOrError found multiple files with the same target path:$newLineIndented${conflicts.mkString(newLineIndented)}"
      )
  }

  /**
   * Concatenates the content of all conflicting files into one entry
   */
  val concat: MergeStrategy = concat(newLine)

  /**
   * Concatenates the content of all conflicting files into one entry
   * @param separator a custom content separator, defaulted to the System line separator
   */
  def concat(separator: String = newLine): MergeStrategy = MergeStrategy("Concat") { conflicts =>
    val streamsWithNewLine = conflicts.map(_.stream()).map(AppendEofInputStream(_, separator))
    val concatenatedStream = () => new SequenceInputStream(Collections.enumeration(streamsWithNewLine.asJava))
    Right(Vector(JarEntry(conflicts.head.target, concatenatedStream)))
  }

  /**
   * Discards all conflicts
   */
  val discard: MergeStrategy = MergeStrategy("Discard", 1)(_ => Right(Vector.empty))

  /**
   * Verifies if all the conflicts have the same content, otherwise error out
   */
  val deduplicate: MergeStrategy = MergeStrategy("Deduplicate") { conflicts =>
    val fingerprints = conflicts.map(dep =>
      sbtassembly.Assembly.sha1Content(dep.stream())
    ).toSet
    if (fingerprints.size == 1)
      Right(Vector(JarEntry(conflicts.head.target, conflicts.head.stream)))
    else
      Left(
        s"Deduplicate found different file contents in the following:$newLineIndented${conflicts.mkString(newLineIndented)}"
      )
  }

  /**
   * Renames matching dependencies, except *.class files (shading should be used instead)
   */
  val rename: MergeStrategy = MergeStrategy("Rename", 1) { conflicts =>
    val classes = conflicts.filter(_.target.endsWith(".class"))
    if (classes.nonEmpty)
      Left(
        s"Classes should not be renamed (use shade rules instead!):$newLineIndented${classes.mkString(newLineIndented)}"
      )
    else
      Right(conflicts.map {
        case Project(name, _, target, stream) => JarEntry(s"${target}_$name", stream)
        case Library(moduleCoord, _, target, stream) =>
          val jarName = Option(moduleCoord.version)
            .filter(_.nonEmpty)
            .map(version => s"${moduleCoord.name}-$version")
            .getOrElse(moduleCoord.name)
          val renamed = s"${FileExtension.replaceFirstIn(target, "")}_$jarName" +
            s"${FileExtension.findFirstIn(target).getOrElse("")}"
          JarEntry(renamed, stream)
      })
  }

  /**
   * Concatenates the content, but leaves out duplicates along the way
   */
  val filterDistinctLines: MergeStrategy = filterDistinctLines(IO.defaultCharset)

  /**
   * Concatenates the content, but leaves out duplicates along the way
   *
   * @param charset the charset to use for reading content liines
   */
  def filterDistinctLines(charset: Charset = IO.defaultCharset): MergeStrategy = MergeStrategy("FilterDistinctLines") {
    conflicts =>
      val uniqueLines = conflicts
        .flatMap { conflict =>
          dependencyStreamResource(conflict) { is =>
            IO.readLines(new BufferedReader(new InputStreamReader(is, charset)))
          }
        }
        .distinct
        .mkString(newLine)
      Right(Vector(JarEntry(conflicts.head.target, () => new ByteArrayInputStream(uniqueLines.getBytes(charset)))))
  }

  /**
   * The default merge strategy if not configured by the user
   */
  val defaultMergeStrategy: String => MergeStrategy = {
    case x if isConfigFile(x) =>
      MergeStrategy.concat
    case PathList(ps @ _*) if isReadme(ps.last) || isLicenseFile(ps.last) =>
      MergeStrategy.rename
    case PathList(ps @ _*) if isSystemJunkFile(ps.last) =>
      MergeStrategy.discard
    case PathList("META-INF", xs @ _*) =>
      xs map {
        _.toLowerCase
      } match {
        case x :: Nil if Seq("manifest.mf", "index.list", "dependencies") contains x =>
          MergeStrategy.discard
        case ps @ (_ :: _) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") || ps.last.endsWith(".rsa") =>
          MergeStrategy.discard
        case "maven" :: _ =>
          MergeStrategy.discard
        case "plexus" :: _ =>
          MergeStrategy.discard
        case "services" :: _ =>
          MergeStrategy.filterDistinctLines
        case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) | ("spring.tooling" :: Nil) =>
          MergeStrategy.filterDistinctLines
        case _ => MergeStrategy.deduplicate
      }
    case _ => MergeStrategy.deduplicate
  }

  private[sbtassembly] def apply(strategyName: String, notifyIfGTE: Int = 2)(
      f: Vector[Dependency] => Either[String, Vector[JarEntry]]
  ): MergeStrategy = new InternalMergeStrategy(strategyName, notifyIfGTE) {
    def apply(dependencies: Vector[Dependency]): Either[String, Vector[JarEntry]] = f(dependencies)
  }

  private[sbtassembly] def merge(
      strategy: MergeStrategy,
      dependencies: Vector[Dependency]
  ): Either[String, MergedEntry] =
    strategy(dependencies).right.map(entry => MergedEntry(entry, dependencies, strategy))
}

object CustomMergeStrategy {

  /**
   * Creates a custom [[MergeStrategy]]
   *
   * @param strategyName a descriptive and unique (within the build) merge strategy name
   * @param notifyIfGTE logs the summary if the number of dependencies merged is greater than or equal to this number.
   *                    If the merge strategy involves a transformation of a single file, it is recommended to set this
   *                    to `1` to see the results in the log. Defaults to `2`
   * @param f the actual logic for merging the dependencies
   * @return a [[MergeStrategy]]
   */
  def apply(strategyName: String, notifyIfGTE: Int = 2)(
      f: Vector[Dependency] => Either[String, Vector[JarEntry]]
  ): MergeStrategy = new CustomMergeStrategy(strategyName, notifyIfGTE) {
    def apply(dependencies: Vector[Dependency]): Either[String, Vector[JarEntry]] = f(dependencies)
  }

  /**
   * Creates a custom rename [[MergeStrategy]]. Custom renames will be processed first before all other merge strategies.
   *
   * Logs the summary if there is at least 1 dependency.
   *
   * @param f produces the new `target` path for the given `Dependency`
   * @return a [[MergeStrategy]]
   */
  def rename(f: Dependency => String): MergeStrategy =
    apply(MergeStrategy.rename.name, 1) { dependencies =>
      Right(dependencies.map(dep => JarEntry(f(dep), dep.stream)))
    }
}
