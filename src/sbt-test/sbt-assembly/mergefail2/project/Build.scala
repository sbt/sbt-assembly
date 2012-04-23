package test

import sbt._
import Keys._
import sbtassembly.Plugin._
import AssemblyKeys._

object B extends Build {
  lazy val project = Project("testmerge", file("."),
    settings = Defaults.defaultSettings ++ assemblySettings ++ Seq(
      version := "0.1",
      jarName in assembly := "foo.jar"
    ))
}
