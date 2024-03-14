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
ThisBuild / scalaVersion := "2.13.11"

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
    assemblyOutputPath            assemblyOption

And here is the list of the keys you can rewite that are scoped globally:

    assemblyAppendContentHash     assemblyCacheOutput           assemblyShadeRules
    assemblyExcludedJars          assemblyMergeStrategy         assemblyRepeatableBuild

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

```scala
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
* `MergeStrategy.concat` simply concatenates all matching files and includes the result. There is also an overload that accepts a line separator for formatting the result
* `MergeStrategy.filterDistinctLines` also concatenates, but leaves out duplicates along the way. There is also an overload that accepts a `Charset` for reading the lines
* `MergeStrategy.rename` renames the files originating from jar files
* `MergeStrategy.discard` simply discards matching files
* `MergeStrategy.preferProject` will choose the first project file over library files if present. Otherwise, it works like `MergeStrategy.first`

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
- Actually, a merge strategy serves two purposes:
  * To merge conflicting files
  * To transform a single file (despite the naming), such as in the case of a `MergeStrategy.rename`. Sometimes, the transformation is a pass-through, as in the case of a `MergeStrategy.deduplicate` if there are no conflicts on a `target` path.
- `ThisBuild / assemblyMergeStrategy` expects a function. You can't do `ThisBuild / assemblyMergeStrategy := MergeStrategy.first`!
- Some files must be discarded or renamed otherwise to avoid breaking the zip (due to duplicate file name) or the legal license. Delegate default handling to `(ThisBuild / assemblyMergeStrategy)` as the above pattern matching example.
- Renames are processed first, since renamed file targets might match more merge patterns afterwards. By default, LICENSEs and READMEs are renamed before applying every other merge strategy. If you need a custom logic for renaming, create a new rename merge strategy so it is processsed first, along with the custom logic. See how to create custom `MergeStrategy`s in a later section of this README.
- There is an edge case that may occasionally fail. If a project has a file that has the same relative path as a directory to be written, an error notification will be written to the console as shown below. To resolve this, create a shade rule or a new merge strategy.

  ```bash
    [error] Files to be written at 'shadeio' have the same name as directories to be written:
    [error]   Jar name = commons-io-2.4.jar, jar org = commons-io, entry target = shadeio/input/Tailer.class (from original source = org/apache/commons/io/input/Tailer.class)
    [error]   Project name = foo, target = shadeio
  ```

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

#### Creating a custom Merge Strategy (since 2.0.0)
Custom merge strategies can be plugged-in to the `assemblyMergeStrategy` function, for example:

```scala 
...
ThisBuild / assemblyMergeStrategy := {
  case "matching-file" => CustomMergeStrategy("my-custom-merge-strat") { conflicts =>
    // NB! same as MergeStrategy.discard
    Right(Vector.empty)
  }
  case x   =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}
...
```

The `CustomMergeStrategy` accepts a `name` and a `notifyIfGTE` that affects how the result is reported in the logs.
Please see the scaladoc for more details.

Finally, to perform the actual merge/transformation logic, a function has to be provided. The function acceptsa `Vector` of `Dependency`, where you can access the `target` of type `String` and the byte payload of type `LazyInputStream`, which is just a type alias for `() => InputStream`.

The input `Dependency` also has two subtypes that you can pattern match on:
  - `Project` represents an internal/project dependency
  - `Library` represents an external/library dependency that also contains the `ModuleCoordinate` (jar org, name and version) it originated from

To create a merge result, a `Vector` of `JarEntry` must be returned wrapped in an `Either.Right`, or empty to discard these conflicts from the final jar.
`JarEntry` only has two fields, a `target` of type `String` and the byte payload of type lazy `InputStream`.

To fail the assembly, return an `Either.Left` with an error message.

There is also a factory specifically for renames, so it gets processed first along with the built-in rename merge strategy, before other merge strategies, as mentioned in a previous section. It accepts a function `Dependency -> String`, so the `Dependency` can be inspected and a new `target` path returned.

Here is an example that appends a `String` to the original `target` path of the matched file.

```scala
...
case "matching-file" =>
  import sbtassembly.Assembly.{Project, Library}
  CustomMergeStrategy.rename {
    case dependency@(_: Project) => dependency.target + "_from_project"
    case dependency@(_: Library) => dependency.target + "_from_library"
  }
