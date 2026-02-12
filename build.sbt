ThisBuild / version := {
  val old = (ThisBuild / version).value
  if ((ThisBuild / isSnapshot).value) "2.1.2-SNAPSHOT"
  else old
}

ThisBuild / organization := "com.eed3si9n"

def scala212 = "2.12.20"
def scala3 = "3.7.2"
ThisBuild / crossScalaVersions := Seq(scala212, scala3)
ThisBuild / scalaVersion := scala3

lazy val jarjar = "com.eed3si9n.jarjarabrams" %% "jarjar-abrams-core" % "1.14.1"

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin, ContrabandPlugin)
  .settings(nocomma {
    name := "sbt-assembly"
    scalacOptions ++= {
      scalaBinaryVersion.value match {
        case "3" =>
          Nil
        case _ =>
          Seq(
            "-Xsource:3",
            "-Xfuture",
          )
      }
    }
    scalacOptions ++= Seq(
      "-deprecation",
      "-unchecked",
      "-Dscalac.patmat.analysisBudget=1024",
    )
    libraryDependencies += jarjar.cross(CrossVersion.for3Use2_13)
    addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.1.0")
    (pluginCrossBuild / sbtVersion) := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.5.8"
        case _      => "2.0.0-RC6"
      }
    }
    scriptedSbt := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.10.7"
        case _      => "2.0.0-RC6"
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
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  val v = (ThisBuild / version).value
  if (v.endsWith("SNAPSHOT")) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}
ThisBuild / publishMavenStyle := true
