lazy val testmerge = (project in file(".")).
  settings(
    version := "0.1",
    assemblyJarName in assembly := "foo.jar",
    assemblyMergeStrategy in assembly := {
      val old = (assemblyMergeStrategy in assembly).value

      {
        case _ => MergeStrategy.singleOrError
      }
    }
  )
