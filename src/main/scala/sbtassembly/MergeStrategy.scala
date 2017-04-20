package sbtassembly

import sbt._
import Keys._
import java.io.{ FileOutputStream, File}
import Assembly.{ sha1string, sha1content }

/**
 * MergeStrategy is invoked if more than one source file is mapped to the 
 * same target path. Its arguments are the tempDir (which is deleted after
 * packaging) and the sequence of source files, and it shall return the
 * file to be included in the assembly (or throw an exception).
 */
abstract class MergeStrategy extends Function1[(File, String, Seq[File]), Either[String, Seq[(File, String)]]] {
  def name: String
  def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]]
  def notifyThreshold = 2
  def detailLogLevel = Level.Warn
  def summaryLogLevel = Level.Warn
  final def apply(args: (File, String, Seq[File])): Either[String, Seq[(File, String)]] =
    apply(args._1, args._2, args._3)
}

object MergeStrategy {
  private val FileExtension = """([.]\w+)$""".r
  private def filenames(tempDir: File, fs: Seq[File]): Seq[String] =
    for(f <- fs) yield {
      AssemblyUtils.sourceOfFileForMerge(tempDir, f) match {
        case (path, base, subDirPath, false) => subDirPath
        case (jar, base, subJarPath, true) => jar + ":" + subJarPath
      }
    }


  @inline def createMergeTarget(tempDir: File, path: String): File = {
    val file = new File(tempDir, "sbtMergeTarget-" + sha1string(path) + ".tmp")
    if (file.exists) {
      IO.delete(file)
    }
    file
  }
  val first: MergeStrategy = new MergeStrategy {
    val name = "first"
    def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
      Right(Seq(files.head -> path))
  }
  val last: MergeStrategy = new MergeStrategy {
    val name = "last"
    def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
      Right(Seq(files.last -> path))
  }
  val singleOrError: MergeStrategy = new MergeStrategy {
    val name = "singleOrError"
    def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
      if (files.size == 1) Right(Seq(files.head -> path))
      else Left("found multiple files for same target path:" +
        filenames(tempDir, files).mkString("\n", "\n", ""))
  }
  val concat: MergeStrategy = new MergeStrategy {
    val name = "concat"
    def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
      val file = createMergeTarget(tempDir, path)
      val out = new FileOutputStream(file)
      try {
        files foreach {f =>
          IO.transfer(f, out)
          if (!IO.read(f).endsWith(IO.Newline)) out.write(IO.Newline.getBytes(IO.defaultCharset))
        }
        Right(Seq(file -> path))
      } finally {
        out.close()
      }
    }
  }
  val filterDistinctLines: MergeStrategy = new MergeStrategy {
    val name = "filterDistinctLines"
    def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
      val lines = files flatMap (IO.readLines(_, IO.utf8))
      val unique = (Vector.empty[String] /: lines)((v, l) => if (v contains l) v else v :+ l)
      val file = createMergeTarget(tempDir, path)
      IO.writeLines(file, unique, IO.utf8)
      Right(Seq(file -> path))
    }
  }
  val deduplicate: MergeStrategy = new MergeStrategy {
    val name = "deduplicate"
    def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
      if (files.size == 1) Right(Seq(files.head -> path))
      else {
        val fingerprints = Set() ++ (files map (sha1content))
        if (fingerprints.size == 1) Right(Seq(files.head -> path))
        else Left("different file contents found in the following:" +
            filenames(tempDir, files).mkString("\n", "\n", ""))
      }
    override def detailLogLevel = Level.Debug
    override def summaryLogLevel = Level.Info
  }
  val rename: MergeStrategy = new MergeStrategy {
    val name = "rename"
    def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
      Right(files flatMap { f =>
        if(!f.exists) Seq.empty
        else if(f.isDirectory && (f ** "*.class").get.nonEmpty) Seq(f -> path)
        else AssemblyUtils.sourceOfFileForMerge(tempDir, f) match {
          case (_, _, _, false) => Seq(f -> path)
          case (jar, base, p, true) =>
            val dest = new File(f.getParent, appendJarName(f.getName, jar))
            IO.move(f, dest)
            val result = Seq(dest -> appendJarName(path, jar))
            if (dest.isDirectory) ((dest ** (-DirectoryFilter))) pair Path.relativeTo(base)
            else result
        }
      })

    def appendJarName(source: String, jar: File): String =
      FileExtension.replaceFirstIn(source, "") +
        "_" + FileExtension.replaceFirstIn(jar.getName, "") +
        FileExtension.findFirstIn(source).getOrElse("")

    override def notifyThreshold = 1
  }
  val discard: MergeStrategy = new MergeStrategy {
    val name = "discard"
    def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
      Right(Nil)   
    override def notifyThreshold = 1
  }

  val defaultMergeStrategy: String => MergeStrategy = {
    case x if Assembly.isConfigFile(x) =>
      MergeStrategy.concat
    case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
      MergeStrategy.rename
    case PathList(ps @ _*) if Assembly.isSystemJunkFile(ps.last) =>
      MergeStrategy.discard
    case PathList("META-INF", xs @ _*) =>
      (xs map {_.toLowerCase}) match {
        case (x :: Nil) if Seq("manifest.mf", "index.list", "dependencies") contains x =>
          MergeStrategy.discard
        case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") || ps.last.endsWith(".rsa") =>
          MergeStrategy.discard
        case "maven" :: xs =>
          MergeStrategy.discard
        case "plexus" :: xs =>
          MergeStrategy.discard
        case "services" :: xs =>
          MergeStrategy.filterDistinctLines
        case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) | ("spring.tooling" :: Nil) =>
          MergeStrategy.filterDistinctLines
        case _ => MergeStrategy.deduplicate
      }
    case _ => MergeStrategy.deduplicate
  }
}
