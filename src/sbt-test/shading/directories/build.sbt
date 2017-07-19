scalaVersion := "2.10.6"

crossPaths := false

assemblyJarName in assembly := "assembly.jar"

assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("somepackage.**" -> "shaded.@1").inAll
)

TaskKey[Unit]("check") := {
  val expected = "Hello shaded.SomeClass"
  val output = Process("java", Seq("-jar", assembly.value.absString)).!!.trim
  if (output != expected) sys.error("Unexpected output: " + output)
}

TaskKey[Unit]("unzip") := {
  IO.unzip((assemblyOutputPath in assembly).value, crossTarget.value / "unzipped")
}
