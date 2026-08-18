import java.io.{ File, FileOutputStream }
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.jar.{ JarEntry, JarOutputStream }

@transient
lazy val recordAssemblyOutput = taskKey[Unit]("Records the current assembly output")

@transient
lazy val checkAssemblyNotRebuilt = taskKey[Unit]("Checks that assembly did not rewrite its output")

@transient
lazy val checkAssemblyRebuilt = taskKey[Unit]("Checks that assembly rewrote its output")

lazy val cacheKeyManifestValue = settingKey[String]("Value of a manifest attribute used by the cache-key test")
lazy val cacheKeyMergeStrategy = settingKey[String]("Built-in conflict strategy used by the cache-key test")

lazy val writeClass = inputKey[Unit]("Updates a compiled-class input")
lazy val writeProcessedResource = inputKey[Unit]("Updates a processed-resource input")
lazy val writeDependencyJar = inputKey[Unit]("Updates a dependency-JAR input")

lazy val assemblyOutputAssertions: Seq[Def.Setting[?]] = {
  object AssemblyOutput {
    private val recordFileName = "recorded-assembly-output.txt"

    private def recordFile(crossTarget: File): File =
      crossTarget / recordFileName

    private def output(baseOutput: File): File =
      if (baseOutput.exists) baseOutput
      else {
        val namePrefix = baseOutput.getName.stripSuffix(".jar") + "-"
        val candidates = Option(baseOutput.getParentFile.listFiles).toSeq.flatten.filter { file =>
          file.isFile && file.getName.startsWith(namePrefix) && file.getName.endsWith(".jar")
        }
        require(candidates.size == 1, s"Expected one hashed assembly output for $baseOutput, found $candidates")
        candidates.head
      }

    private def snapshot(output: File): String =
      s"${output.getAbsolutePath}\t${Files.getLastModifiedTime(output.toPath)}"

    private def read(crossTarget: File): Array[String] =
      IO.read(recordFile(crossTarget)).trim.split("\\t", 2)

    def record(baseOutput: File, crossTarget: File): Unit =
      IO.write(recordFile(crossTarget), snapshot(output(baseOutput)))

    def assertChange(baseOutput: File, crossTarget: File, expectedChange: Boolean): Unit = {
      val previous = read(crossTarget)
      val current = snapshot(output(baseOutput)).split("\\t", 2)
      val changed = previous(0) != current(0) || previous(1) != current(1)
      assert(
        changed == expectedChange,
        if (expectedChange) s"assembly did not rewrite its output: ${current(0)}"
        else s"unchanged assembly rewrote ${current(0)}: ${current(1)} (expected ${previous(1)})",
      )
    }
  }

  Seq(
    recordAssemblyOutput := {
      AssemblyOutput.record((assembly / assemblyOutputPath).value, crossTarget.value)
    },
    checkAssemblyNotRebuilt := {
      AssemblyOutput.assertChange(
        (assembly / assemblyOutputPath).value,
        crossTarget.value,
        expectedChange = false,
      )
    },
    checkAssemblyRebuilt := {
      AssemblyOutput.assertChange(
        (assembly / assemblyOutputPath).value,
        crossTarget.value,
        expectedChange = true,
      )
    },
  )
}

def write(file: File, content: String): Unit = {
  IO.write(file, content)
  val timestamp = math.max(System.currentTimeMillis(), file.lastModified + 1000L)
  Files.setLastModifiedTime(file.toPath, FileTime.fromMillis(timestamp))
}

lazy val root = (project in file(".")).settings(
  version := "0.1",
  scalaVersion := "2.12.18",
  assembly / assemblyJarName := "cache-key.jar",
  cacheKeyManifestValue := "one",
  cacheKeyMergeStrategy := "first",
  Compile / unmanagedJars ++= {
    val conv = fileConverter.value
    implicit val c: xsbti.FileConverter = conv
    (baseDirectory.value / "lib" ** "cache-key-input.jar").classpath
  },
  assembly / packageOptions += Package.ManifestAttributes("Cache-Key-Test" -> cacheKeyManifestValue.value),
  assemblyMergeStrategy := {
    val conflictStrategy = cacheKeyMergeStrategy.value
    (path: String) =>
      if (path == "conflict.txt" && conflictStrategy == "first") MergeStrategy.first
      else if (path == "conflict.txt") MergeStrategy.last
      else MergeStrategy.defaultMergeStrategy(path)
  },
  writeClass := {
    val value = sbt.complete.Parsers.spaceDelimited("<value>").parsed.mkString(" ")
    write((Compile / scalaSource).value / "Example.scala", s"""object Example { val value = "$value" }
""")
  },
  writeProcessedResource := {
    val value = sbt.complete.Parsers.spaceDelimited("<value>").parsed.mkString(" ")
    write((Compile / classDirectory).value / "generated-resource.txt", value)
  },
  writeDependencyJar := {
    val value = sbt.complete.Parsers.spaceDelimited("<value>").parsed.mkString(" ")
    val jar = baseDirectory.value / "lib" / "cache-key-input.jar"
    IO.createDirectory(jar.getParentFile)
    val out = new JarOutputStream(new FileOutputStream(jar))
    try {
      Seq("conflict.txt" -> s"dependency-$value", "dependency.txt" -> value).foreach { case (name, content) =>
        out.putNextEntry(new JarEntry(name))
        out.write(content.getBytes(StandardCharsets.UTF_8))
        out.closeEntry()
      }
    } finally out.close()
    val timestamp = math.max(System.currentTimeMillis(), jar.lastModified + 1000L)
    Files.setLastModifiedTime(jar.toPath, FileTime.fromMillis(timestamp))
  },
  assemblyOutputAssertions,
)
