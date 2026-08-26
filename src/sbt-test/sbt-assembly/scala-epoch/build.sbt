ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "3.3.4"

lazy val known = (project in file("known"))
  .settings(
    name := "known",
    Compile / scalaSource := (ThisBuild / baseDirectory).value / "src" / "main" / "scala"
  )

lazy val unknown = (project in file("unknown"))
  .settings(
    name := "unknown",
    Compile / scalaSource := (ThisBuild / baseDirectory).value / "src" / "main" / "scala",
    assembly / assemblyOption ~= { _.withScalaVersion("4.0.0") }
  )
