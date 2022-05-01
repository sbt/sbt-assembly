ThisBuild / assemblyMergeStrategy := {
  case "a" | "c" | "d" => // this should be ignored in the second pass, so "e" renamed to "a" will not be renamed to "b" in the second pass
    CustomMergeStrategy.rename( _ => "b" )

  case "b" => MergeStrategy.concat // should concatenate all the files renamed to "b" -> "a", "b", "c", and "d"
  case "e" => // this should be renamed to a in the first pass
    CustomMergeStrategy.rename( _ => "a" )
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

lazy val testmerge = (project in file(".")).settings(
  version := "0.1",
  assembly / assemblyJarName := "foo.jar",
  TaskKey[Unit]("check") := {
    IO.withTemporaryDirectory { dir ⇒
      IO.unzip(crossTarget.value / "foo.jar", dir)
      mustContain(dir / "b", Seq("a", "b", "c", "d"))
      mustContain(dir / "a", Seq("e"))
    }
  }
)

def mustContain(f: File, l: Seq[String]): Unit = {
  val lines = IO.readLines(f, IO.utf8)
  if (lines.sorted != l.sorted)
    throw new Exception(
      "file " + f + " had wrong content:\n" + lines.mkString("\n") +
        "\n*** instead of ***\n" + l.mkString("\n")
    )
}

