import AssemblyKeys._

version := "0.1"

assemblySettings

scalaVersion := "2.9.1"

libraryDependencies += "org.scalatest" % "scalatest_2.9.0" % "1.6.1" % "test"

libraryDependencies += "com.weiglewilczek.slf4s" %% "slf4s" % "1.0.7"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "0.9.29" % "runtime"

assemblyOption in assembly ~= { _.copy(cacheOutput = true) }

assemblyOption in assembly ~= { _.copy(cacheUnzip = true) }

jarName in assembly := "foo.jar"

TaskKey[Seq[File]]("genresource") <<= (unmanagedResourceDirectories in Compile) map { (dirs) =>
  val file = dirs.head / "foo.txt"
  IO.write(file, "bye")
  Seq(file)
}

TaskKey[Seq[File]]("genresource2") <<= (unmanagedResourceDirectories in Compile) map { (dirs) =>
  val file = dirs.head / "bar.txt"
  IO.write(file, "bye")
  Seq(file)
}

TaskKey[Unit]("check") <<= (crossTarget) map { (crossTarget) =>
  val process = sbt.Process("java", Seq("-jar", (crossTarget / "foo.jar").toString))
  val out = (process!!)
  if (out.trim != "hello") error("unexpected output: " + out)
  ()
}

TaskKey[Unit]("checkfoo") <<= (crossTarget) map { (crossTarget) =>
  val process = sbt.Process("java", Seq("-jar", (crossTarget / "foo.jar").toString))
  val out = (process!!)
  if (out.trim != "foo.txt") error("unexpected output: " + out)
  ()
}

TaskKey[Unit]("checkhash") <<= (crossTarget, streams) map { (crossTarget, s) =>
  import java.security.MessageDigest
  val jarHash = crossTarget / "jarHash.txt"
  val hash = MessageDigest.getInstance("SHA-1").digest(IO.readBytes(crossTarget / "foo.jar")).map( b => "%02x".format(b) ).mkString
  if ( jarHash.exists )
  {
    val prevHash = IO.read(jarHash)
    s.log.info( "Checking hash: " + hash + ", " + prevHash )
    assert( hash == prevHash )
  }
  IO.write( jarHash, hash )
  ()
}
