package sbtassembly

import sbt.io.{ IO, Using }
import sbt.util.Level
import sbtassembly.Assembly._
import sbtassembly.AssemblyUtils.AppendEofInputStream

import java.io.{ BufferedReader, ByteArrayInputStream, InputStreamReader, SequenceInputStream }
import java.nio.charset.Charset
import java.nio.file.Paths
import java.util.Collections
import scala.collection.JavaConverters._
import scala.reflect.io.Streamable

/**
 * Class for merging files that shares the same target path
 */
trait MergeStrategy {

  /**
   * A descriptive name of the merge strategy which will be used in logging the merge results
   *
   * @return
   */
  def name: String

  /**
   * Report the merge counts only if the number of files to be merged are >= this threshold
   *
   * @return
   */
  def notifyThreshold = 2

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
   * Merges the 'conflicts' - files that are supposed to be written on the same target path on the jar, or files
   *  that match a specific [[PathList]] rule
   *
   * @param conflicts files that need to be merged due to having the same target path on the jar or matching
   *                  a specific [[PathList]] rule
   * @return an Either.Right of a JarEntry for a successful merge or an Either.Left of a JarEntry for a failed merge
   */
  def merge(conflicts: Vector[Dependency]): Either[String, Vector[JarEntry]]
}

object MergeStrategy {
  type StringToMergeStrategy = String => MergeStrategy
  private val FileExtension = """([.]\w+)$""".r
  private val dependencyStreamResource = Using.resource((dependency: Dependency) => dependency.stream())

  private[sbtassembly] def merge(
      strategy: MergeStrategy,
      conflicts: Vector[Dependency]
  ): Either[String, MergedEntry] =
    strategy
      .merge(conflicts)
      .right
      .map(entry => MergedEntry(entry, conflicts, strategy))

  /**
   * Picks the first of the conflicting files in classpath order
   */
  val first: MergeStrategy = new MergeStrategy {
    val name: String = "First"

    override def merge(conflicts: Vector[Dependency]): Either[String, Vector[JarEntry]] =
      Right(Vector(JarEntry(conflicts.head.target, conflicts.head.stream)))
  }

  /**
   * Picks the first of the conflicting files in classpath order
   */
  val last: MergeStrategy = new MergeStrategy {
    val name: String = "Last"

    override def merge(conflicts: Vector[Dependency]): Either[String, Vector[JarEntry]] =
      Right(Vector(JarEntry(conflicts.last.target, conflicts.last.stream)))
  }

  /**
   * Picks the dependency that is the first project/internal dependency, otherwise picks the first library/external dependency
   */
  val favorProjectThenFirstLibrary: MergeStrategy = new MergeStrategy {
    val name: String = "FavorProjectThenFirstExternal"

    override def merge(conflicts: Vector[Dependency]): Either[String, Vector[JarEntry]] = {
      val entry = conflicts.find(_.isProjectDependency).getOrElse(conflicts.head)
      Right(Vector(JarEntry(entry.target, entry.stream)))
    }
  }

  /**
   * Fails the merge on conflict
   */
  val singleOrError: MergeStrategy = new MergeStrategy {
    val name: String = "SingleOrError"

    override def merge(conflicts: Vector[Dependency]): Either[String, Vector[JarEntry]] =
      if (conflicts.size == 1) Right(conflicts.map(conflict => JarEntry(conflict.target, conflict.stream)))
      else
        Left(
          s"$name found multiple files with the same target path:$newLineIndented${conflicts.mkString(newLineIndented)}"
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
  def concat(separator: String = newLine): MergeStrategy = new MergeStrategy {
    val name: String = "Concat"

    override def merge(conflicts: Vector[Dependency]): Either[String, Vector[JarEntry]] = {
      val streamsWithNewLine = conflicts.map(_.stream()).map(AppendEofInputStream(_, separator))
      val concatenatedStream = () => new SequenceInputStream(Collections.enumeration(streamsWithNewLine.asJava))
      Right(Vector(JarEntry(conflicts.head.target, concatenatedStream)))
    }
  }

  /**
   * Discards all conflicts
   */
  val discard: MergeStrategy = new MergeStrategy {
    val name: String = "Discard"

    override def merge(conflicts: Vector[Dependency]): Either[String, Vector[JarEntry]] = Right(Vector.empty)

    override val notifyThreshold = 1
  }

  /**
   * Verifies if all the conflicts have the same content, otherwise error out
   */
  val deduplicate: MergeStrategy = new MergeStrategy {
    val name: String = "Deduplicate"

    override def merge(conflicts: Vector[Dependency]): Either[String, Vector[JarEntry]] = {
      val conflictContents = conflicts.map(_.stream()).map(Streamable.bytes(_))
      val fingerprints = Set() ++ conflictContents.map(sbtassembly.Assembly.sha1Content)
      if (fingerprints.size == 1)
        Right(Vector(JarEntry(conflicts.head.target, () => new ByteArrayInputStream(conflictContents.head))))
      else
        Left(
          s"$name found different file contents in the following:$newLineIndented${conflicts.mkString(newLineIndented)}"
        )
    }
  }

  /**
   * Renames matching files
   */
  val rename: MergeStrategy = new MergeStrategy {
    val name: String = "Rename"

    override def merge(conflicts: Vector[Dependency]): Either[String, Vector[JarEntry]] =
      Right(
        conflicts
          .map {
            case Project(name, _, target, stream) => JarEntry(Paths.get(s"${target.toString}_$name"), stream)
            case Library(_, _, target, stream) if target.endsWith(".class") =>
              JarEntry(target, stream)
            case Library(moduleCoord, _, target, stream) =>
              val jarName = Option(moduleCoord.version)
                .filter(_.nonEmpty)
                .map(version => s"${moduleCoord.name}-$version")
                .getOrElse(moduleCoord.name)
              val renamed = s"${FileExtension.replaceFirstIn(target.toString, "")}_$jarName" +
                s"${FileExtension.findFirstIn(target.toString).getOrElse("")}"
              JarEntry(Paths.get(renamed), stream)
          }
      )

    override val notifyThreshold = 1
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
  def filterDistinctLines(charset: Charset = IO.defaultCharset): MergeStrategy = new MergeStrategy {
    val name: String = "FilterDistinctLines"

    override def merge(conflicts: Vector[Dependency]): Either[String, Vector[JarEntry]] = {
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
}
