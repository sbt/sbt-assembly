name := "foo"
version := "0.1"
scalaVersion := "2.12.18"
libraryDependencies += "commons-io" % "commons-io" % "2.4"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "0.9.29"
assembly / assemblyJarName := "foo.jar"

TaskKey[Unit]("keepOneJarOpen") := {
  System.setProperty("sbtassembly.maxOpenJars", "1")
  ()
}

TaskKey[Unit]("check") := {
  IO.withTemporaryDirectory { dir =>
    IO.unzip(crossTarget.value / "foo.jar", dir)
    mustExist(dir / "Main.class")
    mustExist(dir / "scala" / "Predef.class")
    mustExist(dir / "org" / "apache" / "commons" / "io" / "IOUtils.class")
    mustExist(dir / "org" / "slf4j" / "Logger.class")
    mustExist(dir / "ch" / "qos" / "logback" / "core" / "Context.class")
    mustExist(dir / "ch" / "qos" / "logback" / "classic" / "Logger.class")
  }
  val process = sys.process.Process("java", Seq("-jar", (crossTarget.value / "foo.jar").toString))
  val out = process.!!
  if (out.trim != "hello") sys.error("unexpected output: " + out)
  ()
}

def mustExist(f: File): Unit = {
  if (!f.exists) sys.error("file " + f + " does not exist!")
}
