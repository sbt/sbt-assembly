import scala.jdk.CollectionConverters.*

version := "0.1"
scalaVersion := "3.3.4"
assemblyPackageScala / assembleArtifact := false

def entries(jar: File): Set[String] = {
  val zip = new java.util.zip.ZipFile(jar)
  try zip.entries.asScala.map(_.getName).toSet
  finally zip.close()
}

lazy val root = (project in file("."))
  .settings(
    name := "foo",
    assembly / mainClass := Some("Main"),

    TaskKey[Unit]("checkScalaExcluded") := {
      val found = entries(crossTarget.value / "foo-assembly-0.1.jar").filter(_.startsWith("scala/"))
      if (found.nonEmpty)
        sys.error(s"expected no Scala library entries, found ${found.size}, e.g. ${found.take(5).toList}")
    },

    TaskKey[Unit]("checkScalaPackaged") := {
      val found = entries(crossTarget.value / "scala-library-3.3.4-assembly.jar")
      val expected = Set(
        "scala/CanEqual.class",
        "scala/reflect/Enum.class",
        "scala/collection/immutable/List.class"
      )
      val missing = expected -- found
      if (missing.nonEmpty) sys.error(s"missing from the Scala library JAR: ${missing.toList.sorted}")
    },

    TaskKey[Unit]("run1") := {
      val out = sys.process.Process("java", Seq("-cp",
        (crossTarget.value / "foo-assembly-0.1.jar").toString,
        "Main")).!!
      if (out.trim != "hello") sys.error("unexpected output: " + out)
    },

    TaskKey[Unit]("run2") := {
      val out = sys.process.Process("java", Seq("-cp",
        (crossTarget.value / "scala-library-3.3.4-assembly.jar").toString +
        (if (scala.util.Properties.isWin) ";" else ":") +
        (crossTarget.value / "foo-assembly-0.1.jar").toString,
        "Main")).!!
      if (out.trim != "hello") sys.error("unexpected output: " + out)
    }
  )
