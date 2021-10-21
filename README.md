sbt-assembly
============

*Deploy über JARs. Restart processes.*

sbt-assembly is a sbt plugin originally ported from codahale's assembly-sbt, which I'm guessing was inspired by Maven's assembly plugin. The goal is simple: Create a über JAR of your project with all of its dependencies.

Requirements
------------

* sbt
* The burning desire to have a simple deploy procedure.

Reporting Issues & Contributing
-------------------------------

Before you email me, please read [Issue Reporting Guideline](CONTRIBUTING.md) carefully. Twice. (Don't email me)

Setup
-----

### Using Published Plugin

[![sbt-assembly Scala version support](https://index.scala-lang.org/sbt/sbt-assembly/sbt-assembly/latest-by-scala-version.svg?targetType=Sbt)](https://index.scala-lang.org/sbt/sbt-assembly/sbt-assembly)

Add sbt-assembly as a dependency in `project/plugins.sbt`:

```scala
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "x.y.z")
```
Starting in `sbt-assembly` `1.2.0`, sbt `0.13.x` has been deprecated.  Please use `1.1.0` if this is required.

Usage
-----

Since sbt-assembly is now an auto plugin that's triggered for all projects with `JvmPlugin`, it shouldn't require extra setup to include `assembly` task into your project.
See [migration guide](Migration.md) for details on how to upgrade from older sbt-assembly.

### Applying the plugin to multi-project build.sbt

For example, here's a multi-project `build.sbt`:

```scala
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.13.6"

lazy val app = (project in file("app"))
  .settings(
    assembly / mainClass := Some("com.example.Main"),
    // more settings here ...
  )

lazy val utils = (project in file("utils"))
  .settings(
    assembly / assemblyJarName := "utils.jar",
    // more settings here ...
  )
```

In the above example, both the `app` project and the `utils` project do not run tests during assembly. The `app` project sets a main class whereas the `utils` project sets the name of its jar file.

### assembly task

Now you'll have an awesome new `assembly` task which will compile your project,
run your tests, and then pack your class files and all your dependencies into a
single JAR file: `target/scala_X.X.X/projectname-assembly-X.X.X.jar`.

    > assembly

If you specify a `assembly / mainClass` in build.sbt (or just let it autodetect
one) then you'll end up with a fully executable JAR, ready to rock.

Here is the list of the keys you can rewire that are scoped to current subproject's `assembly` task:

    assemblyJarName               test                          mainClass
    assemblyOutputPath            assemblyOption                assembledMappings
    assembledMappings

And here is the list of the keys you can rewite that are scoped globally:

    assemblyAppendContentHash     assemblyCacheOutput           assemblyCacheUnzip
    assemblyExcludedJars          assemblyMergeStrategy         assemblyShadeRules

Keys scoped to the subproject should be placed in `.settings(...)` whereas the globally scoped keys can either be placed inside of `.settings(...)` or scoped using `ThisBuild / ` to be shared across multiple subprojects.

For example the name of the jar can be set as follows in build.sbt:

```scala
lazy val app = (project in file("app"))
  .settings(
    assembly / assemblyJarName := "something.jar",
    // more settings here ...
  )
```

To set an explicit main class,

```scala
lazy val app = (project in file("app"))
  .settings(
    assembly / mainClass := Some("com.example.Main"),
    // more settings here ...
  )
```

To run the test during assembly,

```scala
lazy val app = (project in file("app"))
  .settings(
    assembly / test := (Test / test).value,
    // more settings here ...
  )
```

Excluding an explicit main class from your assembly requires something a little bit different though

```
lazy val app = (project in file("app"))
  .settings(
    assembly / packageOptions ~= { pos =>
      pos.filterNot { po =>
        po.isInstanceOf[Package.MainClass]
      }
    },
    // more settings here ...
  )
```

### Merge Strategy

If multiple files share the same relative path (e.g. a resource named
`application.conf` in multiple dependency JARs), the default strategy is to
verify that all candidates have the same contents and error out otherwise.
This behavior can be configured on a per-path basis using either one
of the following built-in strategies or writing a custom one:

* `MergeStrategy.deduplicate` is the default described above
* `MergeStrategy.first` picks the first of the matching files in classpath order
* `MergeStrategy.last` picks the last one
* `MergeStrategy.singleOrError` bails out with an error message on conflict
* `MergeStrategy.concat` simply concatenates all matching files and includes the result
* `MergeStrategy.filterDistinctLines` also concatenates, but leaves out duplicates along the way
* `MergeStrategy.rename` renames the files originating from jar files
* `MergeStrategy.discard` simply discards matching files

The mapping of path names to merge strategies is done via the setting
`assemblyMergeStrategy` which can be augmented as follows:

```scala
ThisBuild / assemblyMergeStrategy := {
  case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
  case "application.conf"                            => MergeStrategy.concat
  case "unwanted.txt"                                => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}
```

**NOTE**:
- `ThisBuild / assemblyMergeStrategy` expects a function. You can't do `ThisBuild / assemblyMergeStrategy := MergeStrategy.first`!
- Some files must be discarded or renamed otherwise to avoid breaking the zip (due to duplicate file name) or the legal license. Delegate default handling to `(ThisBuild / assemblyMergeStrategy)` as the above pattern matching example.

By the way, the first case pattern in the above using `PathList(...)` is how you can pick `javax/servlet/*` from the first jar. If the default `MergeStrategy.deduplicate` is not working for you, that likely means you have multiple versions of some library pulled by your dependency graph. The real solution is to fix that dependency graph. You can work around it by `MergeStrategy.first` but don't be surprised when you see `ClassNotFoundException`.

Here is the default:

```scala
  val defaultMergeStrategy: String => MergeStrategy = {
    case x if Assembly.isConfigFile(x) =>
      MergeStrategy.concat
    case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
      MergeStrategy.rename
    case PathList("META-INF", xs @ _*) =>
      (xs map {_.toLowerCase}) match {
        case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
          MergeStrategy.discard
        case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
          MergeStrategy.discard
        case "plexus" :: xs =>
          MergeStrategy.discard
        case "services" :: xs =>
          MergeStrategy.filterDistinctLines
        case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
          MergeStrategy.filterDistinctLines
        case _ => MergeStrategy.deduplicate
      }
    case _ => MergeStrategy.deduplicate
  }
```

Custom `MergeStrategy`s can find out where a particular file comes
from using the `sourceOfFileForMerge` method on `sbtassembly.AssemblyUtils`,
which takes the temporary directory and one of the files passed into the
strategy as parameters.

#### Third Party Merge Strategy Plugins

Support for special-case merge strategies beyond the generic scope can be
provided by companion plugins, below is a non-exhaustive list:

* Log4j2 Plugin Caches (`Log4j2Plugins.dat`):
  <https://github.com/idio/sbt-assembly-log4j2>

### Shading

sbt-assembly can shade classes from your projects or from the library dependencies.
Backed by [Jar Jar Links](https://code.google.com/archive/p/jarjar/wikis/CommandLineDocs.wiki), bytecode transformation (via ASM) is used to change references to the renamed classes.

```scala
ThisBuild / assemblyShadeRules := Seq(
  ShadeRule.rename("org.apache.commons.io.**" -> "shadeio.@1").inAll
)
```

Here are the shade rules:

* `ShadeRule.rename("x.**" -> "y.@1", ...).inAll` This is the main rule.
* `ShadeRule.zap("a.b.c").inAll`
* `ShadeRule.keep("x.**").inAll`

The main `ShadeRule.rename` rule is used to rename classes. All references to the renamed classes will also be updated. If a class name is matched by more than one rule, only the first one will apply.
The `rename` rules takes a vararg of String pairs in `<pattern> -> <result>` format:

- `<pattern>` is a class name with optional wildcards. `**` will match against any valid class name substring. To match a single package component (by excluding `.` from the match), a single `*` may be used instead.
- `<result>` is a class name which can optionally reference the substrings matched by the wildcards. A numbered reference is available for every `*` or `**` in the `<pattern>`, starting from left to right: `@1`, `@2`, etc. A special `@0` reference contains the entire matched class name.

Instead of `.inAll`, call `.inProject` to match your project source, or call `.inLibrary("commons-io" % "commons-io" % "2.4", ...)` to match specific library dependencies. `inProject` and `inLibrary(...)` can be chained.

```scala
ThisBuild / assemblyShadeRules := Seq(
  ShadeRule.rename("org.apache.commons.io.**" -> "shadeio.@1").inLibrary("commons-io" % "commons-io" % "2.4", ...).inProject
)
```

The `ShadeRule.zap` rule causes any matched class to be removed from the resulting jar file. All zap rules are processed before renaming rules.

The `ShadeRule.keep` rule marks all matched classes as "roots". If any keep rules are defined all classes which are not reachable from the roots via dependency analysis are discarded when writing the output jar. This is the last step in the process, after renaming and zapping.

To see the verbose output for shading:

```scala
lazy val app = (project in file("app"))
  .settings(
    assembly / logLevel := Level.Debug
    // more settings here ...
  )
```

#### Scala libraries

Scala classes contain an annotation which, among other things, contain all symbols referenced in that class. As of sbt-assembly XXX the rename rules
will be applied to these annotations as well which makes it possible to compile or reflect against a shaded library.

This is currently limited to renaming packages. Renaming class names will not work and cause compiler errors when compiling against the shaded library.

Excluding JARs and files
------------------------

If you need to tell sbt-assembly to ignore JARs, you're probably doing it wrong.
assembly task grabs deps JARs from your project's classpath. Try fixing the classpath first.

### % "provided" configuration

If you're trying to exclude JAR files that are already part of the container (like Spark), consider scoping the dependent library to `"provided"` configuration:

```scala
libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "0.8.0-incubating" % "provided",
  "org.apache.hadoop" % "hadoop-client" % "2.0.0-cdh4.4.0" % "provided"
)
```

Maven defines ["provided"](http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope) as:

> This is much like `compile`, but indicates you expect the JDK or a container to provide the dependency at runtime. For example, when building a web application for the Java Enterprise Edition, you would set the dependency on the Servlet API and related Java EE APIs to scope `provided` because the web container provides those classes. This scope is only available on the compilation and test classpath, and is not transitive.

The dependency will be part of compilation and test, but excluded from the runtime. If you're using Spark and want to include "provided" dependencies back to `run`, [@douglaz](https://github.com/douglaz) has come up with a one-liner solution on StackOverflow [sbt: how can I add "provided" dependencies back to run/test tasks' classpath?](http://stackoverflow.com/a/21803413/3827):

```scala
Compile / run := Defaults.runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner).evaluated
```

### Exclude specific transitive deps

You might be thinking about excluding JAR files because of the merge conflicts. Merge conflict of `*.class` files indicate pathological classpath, often due to non-modular bundle JAR files or [SLF4J](http://www.slf4j.org/legacy.html), not the problem with assembly. Here's what happens when you try to create a über JAR with Spark included:

```
[error] (*:assembly) deduplicate: different file contents found in the following:
[error] /Users/foo/.ivy2/cache/org.eclipse.jetty.orbit/javax.servlet/orbits/javax.servlet-2.5.0.v201103041518.jar:javax/servlet/SingleThreadModel.class
[error] /Users/foo/.ivy2/cache/org.mortbay.jetty/servlet-api/jars/servlet-api-2.5-20081211.jar:javax/servlet/SingleThreadModel.class
```

In the above case two separate JAR files `javax.servlet-2.5.0.v201103041518.jar` and `servlet-api-2.5-20081211.jar` are defining `javax/servlet/SingleThreadModel.class`! Similarly also conflicts on [common-beanutils](http://commons.apache.org/proper/commons-beanutils/) and [EsotericSoftware/minlog](https://github.com/EsotericSoftware/minlog). Here's how to evict specific transitive deps:

```scala
libraryDependencies ++= Seq(
  ("org.apache.spark" %% "spark-core" % "0.8.0-incubating").
    exclude("org.mortbay.jetty", "servlet-api").
    exclude("commons-beanutils", "commons-beanutils-core").
    exclude("commons-collections", "commons-collections").
    exclude("commons-logging", "commons-logging").
    exclude("com.esotericsoftware.minlog", "minlog")
)
```

See sbt's [Exclude Transitive Dependencies](https://www.scala-sbt.org/release/docs/Library-Management.html#Exclude+Transitive+Dependencies) for more details.

Sometimes it takes a bit of detective work to figure out which transitive deps to exclude. Play! comes with `dist` task, so `assembly` is not needed, but suppose we wanted to run `assembly`. It brings in signpost-commonshttp4, which leads to commons-logging. This conflicts with jcl-over-slf4j, which re-implements the logging API. Since the deps are added via build.sbt and `playScalaSettings`, here's one way to work around it:

```scala
libraryDependencies ~= { _ map {
  case m if m.organization == "com.typesafe.play" =>
    m.exclude("commons-logging", "commons-logging").
      exclude("com.typesafe.play", "sbt-link")
  case m => m
}}
```

### Excluding specific files

To exclude specific files, customize merge strategy:

```scala
ThisBuild / assemblyMergeStrategy := {
  case PathList("about.html") => MergeStrategy.rename
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}
```

### Splitting your project and deps JARs

To make a JAR file containing only the external dependencies, type

    > assemblyPackageDependency

This is intended to be used with a JAR that only contains your project

```scala
lazy val app = (project in file("app"))
  .settings(
    assemblyPackageScala / assembleArtifact := false,
    assemblyPackageDependency / assembleArtifact := false,

    // or as follows
    assembly / assemblyOption ~= {
      _.withIncludeScala(false)
       .withIncludeDependency(false)
    },

    // more settings here ...
  )
```

NOTE: If you use [`-jar` option for `java`](http://docs.oracle.com/javase/7/docs/technotes/tools/solaris/java.html#jar), it will ignore `-cp`, so if you have multiple JAR files you have to use `-cp` and pass the main class: `java -cp "jar1.jar:jar2.jar" Main`

### Excluding Scala library JARs

To exclude Scala library (JARs that start with `scala-` and are included in the binary Scala distribution) to run with `scala` command,

```scala
lazy val app = (project in file("app"))
  .settings(
    assemblyPackageScala / assembleArtifact := false,

    // or as follows
    assembly / assemblyOption ~= {
      _.withIncludeScala(false)
    },

    // more settings here ...
  )
```

### assemblyExcludedJars

If all efforts fail, here's a way to exclude JAR files:

```scala
lazy val app = (project in file("app"))
  .settings(
    assembly / assemblyExcludedJars := {
      val cp = (assembly / fullClasspath).value
      cp filter {_.data.getName == "compile-0.1.0.jar"}
    },

    // more settings here ...
  )
```


### Unzip Caching

When assembling an über artifact, that has many library dependencies, the unzip process can be very IO intensive. These unzipped directories are very suitable for CI systems to persist in between job runs.

```scala
lazy val app = (project in file("app"))
  .settings(
    assemblyUnzipDirectory := Some(localCacheDirectory.value / "sbt-assembly" / "dependencies"),
    assemblyCacheUnzip := true, // this is the default setting
    assemblyCacheUseHardLinks := true, // this is experimental but will use a hard link between the files in assemblyUnzipDirectory to assemblyDirectory to avoid additional copy IO
    // more settings here ...
  )
```

To populate the assemblyUnzipDirectory without a full assembly:

    sbt assemblyCacheDependency 

Other Things
------------

### Content hash

You can also append SHA-1 fingerprint to the assembly file name, this may help you to determine whether it has changed and, for example, if it's necessary to deploy the dependencies,

```scala
ThisBuild / assemblyAppendContentHash := true

// or
lazy val app = (project in file("app"))
  .settings(
     assembly / assemblyOption ~= { _.withAppendContentHash(true) }
  )
```

### Caching

By default for performance reasons, the result of unzipping any dependency JAR files to disk is cached from run-to-run. This feature can be disabled by setting:

```scala
ThisBuild / assemblyCacheUnzip := false

// or
lazy val app = (project in file("app"))
  .settings(
     assembly / assemblyOption ~= { _.withCacheUnzip(false) }
  )
```

In addition the über JAR is cached so its timestamp changes only when the input changes. This feature requires checking the SHA-1 hash of all *.class files, and the hash of all dependency *.jar files. If there are a large number of class files, this could take a long time, although with hashing of jar files, rather than their contents, the speed has recently been [improved](https://github.com/sbt/sbt-assembly/issues/68). This feature can be disabled by setting:

```scala
ThisBuild / assemblyCacheOutput := false

// or
lazy val app = (project in file("app"))
  .settings(
     assembly / assemblyOption ~= { _.withCacheOutput(false) }
  )
```

### Prepending a launch script

Your can prepend a launch script to the über jar. This script will be a valid shell and batch script and will make the jar executable on Unix and Windows. If you enable the shebang the file will be detected as an executable under Linux but this will cause an error message to appear on Windows. On Windows just append a ".bat" to the files name to make it executable.

```scala
import sbtassembly.AssemblyPlugin.defaultUniversalScript

ThisBuild / assemblyPrependShellScript := = Some(defaultUniversalScript(shebang = false)))

lazy val app = (project in file("app"))
  .settings(
     assembly / assemblyJarName := s"${name.value}-${version.value}"
  )
```

This will prepend the following shell script to the jar.

```
(#!/usr/bin/env sh)
@ 2>/dev/null # 2>nul & echo off & goto BOF
:
exec java -jar $JAVA_OPTS "$0" "$@"
exit

:BOF
@echo off
java -jar %JAVA_OPTS% "%~dpnx0" %*
exit /B %errorlevel%
```

You can also choose to prepend just the shell script to the über jar as follows:

```scala
import sbtassembly.AssemblyPlugin.defaultShellScript

ThisBuild / assemblyPrependShellScript := Some(defaultShellScript)

lazy val app = (project in file("app"))
  .settings(
     assembly / assemblyJarName := s"${name.value}-${version.value}"
  )
```

### Publishing (Not Recommended)

Publishing über JARs out to the world is discouraged because non-modular JARs cause much sadness. One might think non-modularity is convenience but it quickly turns into a headache the moment your users step outside of Hello World example code. If you still wish to publish your assembled artifact along with the `publish` task
and all of the other artifacts, add an `assembly` classifier (or other):

```scala
assembly / artifact := {
  val art = (assembly / artifact).value
  art.withClassifier(Some("assembly"))
}

addArtifact(assembly / artifact, assembly)
```

### Q: Despite the concerned friends, I still want publish über JARs. What advice do you have?

You would likely need to set up a front business to lie about what dependencies you have in `pom.xml` and `ivy.xml`.
To do so, make a subproject for über JAR purpose only where you depend on the dependencies, and make a second cosmetic subproject that you use only for publishing purpose:

```scala
lazy val uberJar = project
  .enablePlugins(AssemblyPlugin)
  .settings(
    depend on the good stuff
    publish / skip := true
  )

lazy val cosmetic = project
  .settings(
    name := "shaded-something",
    // I am sober. no dependencies.
    Compile / packageBin := (uberJar / assembly).value
  )
```

License
-------

Published under The MIT License, see LICENSE

Copyright e.e d3si9n, LLC

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
