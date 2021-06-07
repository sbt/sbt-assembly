version in ThisBuild := "0.1"
scalaVersion in ThisBuild := "2.11.12"
assemblyMergeStrategy in ThisBuild := {
  case "a" => MergeStrategy.concat
  case "b" => MergeStrategy.first
  case "c" => MergeStrategy.last
  case "d" => MergeStrategy.filterDistinctLines
  case "e" => MergeStrategy.deduplicate
  case "f" => MergeStrategy.discard
  case PathList("x", "y") => MergeStrategy.discard
  case x   =>
    val oldStrategy = (assemblyMergeStrategy in ThisBuild).value
    oldStrategy(x)
}

lazy val testmerge = (project in file("."))
  .settings(
    assemblyJarName in assembly := "foo.jar",
    TaskKey[Unit]("check") := {
      IO.withTemporaryDirectory { dir ⇒
        IO.unzip(crossTarget.value / "foo.jar", dir)
        mustContain(dir / "a", Seq("1", "2", "1", "3"))
        mustContain(dir / "b", Seq("1"))
        mustContain(dir / "c", Seq("1", "3"))
        mustContain(dir / "d", Seq("1", "2", "3"))
        mustContain(dir / "e", Seq("1"))
        mustNotExist(dir / "f")
        mustContain(dir / "README", Seq("resources"))
        mustContain(dir / "README_1", Seq("1"))
        mustContain(dir / "LICENSE", Seq("resources"))
        mustContain(dir / "LICENSE_1" / "a", Seq("1"))
        // 80f5a06 -- don't rename License.class
        mustExist(dir / "com" / "example" / "License.class")
        // 7ce9509 -- rename things inside META-INF
        mustContain(dir / "META-INF" / "NOTICE_2.txt", Seq("This is the notice for 2"))
        mustContain(dir / "META-INF" / "NOTICE_3.txt", Seq("This is the notice for 3"))
        // 032b3a2 -- Don't rename the directories if they contain
        // classes; if they don't, don't rename the contents.
        mustExist(dir / "com" / "example" / "license" / "PublicDomain.class")
        mustExist(dir / "NOTICE_3" / "README.txt")
        mustExist(dir / "NOTICE_3" / "LICENSE.txt")
      }
    })

def mustContain(f: File, l: Seq[String]): Unit = {
  val lines = IO.readLines(f, IO.utf8)
  if (lines != l)
    throw new Exception("file " + f + " had wrong content:\n" + lines.mkString("\n") +
      "\n*** instead of ***\n" + l.mkString("\n"))
}
def mustNotExist(f: File): Unit = {
  if (f.exists) sys.error("file" + f + " exists!")
}
def mustExist(f: File): Unit = {
  if (!f.exists) sys.error("file" + f + " does not exist!")
}
