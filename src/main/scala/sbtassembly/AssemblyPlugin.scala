package sbtassembly

import com.eed3si9n.jarjarabrams
import sbt.Keys._
import sbt.{ given, * }
import PluginCompat.*

object AssemblyPlugin extends sbt.AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  object autoImport extends AssemblyKeys {
    val Assembly = sbtassembly.Assembly
    val MergeStrategy = sbtassembly.MergeStrategy
    val JarEntry = sbtassembly.Assembly.JarEntry
    val CustomMergeStrategy = sbtassembly.CustomMergeStrategy
    val PathList = sbtassembly.PathList
    val baseAssemblySettings = AssemblyPlugin.baseAssemblySettings
    val ShadeRule = com.eed3si9n.jarjarabrams.ShadeRule
    implicit class RichShadePattern(pattern: jarjarabrams.ShadePattern) {
      def inLibrary(moduleId: ModuleID*): jarjarabrams.ShadeRule =
        pattern.inModuleCoordinates(
          moduleId.toVector
            .map(m => jarjarabrams.ModuleCoordinate(m.organization, m.name, m.revision)): _*
        )
    }
  }
  import autoImport.{ baseAssemblySettings => _, Assembly => _, _ }

  override lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    assemblyMergeStrategy := MergeStrategy.defaultMergeStrategy,
    assemblyShadeRules := Nil,
    assemblyExcludedJars := Nil,
    packageBin / assembleArtifact := true,
    assemblyPackageScala / assembleArtifact := true,
    assemblyPackageDependency / assembleArtifact := true,
    assemblyAppendContentHash := false,
    assemblyPrependShellScript := None,
    assemblyCacheOutput := true,
    assemblyRepeatableBuild := true,
    concurrentRestrictions += Tags.limit(PluginCompat.assemblyTag, 1),
    assemblyMetaBuildHash := {
      val extracted = Project.extract(state.value)
      val metaBuildClasses = (for {
        unit <- extracted.structure.units.values
        sbtFiles <- unit.unit.definitions.dslDefinitions.sbtFiles
        generated <- sbtFiles.generated
      } yield PluginCompat.toFile(generated)).toVector.distinct
      val metaBuildClasspath = (for {
        unit <- extracted.structure.units.values
        cp <- unit.classpath
      } yield PluginCompat.toFile(cp)).toVector.distinct
      val hashes = (metaBuildClasses ++ metaBuildClasspath)
        .map(Assembly.hash).sorted
      hashes.mkString("")
    },
  )

  override lazy val projectSettings: Seq[Def.Setting[_]] = assemblySettings

  // Compile-specific defaults
  def assemblySettings: Seq[sbt.Def.Setting[_]] = baseAssemblySettings ++ Seq(
    assembly / packageOptions := {
      val os = (Compile / packageBin / packageOptions).value
      (assembly / mainClass).value map { s =>
        Package.MainClass(s) +: (os filterNot { _.isInstanceOf[MainClass] })
      } getOrElse os
    },
    assemblyPackageScala / packageOptions := (Compile / packageBin / packageOptions).value,
    assemblyPackageDependency / packageOptions := (Compile / packageBin / packageOptions).value
  )

  def baseAssemblySettings: Seq[sbt.Def.Setting[_]] = (Seq(
    assembly := PluginCompat.assemblyTask(assembly)(Assembly.assemble).value,
    assemblyPackageScala := PluginCompat.assemblyTask(assemblyPackageScala)(Assembly.assemble).value,
    assemblyPackageDependency := PluginCompat.assemblyTask(assemblyPackageDependency)(Assembly.assemble).value,

    // test
    assembly / test := {},
    assemblyPackageScala / test := (assembly / test).value,
    assemblyPackageDependency / test := (assembly / test).value,

    // packageOptions not specific to Compile scope. see also assemblySettings
    assembly / packageOptions := {
      val os = (packageBin / packageOptions).value
      (assembly / mainClass).value map { s =>
        Package.MainClass(s) +: (os filterNot { _.isInstanceOf[MainClass] })
      } getOrElse os
    },
    assemblyPackageScala / packageOptions := (packageBin / packageOptions).value,
    assemblyPackageDependency / packageOptions := (packageBin / packageOptions).value,

    // outputPath
    assembly / target := crossTarget.value,
    assembly / assemblyOutputPath := (assembly / target).value / (assembly / assemblyJarName).value,
    assemblyPackageScala / assemblyOutputPath := (assembly / target).value / (assemblyPackageScala / assemblyJarName).value,
    assemblyPackageDependency / assemblyOutputPath := (assembly / target).value / (assemblyPackageDependency / assemblyJarName).value,
    assembly / assemblyJarName := ((assembly / assemblyJarName) or (assembly / assemblyDefaultJarName)).value,
    assemblyPackageScala / assemblyJarName := ((assemblyPackageScala / assemblyJarName) or (assemblyPackageScala / assemblyDefaultJarName)).value,
    assemblyPackageDependency / assemblyJarName := ((assemblyPackageDependency / assemblyJarName) or (assemblyPackageDependency / assemblyDefaultJarName)).value,
    assemblyPackageScala / assemblyDefaultJarName := "scala-library-" + scalaVersion.value + "-assembly.jar",
    assemblyPackageDependency / assemblyDefaultJarName := name.value + "-assembly-" + version.value + "-deps.jar",
    assembly / assemblyDefaultJarName := name.value + "-assembly-" + version.value + ".jar",
    assembly / mainClass := (mainClass or (Runtime / mainClass)).value,
    assembly / fullClasspath := (fullClasspath or (Runtime / fullClasspath)).value,
    assembly / externalDependencyClasspath := (externalDependencyClasspath or (Runtime / externalDependencyClasspath)).value
  ) ++ inTask(assembly)(assemblyOptionSettings)
    ++ inTask(assemblyPackageScala)(assemblyOptionSettings)
    ++ inTask(assemblyPackageDependency)(assemblyOptionSettings)
    ++ Seq(
      assemblyPackageScala / assemblyOption ~= {
        _.withIncludeBin(false)
          .withIncludeScala(true)
          .withIncludeDependency(false)
      },
      assemblyPackageDependency / assemblyOption ~= {
        _.withIncludeBin(false)
          .withIncludeScala(true)
          .withIncludeDependency(true)
      }
    ))

  def assemblyOptionSettings: Seq[Setting[_]] = Seq(
    assemblyOption := {
      val s = streams.value
      val sr = assemblyShadeRules.value
      if (sr.nonEmpty && exportJars.value) {
        sys.error("exportJars must be set to false for the shading to work")
      }
      AssemblyOption()
        .withIncludeBin((packageBin / assembleArtifact).value)
        .withIncludeScala((assemblyPackageScala / assembleArtifact).value)
        .withIncludeDependency((assemblyPackageDependency / assembleArtifact).value)
        .withExcludedJars(assemblyExcludedJars.value)
        .withCacheOutput(assemblyCacheOutput.value)
        .withAppendContentHash(assemblyAppendContentHash.value)
        .withPrependShellScript(assemblyPrependShellScript.value)
        .withMaxHashLength(assemblyMaxHashLength.?.value)
        .withShadeRules(sr)
        .withScalaVersion(scalaVersion.value)
        .withLevel(logLevel.?.value.getOrElse(Level.Info))
        .withRepeatableBuild(assemblyRepeatableBuild.value)
    }
  )

  lazy val defaultShellScript: Seq[String] = defaultShellScript()

  def defaultShellScript(javaOpts: Seq[String] = Seq.empty): Seq[String] = {
    val javaOptsString = javaOpts.map(_ + " ").mkString
    Seq("#!/usr/bin/env sh", s"""exec java -jar $javaOptsString$$JAVA_OPTS "$$0" "$$@"""", "")
  }

  private def universalScript(shellCommands: String, cmdCommands: String, shebang: Boolean): String =
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

  def defaultUniversalScript(javaOpts: Seq[String] = Seq.empty, shebang: Boolean = true): Seq[String] = {
    val javaOptsString = javaOpts.map(_ + " ").mkString
    Seq(
      universalScript(
        shellCommands = s"""exec java -jar $javaOptsString$$JAVA_OPTS "$$0" "$$@"""",
        cmdCommands = s"""java -jar $javaOptsString%JAVA_OPTS% "%~dpnx0" %*""",
        shebang = shebang
      )
    )
  }
}
