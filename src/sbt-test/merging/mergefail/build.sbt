lazy val testmerge = (project in file(".")).
  settings(
    version := "0.1",
    assembly / assemblyJarName := "foo.jar",
    assembly / assemblyMergeStrategy := {
      val old = (assembly / assemblyMergeStrategy).value

      {
        case _ => MergeStrategy.singleOrError
      }
    }
  )
