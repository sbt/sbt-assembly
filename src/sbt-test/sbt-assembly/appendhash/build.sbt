ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "2.12.18"
ThisBuild / assemblyAppendContentHash := true

lazy val root = (project in file("."))
  .settings(
    name := "foo",
    libraryDependencies ++= Seq(
      "com.eed3si9n" %% "gigahorse-okhttp" % "0.5.0"
    ),

    assembly / assemblyOption ~= {
      _.withIncludeScala(false)
       .withIncludeDependency(false)
    },

    InputKey[Unit]("checkFile") := {
      val args = sbt.complete.Parsers.spaceDelimited("<arg>").parsed
      val expectFileNameRegex = args.head.r
      assert((crossTarget.value ** "*.jar").get.exists{ jar =>
        expectFileNameRegex.findFirstIn(jar.getName).isDefined
      })
    },

    TaskKey[Unit]("checkPrevious") := {
      import sbinary.DefaultProtocol._
      import sbtassembly.PluginCompat._
      import CacheImplicits._
      assert(Some(assembly.value) == assembly.previous)
    }
  )
