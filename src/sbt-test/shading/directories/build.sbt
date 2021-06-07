scalaVersion in ThisBuild := "2.10.7"

assemblyShadeRules in ThisBuild := Seq(
  ShadeRule.rename("somepackage.**" -> "shaded.@1").inAll
)

lazy val root = (project in file("."))
  .settings(
    crossPaths := false,

    assemblyJarName in assembly := "assembly.jar",

    TaskKey[Unit]("check") := {
      val expected = "Hello shaded.SomeClass"
      val output = sys.process.Process("java", Seq("-jar", assembly.value.absString)).!!.trim
      if (output != expected) sys.error("Unexpected output: " + output)
    },

    TaskKey[Unit]("unzip") := {
      IO.unzip((assemblyOutputPath in assembly).value, crossTarget.value / "unzipped")
    }
  )
