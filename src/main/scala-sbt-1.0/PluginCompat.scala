package sbtassembly

import sbt._
import Keys._

object PluginCompat {
  val ClasspathUtilities = sbt.internal.inc.classpath.ClasspathUtilities
  val Using = sbt.io.Using
}
