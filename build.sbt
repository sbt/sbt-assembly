lazy val commonSettings: Seq[Setting[_]] = Seq(
  version in ThisBuild := "0.14.6",
  organization in ThisBuild := "com.eed3si9n"
)

lazy val root = (project in file(".")).
  // enablePlugins(GitVersioning).
  settings(commonSettings: _*).
  settings(
    sbtPlugin := true,
    name := "sbt-assembly",
    description := "sbt plugin to create a single fat jar",
    licenses := Seq("MIT License" -> url("https://github.com/sbt/sbt-assembly/blob/master/LICENSE")),
    scalacOptions := Seq("-deprecation", "-unchecked", "-Dscalac.patmat.analysisBudget=1024"),
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic" % "3.0.1",
      "org.pantsbuild" % "jarjar" % "1.6.5"
    ),
    publishArtifact in (Compile, packageBin) := true,
    publishArtifact in (Test, packageBin) := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := true
  )
