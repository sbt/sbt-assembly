import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.{LogEvent => Log4JLogEvent, _}
import org.apache.logging.log4j.core.Filter.Result
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.filter.LevelRangeFilter
import org.apache.logging.log4j.core.layout.PatternLayout

lazy val tempUnzipDir = IO.createTemporaryDirectory

lazy val root = (project in file(".")).
  settings(
    version := "0.1",
    scalaVersion := "2.11.12",
    libraryDependencies += "commons-io" % "commons-io" % "2.4",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "0.9.29" % "runtime",
    assembly / assemblyShadeRules := Seq(
      ShadeRule
        .rename("org.apache.commons.io.**" -> "shadeio.@1")
        .inLibrary("commons-io" % "commons-io" % "2.4")
        .inProject
    ),
    assemblyUnzipDirectory := Some(tempUnzipDir),
    assemblyCacheUseHardLinks := true,
    logLevel := sbt.Level.Info,
    logBuffered := false,
    assembly / assemblyJarName := "foo.jar",
    TaskKey[Unit]("checkunzip") := {
      val opt = (assembly / assemblyOption).value
      val assemblyDir = opt.assemblyDirectory.get
      val assemblyUnzipDir = opt.assemblyUnzipDirectory.get
      val preShadePath = "org.apache.commons.io".replace('.', java.io.File.separatorChar)
      val postShadePath = "shadeio"

      val sources = PathFinder(assemblyUnzipDir).allPaths pair Path.rebase(assemblyUnzipDir, assemblyDir)
      val ioSources = sources.filter{ case (unzip, _) => unzip.getAbsolutePath.contains(preShadePath) && unzip.isFile }

      assert(ioSources.nonEmpty)
      sources.map{ _._1 }.foreach{ f => assert(f.exists) }

      ioSources.foreach { case (unzipFile, origOutFile) =>
        val outputFile = new java.io.File(
          origOutFile
          .getAbsolutePath
          .toString
          .replace(preShadePath, postShadePath)
        )

        assert(unzipFile.exists)
        assert(outputFile.exists)
        assert(getHashString(unzipFile) != getHashString(outputFile))
      }
      ()
    },
    TaskKey[Unit]("cleanunzip") := {
      IO.delete(tempUnzipDir)
    }
  )

def getHashString(file: java.io.File): String = {
  import java.security.MessageDigest
  MessageDigest
    .getInstance("SHA-1")
    .digest(IO.readBytes(file))
    .map( b => "%02x".format(b) )
    .mkString
}
