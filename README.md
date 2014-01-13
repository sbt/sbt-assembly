sbt-assembly
============

*Deploy fat JARs. Restart processes.*

sbt-assembly is a sbt plugin originally ported from codahale's assembly-sbt, which I'm guessing was inspired by Maven's assembly plugin. The goal is simple: Create a fat JAR of your project with all of its dependencies.

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

For sbt 0.13 add sbt-assembly as a dependency in `project/assembly.sbt`:

```scala
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.10.2")
```

For sbt 0.12, see [sbt-assemlby 0.9.2](https://github.com/sbt/sbt-assembly/tree/0.9.2).

### Using Source Dependency

Alternately, you can have sbt checkout and build the plugin's source from version control.

Specify sbt-assembly.git as a dependency in `project/project/build.scala`:

```scala
import sbt._

object Plugins extends Build {
  lazy val root = Project("root", file(".")) dependsOn(
    uri("git://github.com/sbt/sbt-assembly.git#XX") // where XX is tag or sha1
  )
}
```

(You may need to check this project's tags to see what the most recent release is.)

Usage
-----

### Applying the Plugin to a Project (Adding the `assembly` Task)

First, make sure that you've added the plugin to your build (either the published
builds or source from Git).


Put `assembly.sbt` at the root directory:

```scala
import AssemblyKeys._ // put this at the top of the file

assemblySettings

// your assembly settings here
```

If you are using multi-project `build.sbt`:

```scala
import AssemblyKeys._

lazy val buildSettings = Seq(
  version := "0.1-SNAPSHOT",
  organization := "com.example",
  scalaVersion := "2.10.1"
)

val app = (project in file("app")).
  settings(buildSettings: _*).
  settings(assemblySettings: _*).
  settings(
    // your settings here
  )
```

Now you'll have an awesome new `assembly` task which will compile your project,
run your tests, and then pack your class files and all your dependencies into a
single JAR file: `target/scala_X.X.X/projectname-assembly-X.X.X.jar`.

    > assembly

If you specify a `mainClass in assembly` in build.sbt (or just let it autodetect
one) then you'll end up with a fully executable JAR, ready to rock.

Here is the list of the keys you can rewire for `assembly` task. 

**NOTE**: Any customization must be written after `assemblySettings`.

    jarName                       test                          mainClass
    outputPath                    mergeStrategy                 assemblyOption
    excludedJars                  assembledMappings

For example the name of the jar can be set as follows in build.sbt:

```scala
jarName in assembly := "something.jar"
```

To skip the test during assembly,

```scala
test in assembly := {}
```

To set an explicit main class,

```scala
mainClass in assembly := Some("com.example.Main")
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
`assembly-merge-strategy` which can be augmented as follows:

```scala
mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
    case "application.conf" => MergeStrategy.concat
    case "unwanted.txt"     => MergeStrategy.discard
    case x => old(x)
  }
}
```

**NOTE**:
- `mergeStrategy in assembly` expects a function, you can't do `mergeStrategy in assembly := MergeStrategy.first`!
- Some files must be discarded or renamed otherwise, you're breaking the zip or legal license. Use the pattern above.

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

Excluding JARs and files
------------------------

If you need to tell sbt-assembly to ignore JARs, you're probably doing it wrong.
assembly task grabs deps JARs from your project's classpath. Try fixing the classpath first.

### % "provided" configuration

If you're trying to exclude JAR files that are already part of the container (like Spark), consider scoping the dependent library to `"provided"` configuration:

```scala
libraryDependencies += Seq(
  "org.apache.spark" %% "spark-core" % "0.8.0-incubating" % "provided",
  "org.apache.hadoop" % "hadoop-client" % "2.0.0-cdh4.4.0" % "provided"
)
```

Maven defines ["provided"](http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope) as:

> This is much like `compile`, but indicates you expect the JDK or a container to provide the dependency at runtime. For example, when building a web application for the Java Enterprise Edition, you would set the dependency on the Servlet API and related Java EE APIs to scope `provided` because the web container provides those classes. This scope is only available on the compilation and test classpath, and is not transitive.

The dependency will be part of compilation and test, but excluded from the runtime.

### Exclude specific transitive deps

You might be thinking about exluding JAR files because of the merge conlifcts. Merge conflict of `*.class` files indicate pathological classpath, often due to non-modular bundle JAR files or [SLF4J](http://www.slf4j.org/legacy.html), not the problem with assembly. Here's what happens when you try to create a fat JAR with Spark included:

```
[error] (*:assembly) deduplicate: different file contents found in the following:
[error] /Users/foo/.ivy2/cache/org.eclipse.jetty.orbit/javax.servlet/orbits/javax.servlet-2.5.0.v201103041518.jar:javax/servlet/SingleThreadModel.class
[error] /Users/foo/.ivy2/cache/org.mortbay.jetty/servlet-api/jars/servlet-api-2.5-20081211.jar:javax/servlet/SingleThreadModel.class
```

In the above case two separate JAR files `javax.servlet-2.5.0.v201103041518.jar` and `servlet-api-2.5-20081211.jar` are defining `javax/servlet/SingleThreadModel.class`! Similarly also conlifcts on [common-beanutils](http://commons.apache.org/proper/commons-beanutils/) and [EsotericSoftware/minlog](https://github.com/EsotericSoftware/minlog). Here's how to evict specific transitive deps:

```scala
libraryDependencies ++= Seq(
  ("org.apache.spark" %% "spark-core" % "0.8.0-incubating").
    exclude("org.mortbay.jetty", "servlet-api").
    exclude("commons-beanutils", "commons-beanutils-core").
    exclude("commons-collections", "commons-collections").
    exclude("commons-collections", "commons-collections").
    exclude("com.esotericsoftware.minlog", "minlog")
)
```

See sbt's [Exclude Transitive Dependencies](http://www.scala-sbt.org/release/docs/Detailed-Topics/Library-Management.html#exclude-transitive-dependencies) for more details.

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
mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("about.html") => MergeStrategy.rename
    case x => old(x)
  }
}
```

