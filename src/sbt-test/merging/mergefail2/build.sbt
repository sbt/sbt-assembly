lazy val testmerge = (project in file(".")).
  settings(
    version := "0.1",
    assembly / assemblyJarName := "foo.jar"
  )
