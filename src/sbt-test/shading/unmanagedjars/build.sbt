lazy val testshade = (project in file(".")).
  settings(
    version := "0.1",
    assembly / assemblyJarName := "foo.jar",
    scalaVersion := "2.10.7",
    libraryDependencies += "commons-io" % "commons-io" % "2.4",
    assembly / assemblyShadeRules := Seq(
      ShadeRule.zap("remove.**").inProject,
      ShadeRule.rename("toshade.ShadeClass" -> "toshade.ShadedClass").inProject,
      ShadeRule.rename("com.shade.unmanaged.**" -> "shaded_package.@0").inAll,
      ShadeRule.rename("toshade.ShadePackage" -> "shaded_package.ShadePackage").inProject,
      ShadeRule.rename("org.apache.commons.io.**" -> "shadeio.@1").inLibrary("commons-io" % "commons-io" % "2.4").inProject
    ),
    // logLevel in assembly := Level.Debug,
    TaskKey[Unit]("check") := {
      IO.withTemporaryDirectory { dir ⇒
        IO.unzip(crossTarget.value / "foo.jar", dir)
        mustNotExist(dir / "remove" / "Removed.class")
        mustNotExist(dir / "org" / "apache" / "commons" / "io" / "ByteOrderMark.class")
        mustExist(dir / "shaded_package" / "ShadePackage.class")
        mustExist(dir / "shaded_package" / "com" / "shade" / "unmanaged" / "test" / "Bar.class")
        mustExist(dir / "Foo.class")
        mustExist(dir / "toshade" / "ShadedClass.class")
        mustExist(dir / "shadeio" / "ByteOrderMark.class")
      }
      val process = sys.process.Process("java", Seq("-jar", (crossTarget.value / "foo.jar").toString))
      val out = (process!!)
      if (out.trim != "hello shadeio.filefilter.AgeFileFilter") sys.error("unexpected output: " + out)
      ()
    })

def mustNotExist(f: File): Unit = {
  if (f.exists) sys.error("file" + f + " exists!")
}
def mustExist(f: File): Unit = {
  if (!f.exists) sys.error("file" + f + " does not exist!")
}
