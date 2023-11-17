lazy val root = (project in file(".")).
  settings(
    version := "0.1.0",
    scalaVersion := "2.12.18",
    libraryDependencies ++= Seq(
      "org.apache.cassandra" % "cassandra-all" % "4.0.0"
    ),
    assembly / mainClass := Some("foo.Hello"),
    assembly / assemblyJarName := "foo.jar",
    TaskKey[Unit]("check") := {
      val outputJar = (crossTarget.value / "foo.jar").toString

      val process = sys.process.Process("java", Seq("-jar", outputJar))
      val out = (process!!)
      if (out.trim != "hello") sys.error("unexpected output: " + out)


      val process2 = sys.process.Process("ls", Seq("-lh", outputJar))
      println(process2!!)

      ()
    }
  )
