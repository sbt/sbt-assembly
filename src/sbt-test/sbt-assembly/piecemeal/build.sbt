version := "0.1"
scalaVersion := "2.12.18"
assemblyPackageScala / assembleArtifact := false
assemblyPackageDependency / assembleArtifact := false

lazy val root = (project in file("."))
  .settings(
    name := "foo",
    assembly / mainClass := Some("Main"),

    // assembly / assemblyOption ~= {
    //   _.withIncludeScala(false)
    //    .withIncludeDependency(false)
    // },

    TaskKey[Unit]("check1") := {
      val process = sys.process.Process("java", Seq("-cp",
        (crossTarget.value / "foo-assembly-0.1.jar").toString,
        "Main"))
      val out = process.!!
      if (out.trim != "hello") sys.error("unexpected output: " + out)
      ()
    },
    TaskKey[Unit]("check2") := {
      val process = sys.process.Process("java", Seq("-cp",
        (crossTarget.value / "scala-library-2.12.18-assembly.jar").toString +
        (if (scala.util.Properties.isWin) ";" else ":") +
        (crossTarget.value / "foo-assembly-0.1.jar").toString,
        "Main"))
      val out = process.!!
      if (out.trim != "hello") sys.error("unexpected output: " + out)
      ()
    }
  )

TaskKey[Unit]("fileCheck1") := {
  assert((crossTarget.value / "foo-assembly-0.1.jar").exists())
}

TaskKey[Unit]("fileCheck2") := {
  assert((crossTarget.value / "foo-assembly-0.1-deps.jar").exists())
}

TaskKey[Unit]("fileCheck3") := {
  assert((crossTarget.value / "scala-library-2.12.18-assembly.jar").exists())
}