...
```

For more information/examples, see the scaladoc/source code in `sbtassembly.Assembly` and `sbtassembly.MergeStrategy`.

**NOTE**:
- The `name` parameter will be distinguished from a built-in strategy. For example, the `name`=`First` will execute its custom logic along with the built-in `MergeStrategy.first`. They cannot cancel/override one another. In fact, the custom merge strategy will be logged as `First (Custom)` for clarity.
- However, you should still choose a unique `name` for a custom merge strategy within the build. Even if all built-in and custom merge strategies are guaranteed to execute if they match a pattern regardless of their `name`s, similarly-named custom merge strategies will have their log reports joined. YMMV, but you are encouraged to **avoid duplicate names**.

#### Third Party Merge Strategy Plugins

Support for special-case merge strategies beyond the generic scope can be
provided by companion plugins, below is a non-exhaustive list:

* [Log4j2 Plugin Caches](https://github.com/mpollmeier/sbt-assembly-log4j2): merge `Log4j2Plugins.dat` binaries

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

Caching is implemented by checking all the input dependencies (class and jar files)' latest timestamp and some configuration changes from the build file.

In addition the über JAR is cached so its timestamp changes only when the input changes.

To disable caching:

```scala
ThisBuild / assemblyCacheOutput := false

// or
lazy val app = (project in file("app"))
  .settings(
     assembly / assemblyOption ~= { _.withCacheOutput(false) }
  )
```

**NOTE**:
- Unfortunately, using a custom `MergeStrategy` other than `rename` will create a function in which the plugin cannot predict
   the outcome. This custom function must always be executed if it matches a `PathList` pattern, and thus, **will disable caching**.

#### Jar assembly performance

By default, the setting key `assemblyRepeatableBuild` is set to `true`. This ensures that the jar entries are assembled in a specific order, resulting in a consistent hash for the jar.

There is actually a performance improvement to be gained if this setting is set to `false`, since jar entries will now be assembled in parallel. The trade-off is, the jar will not have a consistent hash, and thus, caching will not work.

To set the repeatable build to false:

```scala 
ThisBuild / assemblyRepeatableBuild := false
```

If a repeatable build/consistent jar is not of much importance, one may avail of this feature for improved performance, especially for large projects.

### Prepending a launch script

Your can prepend a launch script to the über jar. This script will be a valid shell and batch script and will make the jar executable on Unix and Windows. If you enable the shebang the file will be detected as an executable under Linux but this will cause an error message to appear on Windows. On Windows just append a ".bat" to the files name to make it executable.

```scala
import sbtassembly.AssemblyPlugin.defaultUniversalScript

ThisBuild / assemblyPrependShellScript := Some(defaultUniversalScript(shebang = false))

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

We discourage you from publishing non-shaded über JARs beyond deployment. The moment your über JAR is used as a library, it becomes a **parasitized über JAR**, bringing in parasite libraries that can not be excluded or resolved. One might think non-modularity is convenience, but it turns into others' headache down the road.

Here are some example of parasitized über JARs:

- hive-exec 2.3.9 and 3.1.3 contain com.google.common, com.google.protobuf, io.airlift, org.apache.parquet, org.codehaus.jackson, org.joda.time, etc.

### Q: Despite the concerned friends, I still want publish über JARs. What advice do you have?

Shade everything. Next, you would likely need to set up a front business to lie about what dependencies you have in `pom.xml` and `ivy.xml`.
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
