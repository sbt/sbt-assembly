package sbtassembly

import sbt._

object AssemblyUtils {
  private val PathRE = "([^/]+)/(.*)".r

  /** Find the source file (and possibly the entry within a jar) whence a conflicting file came.
   * 
   * @param tempDir The temporary directory provided to a `MergeStrategy`
   * @param f One of the files provided to a `MergeStrategy`
   * @return The source file and, if the source file is a jar, the path within that jar.
   */
  def sourceOfFileForMerge(tempDir: File, f: File): (File, Option[String]) = {
    val baseURI = tempDir.getCanonicalFile.toURI
    val otherURI = f.getCanonicalFile.toURI
    baseURI.relativize(otherURI) match {
      case x if x.isAbsolute =>
        (f, None)
      case relative =>
        val PathRE(head, tail) = relative.getPath
        val jarName = IO.read(tempDir / (head + ".jarName"), IO.utf8)
        (new File(jarName), Some(tail))
    }
  }
}
