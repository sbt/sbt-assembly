@transient
lazy val recordAssemblyMtime = taskKey[Unit]("Records the assembly JAR's modification time")

@transient
lazy val checkAssemblyNotRebuilt = taskKey[Unit]("Checks that unchanged assembly did not rewrite its JAR")

@transient
lazy val checkAssemblyRebuilt = taskKey[Unit]("Checks that assembly rewrote its JAR")

lazy val assemblyMtimeSettings: Seq[Def.Setting[?]] = {
  object AssemblyMtime {
    private val recordFileName = "recorded-assembly-mtime.txt"

    private def recordFile(crossTarget: File): File =
      crossTarget / recordFileName

    private def read(output: File): String =
      java.nio.file.Files.getLastModifiedTime(output.toPath).toString

    def record(output: File, crossTarget: File): Unit =
      IO.write(recordFile(crossTarget), read(output))

    def assertChange(output: File, crossTarget: File, expectedChange: Boolean): Unit = {
      val expected = IO.read(recordFile(crossTarget)).trim
      val actual = read(output)
      val changed = actual != expected
      assert(
        changed == expectedChange,
        if (expectedChange) s"assembly did not rewrite $output"
        else s"unchanged assembly rewrote $output: $actual (expected $expected)",
      )
    }
  }

  // These utilities record the assembly JAR's timestamp and verify whether
  // a subsequent `assembly` invocation did or did not rewrite that JAR.
  Seq(
    recordAssemblyMtime := {
      AssemblyMtime.record((assembly / assemblyOutputPath).value, crossTarget.value)
    },
    checkAssemblyNotRebuilt := {
      AssemblyMtime.assertChange(
        (assembly / assemblyOutputPath).value,
        crossTarget.value,
        expectedChange = false,
      )
    },
    checkAssemblyRebuilt := {
      AssemblyMtime.assertChange(
        (assembly / assemblyOutputPath).value,
        crossTarget.value,
        expectedChange = true,
      )
    },
  )
}

lazy val root = (project in file(".")).
  settings(
    version := "0.1",
    scalaVersion := "2.12.18",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10" % "test",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "0.9.29" % "runtime",
    assembly / assemblyJarName := "foo.jar",
    InputKey[Seq[File]]("genresource") := {
      val dirs = (Compile / unmanagedResourceDirectories).value
      val file = dirs.head / "foo.txt"
      IO.write(file, "bye")
      Seq(file)
    },
    InputKey[Seq[File]]("genresource2") := {
      val dirs = (Compile / unmanagedResourceDirectories).value
      val file = dirs.head / "bar.txt"
      IO.write(file, "bye")
      Seq(file)
    },
    TaskKey[Unit]("check") := {
      val process = sys.process.Process("java", Seq("-jar", (crossTarget.value / "foo.jar").toString))
      val out = process.!!
      if (out.trim != "hello") sys.error("unexpected output: " + out)
      ()
    },
    TaskKey[Unit]("checkfoo") := {
      val process = sys.process.Process("java", Seq("-jar", (crossTarget.value / "foo.jar").toString))
      val out = process.!!
      if (out.trim != "foo.txt") sys.error("unexpected output: " + out)
      ()
    },

    assemblyMtimeSettings,

    TaskKey[Unit]("checkhash") := {
      import java.security.MessageDigest
      val s = streams.value
      val jarHash = crossTarget.value / "jarHash.txt"
      val hash = MessageDigest.getInstance("SHA-1").digest(IO.readBytes(crossTarget.value / "foo.jar")).map( b => "%02x".format(b) ).mkString
      if ( jarHash.exists )
      {
        val prevHash = IO.read(jarHash)
        s.log.info( "Checking hash: " + hash + ", " + prevHash )
        assert( hash == prevHash )
      }
      IO.write( jarHash, hash )
      ()
    }
  )
