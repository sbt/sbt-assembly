sbtPlugin := true

name := "sbt-assembly"

organization := "com.eed3si9n"

version := "0.9.0-SNAPSHOT"

CrossBuilding.crossSbtVersions := Seq("0.11.3", "0.11.2" ,"0.12")

description := "sbt plugin to create a single fat jar"

licenses := Seq("MIT License" -> url("https://github.com/sbt/sbt-assembly/blob/master/LICENSE"))

scalacOptions := Seq("-deprecation", "-unchecked")

publishArtifact in (Compile, packageBin) := true

publishArtifact in (Test, packageBin) := false

publishArtifact in (Compile, packageDoc) := false

publishArtifact in (Compile, packageSrc) := true

// CrossBuilding.scriptedSettings
ScriptedPlugin.scriptedSettings

scriptedLaunchOpts ++= Seq("-Xmx1024M", "-XX:MaxPermSize=256M")

scriptedBufferLog := false

publishMavenStyle := false

publishTo <<= (version) { version: String =>
   val scalasbt = "http://scalasbt.artifactoryonline.com/scalasbt/"
   val (name, u) = if (version.contains("-SNAPSHOT")) ("sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
                   else ("sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
   Some(Resolver.url(name, url(u))(Resolver.ivyStylePatterns))
}

credentials += Credentials(Path.userHome / ".ivy2" / ".sbtcredentials")

lsSettings

LsKeys.tags in LsKeys.lsync := Seq("sbt", "jar")

(externalResolvers in LsKeys.lsync) := Seq(
  "sbt-plugin-releases" at "http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases")
