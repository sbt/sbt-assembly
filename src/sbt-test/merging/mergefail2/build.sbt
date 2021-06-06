lazy val testmerge = (project in file(".")).
  settings(
    version := "0.1",
    assemblyJarName in assembly := "foo.jar"
  )
