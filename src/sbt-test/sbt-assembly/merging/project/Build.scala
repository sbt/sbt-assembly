package test

import sbt._
import Keys._
import sbtassembly.Plugin._
import AssemblyKeys._

object B extends Build {

  lazy val project = Project("testmerge", file("."),
    settings = Defaults.defaultSettings ++ assemblySettings ++ Seq(
      version := "0.1",
      jarName in assembly := "foo.jar",
      mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) ⇒
        {
          case "a" ⇒ MergeStrategy.concat
          case "b" ⇒ MergeStrategy.first
          case "c" ⇒ MergeStrategy.last
          case "d" ⇒ MergeStrategy.filterDistinctLines
          case "e" ⇒ MergeStrategy.deduplicate
          case "f" ⇒ MergeStrategy.discard
          case x   ⇒ old(x)
        }
      },
      TaskKey[Unit]("check") <<= (target) map { (target) ⇒
        IO.withTemporaryDirectory { dir ⇒
          IO.unzip(target / "foo.jar", dir)
          mustContain(dir / "a", Seq("1", "2", "1", "3"))
          mustContain(dir / "b", Seq("1"))
          mustContain(dir / "c", Seq("1", "3"))
          mustContain(dir / "d", Seq("1", "2", "3"))
          mustContain(dir / "e", Seq("1"))
          mustNotExist(dir / "f")
          mustContain(dir / "README", Seq("resources"))
          mustContain(dir / "README_1", Seq("1"))
          mustContain(dir / "LICENSE", Seq("resources"))
          mustContain(dir / "LICENSE_1" / "a", Seq("1"))
          // 80f5a06 -- don't rename License.class
          mustExist(dir / "com" / "example" / "License.class")
          // 7ce9509 -- rename things inside META-INF
          mustContain(dir / "META-INF" / "NOTICE_2.txt", Seq("This is the notice for 2"))
          mustContain(dir / "META-INF" / "NOTICE_3.txt", Seq("This is the notice for 3"))
          // 032b3a2 -- Don't rename the directories if they contain
          // classes; if they don't, don't rename the contents.
          mustExist(dir / "com" / "example" / "license" / "PublicDomain.class")
          mustExist(dir / "NOTICE_3" / "README.txt")
          mustExist(dir / "NOTICE_3" / "LICENSE.txt")
        }
      }))

  private def mustContain(f: File, l: Seq[String]) {
    val lines = IO.readLines(f, IO.utf8)
    if (lines != l)
      throw new Exception("file " + f + " had wrong content:\n" + lines.mkString("\n") +
        "\n*** instead of ***\n" + l.mkString("\n"))
  }
  private def mustNotExist(f: File) {
    if (f.exists) sys.error("file" + f + " exists!")
  }
  private def mustExist(f: File) {
    if (!f.exists) sys.error("file" + f + " does not exist!")
  }
}
