import sbtassembly.Assembly._
import sbtassembly.MergeStrategy
import java.nio.file.Paths

ThisBuild / assemblyMergeStrategy := {
  case "a" | "c" | "d" => new MergeStrategy {  // this should be ignored in the second pass, so "e" renamed to "a" will not be renamed to "b" in the second pass
    override def name = MergeStrategy.rename.name // same name as an existing merge strategy, but should be OK as this class has a different FQCN

    override def merge(conflicts: Vector[Assembly.Dependency]) =
      Right(Vector(JarEntry(Paths.get("b"), conflicts.head.stream)))

    override val notifyThreshold = 1
  }
  case "b" => MergeStrategy.concat // should concatenate all the files renamed to "b" -> "a", "b", "c", and "d"
  case "e" => new MergeStrategy {  // this should be renamed to a in the first pass
    override def name = MergeStrategy.rename.name // same name as an existing merge strategy, but should be OK as this class has a different FQCN

    override def merge(conflicts: Vector[Assembly.Dependency]) =
      Right(Vector(JarEntry(Paths.get("a"), conflicts.head.stream)))

    override val notifyThreshold = 1
  }

  case x       =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

lazy val testmerge = (project in file(".")).
  settings(
    version := "0.1",
    assembly / assemblyJarName := "foo.jar",
    TaskKey[Unit]("check") := {
      IO.withTemporaryDirectory { dir ⇒
        IO.unzip(crossTarget.value / "foo.jar", dir)
        mustContain(dir / "b",  Seq("a", "b", "c", "d"))
        mustContain(dir / "a",  Seq("e"))
      }
    }
  )

def mustContain(f: File, l: Seq[String]): Unit = {
  val lines = IO.readLines(f, IO.utf8)
  if (lines.sorted != l.sorted)
    throw new Exception("file " + f + " had wrong content:\n" + lines.mkString("\n") +
      "\n*** instead of ***\n" + l.mkString("\n"))
}
