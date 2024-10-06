version := "0.1"
scalaVersion := "2.12.18"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "0.9.29" % "runtime",
    Compile / unmanagedJars ++= {
      val conv = fileConverter.value
      implicit val c: xsbti.FileConverter = conv
      (baseDirectory.value / "lib" / "compile" ** "*.jar").classpath
    },
    Runtime / unmanagedJars ++= {
      val conv = fileConverter.value
      implicit val c: xsbti.FileConverter = conv
      (baseDirectory.value / "lib" / "runtime" ** "*.jar").classpath
    },
    Test / unmanagedJars ++= {
      val conv = fileConverter.value
      implicit val c: xsbti.FileConverter = conv
      (baseDirectory.value / "lib" / "test" ** "*.jar").classpath
    },
    assemblyExcludedJars := {
      (assembly / fullClasspath).value.filter { _.data.toString().endsWith("compile-0.1.0.jar") }
    },
    assembly / assemblyJarName := "foo.jar",
    TaskKey[Unit]("check") := {
      val process = sys.process.Process("java", Seq("-jar", (crossTarget.value / "foo.jar").toString))
      val out = process.!!
      if (out.trim != "hello") sys.error("unexpected output: " + out)
      ()
    }
  )

TaskKey[Unit]("fileCheck") := {
  assert((crossTarget.value / "foo.jar").exists())
}
