scalaVersion := scala212

lazy val scala212 = "2.12.15"
lazy val scala213 = "2.13.7"

ThisBuild / crossScalaVersions := List(scala212, scala213)

assembly / assemblyShadeRules := Seq(
  ShadeRule.rename("example.A" -> "example.C").inProject
)
