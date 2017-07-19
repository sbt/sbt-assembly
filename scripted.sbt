scriptedSettings1

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value)
}

scriptedBufferLog := false

def scriptedSettings1 = Def settings (
  ScriptedPlugin.scriptedSettings filterNot (_.key.key.label == libraryDependencies.key.label),
  libraryDependencies ++= {
    val cross = CrossVersion.partialVersion(scriptedSbt.value) match {
      case Some((0, 13)) => CrossVersion.Disabled
      case Some((1, _))  => CrossVersion.binary
      case _             => sys error s"Unhandled sbt version ${scriptedSbt.value}"
    }
    Seq(
      "org.scala-sbt" % "scripted-sbt" % scriptedSbt.value % scriptedConf.toString cross cross,
      "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % scriptedLaunchConf.toString
    )
  }
)
