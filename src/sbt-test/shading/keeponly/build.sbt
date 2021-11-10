lazy val scala212 = "2.12.15"
lazy val scala213 = "2.13.7"

scalaVersion := scala212
crossScalaVersions := List(scala212, scala213)

lazy val testkeep = (project in file(".")).
  settings(
    version := "0.1",
    assembly / assemblyJarName := "foo.jar",
    assembly / assemblyShadeRules := Seq(
      ShadeRule.keep("keep.**").inProject
    ),
    TaskKey[Unit]("check") := {
      IO.withTemporaryDirectory { dir ⇒
        IO.unzip(crossTarget.value / "foo.jar", dir)
        mustNotExist(dir / "removed" / "ShadeClass.class")
        mustNotExist(dir / "removed" / "ShadePackage.class")
        mustExist(dir / "keep" / "Keeped.class")
      }
    })

def mustNotExist(f: File): Unit = {
  if (f.exists) sys.error("file" + f + " exists!")
}
def mustExist(f: File): Unit = {
  if (!f.exists) sys.error("file" + f + " does not exist!")
}
