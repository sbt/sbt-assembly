ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / organization := "scalasigannottest"
ThisBuild / scalaVersion := scala213

lazy val scala212 = "2.12.18"
lazy val scala213 = "2.13.11"

ThisBuild / crossScalaVersions := List(scala212, scala213)


val shadingSettings: Seq[Def.Setting[_]] = Seq(
  exportJars := false,
  assembly / assemblyShadeRules := Seq(
    ShadeRule.rename(
      "to.be.shaded.**" -> "shade.@1"
    ).inAll
  ),
  assembly / assemblyOption ~= { _.withIncludeScala(false) },
  assembly / assemblyExcludedJars := {
    val cp = (assembly / fullClasspath).value
    cp.filterNot {p =>
      p.data.toString.contains("tobeshaded")
    }
  },

  sbt.Test / packageBin / artifactClassifier := None,
  Compile / assembly / artifact := (Compile / assembly / artifact).value.withClassifier(Some("shaded"))

) ++ addArtifact(Compile / assembly / artifact, assembly).settings

// A jar to be shaded in shadedLib
lazy val toBeShaded = project.in(file("tobeshaded"))
  .settings(
    Seq(name := "tobeshaded")
  )

// Our shaded fatLib
lazy val fatLib = project.in(file("fatlib"))
  .settings(
    Seq(
      name := "fatlib",
      (Compile / unmanagedJars) := {
        val tbs = (toBeShaded / Compile / packageBin).value
        //Seq(sbt.internal.util.Attributed.blank[java.io.File](tbs))

        Seq(Attributed.blank(tbs))
      }
    )
  )
  .settings(shadingSettings)

// Application using fatLib
lazy val root = project.in(file("."))
  .settings(
    Seq(
      name := "scalasiggannottest",
      assembly / mainClass := Some("scalasigannot.Main"),
      libraryDependencies := Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      ),
      Compile / unmanagedJars := {
        //val tbs: File = ((packageBin in (Compile, assembly)) in fatLib).value
        //Seq(sbt.internal.util.Attributed.blank[java.io.File](tbs))

        val x = (fatLib / Compile / assembly).value
        Seq(Attributed.blank(x))
      },
      fgRun / aggregate := false,
    )
  )
  .aggregate(fatLib, toBeShaded)