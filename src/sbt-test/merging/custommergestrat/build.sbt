ThisBuild / assemblyMergeStrategy := {
  case "sbtassembly" => CustomMergeStrategy.rename(_ => "some-other-target")
  case x   =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

lazy val testmerge = (project in file(".")).
  settings(
    version := "0.1",
    assembly / assemblyJarName := "foo.jar",
    TaskKey[Unit]("check") := {
      IO.withTemporaryDirectory { dir ⇒
        IO.unzip(crossTarget.value / "foo.jar", dir)
        mustContain(dir / "some-other-target", Seq("I should exist"))
        mustContain(dir / "sbtassembly" / "file.txt", Seq("test file"))
      }
    }
  )

def mustContain(f: File, l: Seq[String]): Unit = {
  val lines = IO.readLines(f, IO.utf8)
  if (lines != l)
    throw new Exception("file " + f + " had wrong content:\n" + lines.mkString("\n") +
      "\n*** instead of ***\n" + l.mkString("\n"))
}
