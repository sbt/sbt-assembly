ThisBuild / assemblyMergeStrategy := {
  case "sbtassembly" => CustomMergeStrategy("Reverse characters", 1) { dependencies => // will always invalidate the cache
    import java.io.{ BufferedReader, ByteArrayInputStream, InputStreamReader }
    import sbtassembly.Assembly.Dependency
    import sbt.io.Using
    Right(
      dependencies.map { dependency =>
        val reversed = Using.resource((dep: Dependency) => dep.stream())(dependency) { is =>
          IO.readLines(new BufferedReader(new InputStreamReader(is, IO.defaultCharset)))
            .map(_.reverse)
        }.mkString("")
        JarEntry(dependency.target, () => new ByteArrayInputStream(reversed.getBytes(IO.defaultCharset)))
      }
    )
  }
  case x   =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

lazy val testmerge = (project in file(".")).
  settings(
    version := "0.1",
    assembly / assemblyJarName := "foo.jar",
    TaskKey[Unit]("check") := {
      IO.withTemporaryDirectory { dir =>
        IO.unzip(crossTarget.value / "foo.jar", dir)
        mustContain(dir / "sbtassembly", Seq("reversed"))
      }
    }
  )

def mustContain(f: File, l: Seq[String]): Unit = {
  val lines = IO.readLines(f, IO.utf8)
  if (lines != l)
    throw new Exception("file " + f + " had wrong content:\n" + lines.mkString("\n") +
      "\n*** instead of ***\n" + l.mkString("\n"))
}

TaskKey[Unit]("copy-preserve-last-modified") := {
  IO.copy(Seq((crossTarget.value / "foo.jar") -> (crossTarget.value / "foo-1.jar")), true, true, true)
}

TaskKey[Unit]("fileCheck1") := {
  assert((crossTarget.value / "foo.jar").exists())
}
