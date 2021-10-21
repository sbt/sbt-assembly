package sbtassembly

import sbt._
import Keys._
import com.eed3si9n.jarjarabrams

object AssemblyPlugin extends sbt.AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  object autoImport extends AssemblyKeys {
    val Assembly = sbtassembly.Assembly
    val MergeStrategy = sbtassembly.MergeStrategy
    val PathList = sbtassembly.PathList
    val baseAssemblySettings = AssemblyPlugin.baseAssemblySettings
    val ShadeRule = com.eed3si9n.jarjarabrams.ShadeRule
    implicit class RichShadePattern(pattern: jarjarabrams.ShadePattern) {
      def inLibrary(moduleId: ModuleID*): jarjarabrams.ShadeRule =
        pattern.inModuleCoordinates(moduleId.toVector
          .map(m => jarjarabrams.ModuleCoordinate(m.organization, m.name, m.revision)): _*)
    }
  }
  import autoImport.{ Assembly => _, baseAssemblySettings => _, _ }

  override lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    assemblyUnzipDirectory := None,
    assemblyMergeStrategy := MergeStrategy.defaultMergeStrategy,
    assemblyShadeRules := Nil,
    assemblyExcludedJars := Nil,
    assembleArtifact in packageBin := true,
    assembleArtifact in assemblyPackageScala := true,
    assembleArtifact in assemblyPackageDependency := true,
    assemblyAppendContentHash := false,
    assemblyCacheUnzip := true,
    assemblyCacheOutput := true,
    assemblyCacheUseHardLinks := false,
    assemblyPrependShellScript := None
  )

  override lazy val projectSettings: Seq[Def.Setting[_]] = assemblySettings

  // Compile-specific defaults
  lazy val assemblySettings: Seq[sbt.Def.Setting[_]] = baseAssemblySettings ++ Seq(
    packageOptions in assembly := {
      val os = (packageOptions in (Compile, packageBin)).value
      (mainClass in assembly).value map { s =>
        Package.MainClass(s) +: (os filterNot {_.isInstanceOf[Package.MainClass]})
      } getOrElse {os}
    },
    packageOptions in assemblyPackageScala      := (packageOptions in (Compile, packageBin)).value,
    packageOptions in assemblyPackageDependency := (packageOptions in (Compile, packageBin)).value
  )

  lazy val baseAssemblySettings: Seq[sbt.Def.Setting[_]] = (Seq(
    assembly                                       := Assembly.assemblyTask(assembly).value,
    assembledMappings in assembly                  := Assembly.assembledMappingsTask(assembly).value,
    assemblyPackageScala                           := Assembly.assemblyTask(assemblyPackageScala).value,
    assembledMappings in assemblyPackageScala      := Assembly.assembledMappingsTask(assemblyPackageScala).value,
    assemblyPackageDependency                      := Assembly.assemblyTask(assemblyPackageDependency).value,
    assembledMappings in assemblyPackageDependency := Assembly.assembledMappingsTask(assemblyPackageDependency).value,
    assemblyCacheDependency                        := Assembly.assemblyCacheDependencyTask(assemblyPackageDependency).value,

    // test
    test in assembly := { () },
    test in assemblyPackageScala := (test in assembly).value,
    test in assemblyPackageDependency := (test in assembly).value,

    // packageOptions not specific to Compile scope. see also assemblySettings
    packageOptions in assembly := {
      val os = (packageOptions in packageBin).value
      (mainClass in assembly).value map { s =>
        Package.MainClass(s) +: (os filterNot {_.isInstanceOf[Package.MainClass]})
      } getOrElse {os}
    },
    packageOptions in assemblyPackageScala      := (packageOptions in packageBin).value,
    packageOptions in assemblyPackageDependency := (packageOptions in packageBin).value,

    // outputPath
    assemblyOutputPath in assembly                  := { (target in assembly).value / (assemblyJarName in assembly).value },
    assemblyOutputPath in assemblyPackageScala      := { (target in assembly).value / (assemblyJarName in assemblyPackageScala).value },
    assemblyOutputPath in assemblyPackageDependency := { (target in assembly).value / (assemblyJarName in assemblyPackageDependency).value },
    target in assembly := crossTarget.value,

    assemblyJarName in assembly                   := ((assemblyJarName in assembly)                  or (assemblyDefaultJarName in assembly)).value,
    assemblyJarName in assemblyPackageScala       := ((assemblyJarName in assemblyPackageScala)      or (assemblyDefaultJarName in assemblyPackageScala)).value,
    assemblyJarName in assemblyPackageDependency  := ((assemblyJarName in assemblyPackageDependency) or (assemblyDefaultJarName in assemblyPackageDependency)).value,

    assemblyDefaultJarName in assemblyPackageScala      := { "scala-library-" + scalaVersion.value + "-assembly.jar" },
    assemblyDefaultJarName in assemblyPackageDependency := { name.value + "-assembly-" + version.value + "-deps.jar" },
    assemblyDefaultJarName in assembly                  := { name.value + "-assembly-" + version.value + ".jar" },

    mainClass in assembly := (mainClass or (mainClass in Runtime)).value,

    fullClasspath in assembly := (fullClasspath or (fullClasspath in Runtime)).value,

    externalDependencyClasspath in assembly := (externalDependencyClasspath or (externalDependencyClasspath in Runtime)).value
  ) ++ inTask(assembly)(assemblyOptionSettings)
    ++ inTask(assemblyPackageScala)(assemblyOptionSettings)
    ++ inTask(assemblyPackageDependency)(assemblyOptionSettings)
    ++ inTask(assemblyCacheDependency)(assemblyOptionSettings)
    ++ Seq(
    assemblyOption in assemblyPackageScala ~= {
      _.withIncludeBin(false)
        .withIncludeScala(true)
        .withIncludeDependency(false)
    },
    assemblyOption in assemblyPackageDependency ~= {
      _.withIncludeBin(false)
        .withIncludeScala(true)
        .withIncludeDependency(true)
    },
    assemblyOption in assemblyCacheDependency ~= {
      _.withIncludeBin(false)
        .withIncludeScala(true)
        .withIncludeDependency(true)
    },
  ))

  def assemblyOptionSettings: Seq[Setting[_]] = Seq(
    assemblyOption := {
      val s = streams.value
      AssemblyOption()
        .withAssemblyDirectory(s.cacheDirectory / "assembly")
        .withAssemblyUnzipDirectory(assemblyUnzipDirectory.value)
        .withIncludeBin((assembleArtifact in packageBin).value)
        .withIncludeScala((assembleArtifact in assemblyPackageScala).value)
        .withIncludeDependency((assembleArtifact in assemblyPackageDependency).value)
        .withMergeStrategy(assemblyMergeStrategy.value)
        .withExcludedJars(assemblyExcludedJars.value)
        .withExcludedFiles(Assembly.defaultExcludedFiles)
        .withCacheOutput(assemblyCacheOutput.value)
        .withCacheUnzip(assemblyCacheUnzip.value)
        .withCacheUseHardLinks(assemblyCacheUseHardLinks.value)
        .withAppendContentHash(assemblyAppendContentHash.value)
        .withPrependShellScript(assemblyPrependShellScript.value)
        .withMaxHashLength(assemblyMaxHashLength.?.value)
        .withShadeRules(assemblyShadeRules.value)
        .withScalaVersion(scalaVersion.value)
        .withLevel(logLevel.?.value.getOrElse(Level.Info))
    }
  )

  lazy val defaultShellScript: Seq[String] = defaultShellScript()

  def defaultShellScript(javaOpts: Seq[String] = Seq.empty): Seq[String] = {
    val javaOptsString = javaOpts.map(_ + " ").mkString
    Seq("#!/usr/bin/env sh", s"""exec java -jar $javaOptsString$$JAVA_OPTS "$$0" "$$@"""", "")
  }

  private def universalScript(shellCommands: String,
                              cmdCommands: String,
                              shebang: Boolean): String = {
    Seq(
      if (shebang) "#!/usr/bin/env sh" else "",
      "@ 2>/dev/null # 2>nul & echo off & goto BOF\r",
      ":",
      shellCommands.replaceAll("\r\n|\n", "\n"),
      "exit",
      Seq(
        "",
        ":BOF",
        cmdCommands.replaceAll("\r\n|\n", "\r\n"),
        "exit /B %errorlevel%",
        ""
      ).mkString("\r\n")
    ).filterNot(_.isEmpty).mkString("\n")
  }

  def defaultUniversalScript(javaOpts: Seq[String] = Seq.empty, shebang: Boolean = true): Seq[String] = {
    val javaOptsString = javaOpts.map(_ + " ").mkString
    Seq(universalScript(
      shellCommands = s"""exec java -jar $javaOptsString$$JAVA_OPTS "$$0" "$$@"""",
      cmdCommands = s"""java -jar $javaOptsString%JAVA_OPTS% "%~dpnx0" %*""",
      shebang = shebang
    ))
  }
}
