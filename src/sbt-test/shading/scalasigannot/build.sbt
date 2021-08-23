version in ThisBuild := "1.0-SNAPSHOT"
organization in ThisBuild := "scalasigannottest"
scalaVersion in ThisBuild := "2.13.1"

lazy val scala211 = "2.11.12"
lazy val scala212 = "2.12.14"
lazy val scala213 = "2.13.6"

crossScalaVersions in ThisBuild := List(scalaVersion.value, scala211, scala212, scala213)


val shadingSettings: Seq[Def.Setting[_]] = Seq(
  assemblyShadeRules in assembly := Seq(
    ShadeRule.rename(
      "to.be.shaded.**" -> "shade.@1"
    ).inAll
  ),
  assemblyOption in assembly ~= { _.withIncludeScala(false) },
  assemblyExcludedJars in assembly := {
    val cp = (fullClasspath in assembly).value
    cp.filterNot {p =>
      p.data.getName.startsWith("tobeshaded")
    }
  },

  artifactClassifier in (sbt.Test, packageBin) := None,
  artifact in (Compile, assembly) := (artifact in (Compile, assembly)).value.withClassifier(Some("shaded"))

) ++ addArtifact(artifact in (Compile, assembly), assembly).settings

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
      (unmanagedJars in Compile) := {
        val tbs: File = ((packageBin in Compile) in toBeShaded).value
        //Seq(sbt.internal.util.Attributed.blank[java.io.File](tbs))

        Seq(Attributed.blank[java.io.File](tbs))
      }
    )
  )
  .settings(shadingSettings)

// Application using fatLib
lazy val root = project.in(file("."))
  .settings(
    Seq(
      name := "scalasiggannottest",
      mainClass in assembly := Some("scalasigannot.Main"),
      libraryDependencies := Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      ),
      (unmanagedJars in Compile) := {
        //val tbs: File = ((packageBin in (Compile, assembly)) in fatLib).value
        //Seq(sbt.internal.util.Attributed.blank[java.io.File](tbs))

        val x = (assembly in (fatLib, Compile)).value
        Seq(Attributed.blank[java.io.File](x))
      }
    )
  )
  .aggregate(fatLib, toBeShaded)