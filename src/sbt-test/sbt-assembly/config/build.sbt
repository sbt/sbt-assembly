version in ThisBuild := "0.1"
scalaVersion in ThisBuild := "2.11.12"

lazy val root = (project in file("."))
  .settings(inConfig(Test)(baseAssemblySettings))
  .settings(
    Test / assembly / assemblyJarName := "foo.jar",
    TaskKey[Unit]("check") := {
      val process = sys.process.Process("java", Seq("-jar", (crossTarget.value / "foo.jar").toString))
      val out = (process!!)
      if (out.trim != "hellospec") sys.error("unexpected output: " + out)
      ()
    }
  )
