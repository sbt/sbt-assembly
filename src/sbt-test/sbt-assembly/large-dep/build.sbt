import sbtassembly.AssemblyPlugin.autoImport._


lazy val root = (project in file(".")).
  settings(
    version := "0.1.0",
    scalaVersion := "2.12.18",
    libraryDependencies ++= Seq(
      "org.apache.calcite" % "calcite-core" % "1.36.0"
    ),
    assembly / assemblyShadeRules := Seq(
      // shade guava for calcite
      ShadeRule.rename("com.google.guava.**" -> s"new_guava.com.google.guava.@1").inAll,
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
