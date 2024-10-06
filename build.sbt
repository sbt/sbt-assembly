ThisBuild / version := {
  val old = (ThisBuild / version).value
  if ((ThisBuild / isSnapshot).value) "2.1.2-SNAPSHOT"
  else old
}

ThisBuild / organization := "com.eed3si9n"

def scala212 = "2.12.20"
def scala3 = "3.3.4"
ThisBuild / crossScalaVersions := Seq(scala212, scala3)
ThisBuild / scalaVersion := scala3

lazy val jarjar = "com.eed3si9n.jarjarabrams" %% "jarjar-abrams-core" % "1.14.0"

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin, ContrabandPlugin)
  .settings(nocomma {
    name := "sbt-assembly"
    scalacOptions := Seq(
      "-Xsource:3",
      "-deprecation",
      "-unchecked",
      "-Dscalac.patmat.analysisBudget=1024",
      "-Xfuture",
    )
    libraryDependencies += jarjar.cross(CrossVersion.for3Use2_13)
    (pluginCrossBuild / sbtVersion) := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.5.8"
        case _      => "2.0.0-M2"
      }
    }
    scriptedSbt := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.10.2"
        case _      => "2.0.0-M2"
      }
    }
    Compile / generateContrabands / sourceManaged := baseDirectory.value / "src" / "main" / "scala"
    scriptedLaunchOpts := { scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    }
    scriptedBufferLog := false
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
ThisBuild / description := "sbt plugin to create a single über jar"
ThisBuild / homepage := Some(url("https://github.com/sbt/sbt-assembly"))
ThisBuild / licenses := Seq("MIT" -> url("https://github.com/sbt/sbt-assembly/blob/master/LICENSE"))
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true
