exportJars := false

lazy val shadeerror = (project in file(".")).
  settings(
    version := "0.1",
    assembly / assemblyJarName := "foo.jar",
    scalaVersion := "2.12.20",
    assembly / assemblyShadeRules := Seq(
      ShadeRule.rename("payload.**" -> "shadedpayload.@1").inAll
    ),
    TaskKey[Unit]("makeUnshadeableJar") := {
      val validClass = IO.readBytes((Compile / classDirectory).value / "Main.class")
      val futureClass = validClass.clone()
      futureClass(6) = 0.toByte
      futureClass(7) = 99.toByte
      val dir = IO.createTemporaryDirectory
      IO.write(dir / "future", futureClass)
      IO.write(dir / "garbage", "this is not bytecode".getBytes("UTF-8"))
      IO.write(dir / "misplaced", validClass)
      IO.write(dir / "resource", "a resource")
      val lib = baseDirectory.value / "lib"
      IO.createDirectory(lib)
      IO.zip(
        Seq(
          (dir / "future") -> "payload/Future.class",
          (dir / "garbage") -> "payload/Garbage.class",
          (dir / "misplaced") -> "payload/Misplaced.class",
          (dir / "resource") -> "payload/resource.txt"
        ),
        lib / "payload.jar",
        None
      )
      ()
    },
    TaskKey[Unit]("checkUnshadeableEntries") := {
      val zip = new java.util.zip.ZipFile(crossTarget.value / "foo.jar")
      val names =
        try {
          val builder = Set.newBuilder[String]
          val entries = zip.entries
          while (entries.hasMoreElements) builder += entries.nextElement.getName
          builder.result()
        } finally zip.close()
      mustExist(names, "payload/Future.class")
      mustExist(names, "payload/Garbage.class")
      mustNotExist(names, "payload/Misplaced.class")
      mustExist(names, "shadedpayload/resource.txt")
      ()
    }
  )

def mustExist(names: Set[String], name: String): Unit =
  if (!names(name)) sys.error(s"$name is missing from the assembly")

def mustNotExist(names: Set[String], name: String): Unit =
  if (names(name)) sys.error(s"$name is present in the assembly")
