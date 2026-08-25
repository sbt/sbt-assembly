lazy val keepPatterns = settingKey[Seq[String]]("keep patterns under test")

def classPresent(jar: File, entry: String): Boolean = {
  val zf = new java.util.zip.ZipFile(jar)
  try zf.getEntry(entry) != null finally zf.close()
}

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    scalaVersion := "2.12.18",
    exportJars := false,
    assembly / assemblyJarName := "foo.jar",
    keepPatterns := Seq("keep.**"),
    assembly / assemblyShadeRules := Seq(ShadeRule.keep(keepPatterns.value: _*).inProject),
    TaskKey[Unit]("checkKeptOnly") := {
      val jar = (assembly / assemblyOutputPath).value
      assert(classPresent(jar, "keep/Kept$.class"), "keep/Kept$.class missing")
      assert(!classPresent(jar, "removed/Dropped$.class"), "removed/Dropped$.class should be dropped")
    },
    TaskKey[Unit]("checkBothKept") := {
      val jar = (assembly / assemblyOutputPath).value
      val kept = classPresent(jar, "keep/Kept$.class")
      val dropped = classPresent(jar, "removed/Dropped$.class")
      streams.value.log.info(s"kept=$kept droppedClassPresent=$dropped")
      assert(kept, "keep/Kept$.class missing")
      assert(dropped, "STALE CACHE: keep rule widened to removed.** but removed/Dropped$.class is still absent")
    }
  )
