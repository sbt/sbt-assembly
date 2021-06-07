version in ThisBuild := "0.1"
scalaVersion in ThisBuild := "2.11.12"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "0.9.29" % "runtime",
    unmanagedJars in Compile ++= {
       (baseDirectory.value / "lib" / "compile" ** "*.jar").classpath
    },
    unmanagedJars in Runtime ++= {
       (baseDirectory.value / "lib" / "runtime" ** "*.jar").classpath
    },
    unmanagedJars in Test ++= {
       (baseDirectory.value / "lib" / "test" ** "*.jar").classpath
    },
    assemblyExcludedJars := {
      (fullClasspath in assembly).value filter {_.data.getName == "compile-0.1.0.jar"}
    },
    assemblyJarName in assembly := "foo.jar",
    TaskKey[Unit]("check") := {
      val process = sys.process.Process("java", Seq("-jar", (crossTarget.value / "foo.jar").toString))
      val out = (process!!)
      if (out.trim != "hello") sys.error("unexpected output: " + out)
      ()
    }
  )
