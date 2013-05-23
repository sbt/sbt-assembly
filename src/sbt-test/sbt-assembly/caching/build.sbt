import AssemblyKeys._

version := "0.1"

assemblySettings

scalaVersion := "2.9.1"

libraryDependencies += "org.scalatest" % "scalatest_2.9.0" % "1.6.1" % "test"

libraryDependencies += "com.weiglewilczek.slf4s" %% "slf4s" % "1.0.7"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "0.9.29" % "runtime"

assemblyCacheUnzip in assembly := true

assemblyCacheOutput in assembly := false

unmanagedJars in Compile <++= baseDirectory map { base =>
   (base / "lib" / "compile" ** "*.jar").classpath
}

unmanagedJars in Runtime <++= baseDirectory map { base =>
   (base / "lib" / "runtime" ** "*.jar").classpath
}

unmanagedJars in Test <++= baseDirectory map { base =>
   (base / "lib" / "test" ** "*.jar").classpath
}

excludedJars in assembly <<= (fullClasspath in assembly) map { cp => 
  cp filter {_.data.getName == "compile-0.1.0.jar"}
}

jarName in assembly := "foo.jar"

TaskKey[Unit]("check") <<= (crossTarget) map { (crossTarget) =>
  val process = sbt.Process("java", Seq("-jar", (crossTarget / "foo.jar").toString))
  val out = (process!!)
  if (out.trim != "hello") error("unexpected output: " + out)
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
