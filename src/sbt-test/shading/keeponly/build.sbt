lazy val scala211 = "2.11.12"
lazy val scala212 = "2.12.14"
lazy val scala213 = "2.13.6"

scalaVersion := scala211
crossScalaVersions := List(scala211, scala212, scala213)

lazy val testkeep = (project in file(".")).
  settings(
    version := "0.1",
    assemblyJarName in assembly := "foo.jar",
    assemblyShadeRules in assembly := Seq(
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
