import AssemblyKeys._

name := "foo"

version := "0.1"

scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0"
)

assemblySettings

assemblyOption in assembly ~= { _.copy(includeScala = false, includeDependency = false) }

assemblyOption in assembly ~= { _.copy(appendContentHash = true) }

assemblyOption in packageDependency ~= { _.copy(appendContentHash = true) }

TaskKey[Unit]("check") <<= (crossTarget) map { (crossTarget) =>
  val process = sbt.Process("java", Seq("-cp", 
    (crossTarget / "foo-assembly-0.1-7a4ebf373b385ed1badbab93d52cffdfc4587c04.jar").toString +
    java.io.File.pathSeparator +
    (crossTarget / "foo-assembly-0.1-deps-1aa2cc229f2e93446713bf8d1c6efc1e6ddab0fe.jar").toString,
    "Main"))
  val out = (process!!)
  if (out.trim != "hello") error("unexpected output: " + out)
  ()
}
