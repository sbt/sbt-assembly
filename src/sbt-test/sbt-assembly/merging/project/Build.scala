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
      // make sure jars appear in a reproducible order and not just in the
      // the non-deterministic order the file-system lists them
      unmanagedJars in Compile ~= (_.sortBy(_.data.getName)),
      mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) ⇒
        {
          case "a" ⇒ MergeStrategy.concat
          case "b" ⇒ MergeStrategy.first
          case "c" ⇒ MergeStrategy.last
          case "d" ⇒ MergeStrategy.filterDistinctLines
          case "f" ⇒ MergeStrategy.concatWithCaption
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
          mustContain(dir / "f", Seq("1.jar:f", "", "1", "", "2.jar:f", "", "2", "", "3.jar:f", "", "1", "3", ""))
        }
      }))

  private def mustContain(f: File, l: Seq[String]) {
    val lines = IO.readLines(f, IO.utf8)
    if (lines != l)
      throw new Exception("file " + f + " had wrong content:\n" + lines.mkString("\n") +
        "\n*** instead of ***\n" + l.mkString("\n"))
  }
}
