package sbtassembly

import sbt._

import java.io.{File, InputStream}
import java.util.zip.ZipInputStream
import scala.collection.mutable.HashSet
import ErrorHandling.translate
import PluginCompat._
import Using._

import java.nio.file.{FileSystemException, Files}
import scala.Function.tupled

private[sbtassembly] object AssemblyUtils {
  private val PathRE = "([^/]+)/(.*)".r

  /** Find the source file (and possibly the entry within a jar) whence a conflicting file came.
   *
   * @param tempDir The temporary directory provided to a `MergeStrategy`
   * @param f One of the files provided to a `MergeStrategy`
   * @return The source jar or dir; the path within that dir; and true if it's from a jar.
   */
  def sourceOfFileForMerge(tempDir: File, f: File): (File, File, String, Boolean) = {
    val baseURI = tempDir.getCanonicalFile.toURI
    val otherURI = f.getCanonicalFile.toURI
    val relative = baseURI.relativize(otherURI)
    val PathRE(head, tail) = relative.getPath
    val base = tempDir / head

    if ((tempDir / (head + ".jarName")) exists) {
      val jarName = IO.read(tempDir / (head + ".jarName"), IO.utf8)
      (new File(jarName), base, tail, true)
    } else {
      val dirName = IO.read(tempDir / (head + ".dir"), IO.utf8)
      (new File(dirName), base, tail, false)
    } // if-else
  }

  // working around https://github.com/sbt/sbt-assembly/issues/90
  def unzip(from: File, toDirectory: File, log: Logger, filter: NameFilter = AllPassFilter, preserveLastModified: Boolean = true): Set[File] =
    fileInputStream(from)(in => unzipStream(in, toDirectory, log, filter, preserveLastModified))
  def unzipURL(from: URL, toDirectory: File, log: Logger, filter: NameFilter = AllPassFilter, preserveLastModified: Boolean = true): Set[File] =
    urlInputStream(from)(in => unzipStream(in, toDirectory, log, filter, preserveLastModified))
  def unzipStream(from: InputStream, toDirectory: File, log: Logger, filter: NameFilter = AllPassFilter, preserveLastModified: Boolean = true): Set[File] =
  {
    IO.createDirectory(toDirectory)
    zipInputStream(from) { zipInput => extract(zipInput, toDirectory, log, filter, preserveLastModified) }
  }
  private def extract(from: ZipInputStream, toDirectory: File, log: Logger, filter: NameFilter, preserveLastModified: Boolean) =
  {
    val set = new HashSet[File]
    def next(): Unit = {
      val entry = from.getNextEntry
      if(entry == null)
        ()
      else
      {
        val name = entry.getName
        if(filter.accept(name))
        {
          val target = new File(toDirectory, name)
          //log.debug("Extracting zip entry '" + name + "' to '" + target + "'")

          try {
            if (entry.isDirectory)
              IO.createDirectory(target)
            else
            {
              set += target
              translate("Error extracting zip entry '" + name + "' to '" + target + "': ") {
                fileOutputStream(false)(target) { out => IO.transfer(from, out) }
              }
            }

            if (preserveLastModified) {
              val t: Long = entry.getTime
              val EPOCH_1980_01_01 = 315532800000L
              val EPOCH_2010_01_01 = 1262304000000L
              if (t > EPOCH_1980_01_01) target.setLastModified(t)
              else target.setLastModified(EPOCH_2010_01_01)
            }
          } catch {
            case e: Throwable => log.warn(e.getMessage)
          }
        }
        else
        {
          //log.debug("Ignoring zip entry '" + name + "'")
        }
        from.closeEntry()
        next()
      }
    }
    next()
    Set() ++ set
  }

  def getMappings(rootDir : File, excluded: Set[File]): Vector[(File, String)] =
    if(!rootDir.exists) Vector()
    else {
      val sysFileSep = System.getProperty("file.separator")
      def loop(dir: File, prefix: String, acc: Seq[(File, String)]): Seq[(File, String)] = {
        val children = (dir * new SimpleFileFilter(f => !excluded(f))).get
        children.flatMap { f =>
          val rel = (if(prefix.isEmpty) "" else prefix + sysFileSep) + f.getName
          val pairAcc = (f -> rel) +: acc
          if(f.isDirectory) loop(f, rel, pairAcc) else pairAcc
        }
      }
      loop(rootDir, "", Nil).toVector
    }


  def isHardLinkSupported(sourceDir: File, destDir: File): Boolean = {
    assert(sourceDir.isDirectory)
    assert(destDir.isDirectory)

    withTemporaryFileInDirectory("sbt-assembly", "file", sourceDir) { sourceFile =>
      try {
        val destFile = destDir / sourceFile.getName
        Files.createLink(destFile.toPath, sourceFile.toPath)
        IO.delete(destFile)
        true
      } catch {
        case ex: FileSystemException if ex.getMessage().contains("Invalid cross-device link") => false
      }
    }
  }

  def withTemporaryFileInDirectory[T](prefix: String, postfix: String, dir: File)(
    action: File => T
  ): T = {
    assert(dir.isDirectory)
    val file = File.createTempFile(prefix, postfix, dir)
    try { action(file) } finally { file.delete(); () }
  }

  // region copyDirectory

  /** This is an experimental port of https://github.com/sbt/io/pull/326 */

  def copyDirectory(
    source: File,
    target: File,
    overwrite: Boolean = false,
    preserveLastModified: Boolean = false,
    preserveExecutable: Boolean = true,
    hardLink: Boolean = false
  ): Unit = {
    val sources = PathFinder(source).allPaths pair Path.rebase(source, target)
    copy(sources, overwrite, preserveLastModified, preserveExecutable, hardLink)
    ()
  }

  def copy(
    sources: Traversable[(File, File)],
    overwrite: Boolean,
    preserveLastModified: Boolean,
    preserveExecutable: Boolean,
    hardLink: Boolean
  ): Set[File] =
    sources
      .map(tupled(copyImpl(overwrite, preserveLastModified, preserveExecutable, hardLink)))
      .toSet

  private def copyImpl(
    overwrite: Boolean,
    preserveLastModified: Boolean,
    preserveExecutable: Boolean,
    hardLink: Boolean
  )(from: File, to: File): File = {
    if (overwrite || !to.exists || IO.getModifiedTimeOrZero(from) > IO.getModifiedTimeOrZero(to)) {
      if (from.isDirectory) {
        IO.createDirectory(to)
      } else {
        IO.createDirectory(to.getParentFile)
        copyFile(from, to, preserveLastModified, preserveExecutable, hardLink)
      }
    }
    to
  }

  def copyFile(
    sourceFile: File,
    targetFile: File,
    preserveLastModified: Boolean,
    preserveExecutable: Boolean,
    hardLink: Boolean
  ): Unit = {
    // NOTE: when modifying this code, test with larger values of CopySpec.MaxFileSizeBits than default

    require(sourceFile.exists, "Source file '" + sourceFile.getAbsolutePath + "' does not exist.")
    require(
      !sourceFile.isDirectory,
      "Source file '" + sourceFile.getAbsolutePath + "' is a directory."
    )
    if (hardLink) {
      if (targetFile.exists) targetFile.delete()
      Files.createLink(targetFile.toPath, sourceFile.toPath)
      ()
    } else {
      fileInputChannel(sourceFile) { in =>
        fileOutputChannel(targetFile) { out =>
          // maximum bytes per transfer according to  from http://dzone.com/snippets/java-filecopy-using-nio
          val max = (64L * 1024 * 1024) - (32 * 1024)
          val total = in.size
          def loop(offset: Long): Long =
            if (offset < total)
              loop(offset + out.transferFrom(in, offset, max))
            else
              offset
          val copied = loop(0)
          if (copied != in.size)
            sys.error(
              "Could not copy '" + sourceFile + "' to '" + targetFile + "' (" + copied + "/" + in.size + " bytes copied)"
            )
        }
      }
      if (preserveLastModified) {
        IO.copyLastModified(sourceFile, targetFile)
        ()
      }
      if (preserveExecutable) {
        IO.copyExecutable(sourceFile, targetFile)
        ()
      }
    }
  }

  // endregion
}
