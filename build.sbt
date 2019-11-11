lazy val commonSettings: Seq[Setting[_]] = Seq(
  version in ThisBuild := "0.14.11-SNAPSHOT",
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
    scalacOptions := Seq("-deprecation", "-unchecked", "-Dscalac.patmat.analysisBudget=1024", "-Xfuture"),
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic" % "3.0.8",
      "org.pantsbuild" % "jarjar" % "1.7.2"
    ),
    crossSbtVersions := Seq("0.13.18", "1.2.8"), // https://github.com/sbt/sbt/issues/5049
    publishArtifact in (Compile, packageBin) := true,
    publishArtifact in (Test, packageBin) := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := true
  )
