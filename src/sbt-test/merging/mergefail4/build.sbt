ThisBuild / assemblyMergeStrategy := {
  case "Test.class" => MergeStrategy.rename
  case x   =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}
assembly / logLevel := Level.Debug
lazy val testmerge = (project in file(".")).
  settings(
    version := "0.1",
    assembly / assemblyJarName := "foo.jar"
  )
