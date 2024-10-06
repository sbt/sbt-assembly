scalaVersion := "2.12.18"
assemblyShadeRules := Seq(
  ShadeRule.rename("somepackage.**" -> "shaded.@1").inAll
)
exportJars := false

lazy val root = (project in file("."))
  .settings(
    assembly / assemblyJarName := "assembly.jar",
    TaskKey[Unit]("unzip") := {
      IO.unzip((assembly / assemblyOutputPath).value, crossTarget.value / "unzipped")
    }
  )

TaskKey[Unit]("check") := {
  val expected = "Hello shaded.SomeClass"
  val output = sys.process.Process(
    "java",
    Seq("-jar", (crossTarget.value / "assembly.jar").toString)
  ).!!.trim
  if (output != expected) sys.error("Unexpected output: " + output)
}
