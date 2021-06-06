ThisBuild / version := "0.15.1-SNAPSHOT"
ThisBuild / organization := "com.eed3si9n"

def scala212 = "2.12.8"
def scala210 = "2.10.7"
ThisBuild / crossScalaVersions := Seq(scala212, scala210)
ThisBuild / scalaVersion := scala212

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin) // ContrabandPlugin
  .settings(pomConsistency2021DraftSettings)
  .settings(nocomma {
    name := "sbt-assembly"
    scalacOptions := Seq("-deprecation", "-unchecked", "-Dscalac.patmat.analysisBudget=1024", "-Xfuture")
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic" % "3.0.8",
      "com.eed3si9n.jarjarabrams" %% "jarjar-abrams-core" % "0.1.0",
      "org.scalatest" %% "scalatest" % "3.1.1" % Test,
    )
    (pluginCrossBuild / sbtVersion) := {
      scalaBinaryVersion.value match {
        case "2.10" => "0.13.18"
        case "2.12" => "1.2.8"
      }
    }
    Compile / generateContrabands / sourceManaged := baseDirectory.value / "src" / "main" / "contraband-scala"
  })

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/sbt/sbt-assembly"),
    "scm:git@github.com:sbt/sbt-assembly.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "eed3si9n",
    name  = "Eugene Yokota",
    email = "@eed3si9n",
    url   = url("https://eed3si9n.com/")
  ),
)
ThisBuild / description := "sbt plugin to create a single fat jar"
ThisBuild / homepage := Some(url("https://github.com/sbt/sbt-assembly"))
ThisBuild / licenses := Seq("MIT" -> url("https://github.com/sbt/sbt-assembly/blob/master/LICENSE"))
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true

// See https://eed3si9n.com/pom-consistency-for-sbt-plugins
lazy val pomConsistency2021Draft = settingKey[Boolean]("experimental")

/**
 * this is an unofficial experiment to re-publish plugins with better Maven compatibility
 */
def pomConsistency2021DraftSettings: Seq[Setting[_]] = Seq(
  pomConsistency2021Draft := Set("true", "1")(sys.env.get("POM_CONSISTENCY").getOrElse("false")),
  moduleName := {
    if (pomConsistency2021Draft.value)
      sbtPluginModuleName2021Draft(moduleName.value,
        (pluginCrossBuild / sbtBinaryVersion).value)
    else moduleName.value
  },
  projectID := {
    if (pomConsistency2021Draft.value) sbtPluginExtra2021Draft(projectID.value)
    else projectID.value
  },
)

def sbtPluginModuleName2021Draft(n: String, sbtV: String): String =
  s"""${n}_sbt${if (sbtV == "1.0") "1" else if (sbtV == "2.0") "2" else sbtV}"""

def sbtPluginExtra2021Draft(m: ModuleID): ModuleID =
  m.withExtraAttributes(Map.empty)
   .withCrossVersion(CrossVersion.binary)