### Excluding Scala library, your project, or deps JARs

To exclude Scala library,

```scala
assemblyOption in assembly ~= { _.copy(includeScala = false) }
```

To exclude the class files from the main sources,

```scala
assemblyOption in assembly ~= { _.copy(includeBin = false) }
```

To make a JAR file containing only the dependencies, type

    > assemblyPackageDependency

NOTE: If you use [`-jar` option for `java`](http://docs.oracle.com/javase/7/docs/technotes/tools/solaris/java.html#jar), it will ignore `-cp`, so if you have multiple JAR files you have to use `-cp` and pass the main class: `java -cp "jar1.jar:jar2.jar" Main`

### excludedJars

If all efforts fail, here's a way to exclude JAR files:

```scala
excludedJars in assembly <<= (fullClasspath in assembly) map { cp => 
  cp filter {_.data.getName == "compile-0.1.0.jar"}
}
```

Other Things
------------

### Content hash

You can also append SHA-1 fingerprint to the assembly file name, this may help you to determine whether it has changed and, for example, if it's necessary to deploy the dependencies,

```scala
assemblyOption in packageDependency ~= { _.copy(appendContentHash = true) }
```

### Caching

By default for performance reasons, the result of unzipping any dependency JAR files to disk is cached from run-to-run. This feature can be disabled by setting:

```scala
assemblyOption in assembly ~= { _.copy(cacheUnzip = false) }
```

In addition the fat JAR is cached so its timestamp changes only when the input changes. This feature requires checking the SHA-1 hash of all *.class files, and the hash of all dependency *.jar files. If there are a large number of class files, this could take a long time, although with hashing of jar files, rather than their contents, the speed has recently been [improved](https://github.com/sbt/sbt-assembly/issues/68). This feature can be disabled by setting:

```scala
assemblyOption in assembly ~= { _.copy(cacheOutput = false) }
```

### Publishing (Not Recommended)

Publishing fat JARs out to the world is discouraged because non-modular JARs cause much sadness. One might think non-modularity is convenience but it quickly turns into a headache the moment your users step outside of Hello World example code. If you still wish to publish your assembled artifact along with the `publish` task
and all of the other artifacts, add an `assembly` classifier (or other):

```scala
artifact in (Compile, assembly) ~= { art =>
  art.copy(`classifier` = Some("assembly"))
}

addArtifact(artifact in (Compile, assembly), assembly)
```

### Prepending Shebang

You can prepend shell script to the fat jar as follows:

```scala
assemblyOption in assembly ~= { _.copy(prependShellScript = Some(defaultShellScript)) }

jarName in assembly := { s"${name.value}-${version.value}" }
```

This will prepend the following shell script to the jar.

```
#!/usr/bin/env sh
exec java -jar "$0" "$@"
```

License
-------

Copyright (c) 2010-2013 e.e d3si9n, Coda Hale

Published under The MIT License, see LICENSE
