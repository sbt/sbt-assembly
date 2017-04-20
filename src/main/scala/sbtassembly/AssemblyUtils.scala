package sbtassembly

import sbt._
import sbt.io.Using._
import java.io.{File, InputStream}
import java.util.zip.ZipInputStream
import scala.collection.mutable.HashSet
import ErrorHandling.translate

object AssemblyUtils {
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
    def next()
    {
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
            if(entry.isDirectory)
              IO.createDirectory(target)
            else
            {
              set += target
              translate("Error extracting zip entry '" + name + "' to '" + target + "': ") {
                fileOutputStream(false)(target) { out => IO.transfer(from, out) }
              }
            }
            if(preserveLastModified)
              target.setLastModified(entry.getTime)
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
}
