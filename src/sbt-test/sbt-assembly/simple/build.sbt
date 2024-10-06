name := "foo"
version := "0.1"
scalaVersion := "2.12.18"
assemblyJarName := "foo.jar"
TaskKey[Unit]("check") := {
  val process = sys.process.Process("java", Seq("-jar", (crossTarget.value / "foo.jar").toString))
  val out = process.!!
  if (out.trim != "hello") sys.error("unexpected output: " + out)
  ()
}

TaskKey[Unit]("fileCheck") := {
  assert((crossTarget.value / "foo.jar").exists())
}
