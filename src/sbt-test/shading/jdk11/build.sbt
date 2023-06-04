scalaVersion := scala212

lazy val scala212 = "2.12.18"
lazy val scala213 = "2.13.11"

ThisBuild / crossScalaVersions := List(scala212, scala213)

assembly / assemblyShadeRules := Seq(
  ShadeRule.rename("example.A" -> "example.C").inProject
)
