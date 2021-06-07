version in ThisBuild := "0.1"
scalaVersion in ThisBuild := "2.12.8"

lazy val root = (project in file("."))
  .settings(
    assemblyJarName := "foo.jar",
    TaskKey[Unit]("check") := {
      val process = sys.process.Process("java", Seq("-jar", (crossTarget.value / "foo.jar").toString))
      val out = (process!!)
      if (out.trim != "hello") sys.error("unexpected output: " + out)
      ()
    }
  )
