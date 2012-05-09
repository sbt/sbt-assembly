sbt-assembly
============

*Deploy fat JARs. Restart processes.*

sbt-assembly is a sbt 0.10+ port of an awesome sbt plugin by codahale:

> assembly-sbt is a [simple-build-tool](http://code.google.com/p/simple-build-tool/)
plugin for building a single JAR file of your project which includes all of its
dependencies, allowing to deploy the damn thing as a single file without dicking
around with shell scripts and lib directories or, worse, welding your
configuration to your deployable in the form of a WAR file.

Requirements
------------

* Simple Build Tool
* The burning desire to have a simple deploy procedure.

Latest
------

For sbt 0.11.3, add sbt-assembly as a dependency in `project/plugins.sbt`:

```scala
resolvers += Resolver.url("artifactory", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.8.1")
```

For sbt 0.12.0-M2, add the following to `project/plugins.sbt`:

```
libraryDependencies += Defaults.sbtPluginExtra("com.eed3si9n" % "sbt-assembly" % "0.7.4", "0.12.0-M2", "2.9.1")
```

How To Use
----------

Or, specify sbt-assembly.git as a dependency in `project/plugins/project/build.scala`:

```scala
import sbt._

object Plugins extends Build {
  lazy val root = Project("root", file(".")) dependsOn(
    uri("git://github.com/sbt/sbt-assembly.git#XX") // where XX is branch
  )
}
```

(You may need to check this project's tags to see what the most recent release
is. I'm notoriously crap about updating the version numbers in my READMEs.)

Then, add the following in your `build.sbt`:

```scala
import AssemblyKeys._ // put this at the top of the file

assemblySettings
```

or, for full configuration:

```scala
import sbtassembly.Plugin._
import AssemblyKeys._

lazy val sub = Project("sub", file("sub"),
  settings = buildSettings ++ assemblySettings ++
             Seq( // your settings here
             ))
```

Now you'll have an awesome new `assembly` task which will compile your project,
run your tests, and then pack your class files and all your dependencies into a
single JAR file: `target/scala_X.X.X/projectname-assembly-X.X.X.jar`.

    > assembly

If you specify a `mainClass in assembly` in build.sbt (or just let it autodetect
one) then you'll end up with a fully executable JAR, ready to rock.

Here is the list of the keys you can rewire for `assembly` task.

    target                        assembly-jar-name             test
    assembly-option               main-class                    full-classpath
    dependency-classpath          assembly-excluded-files       assembly-excluded-jars

For example the name of the jar can be set as follows in build.sbt:

```scala
jarName in assembly := "something.jar"
```

To skip the test during assembly,

```scala
test in assembly := {}
```

To exclude Scala library,

```scala
assembleArtifact in packageScala := false
```

To exclude the class files from the main sources,

```scala
assembleArtifact in packageBin := false
```

To exclude some jar file, first consider using `"provided"` dependency. The dependency will be part of compilation, but excluded from the runtime. Next, try creating a custom configuration that describes your classpath. If all efforts fail, here's a way to exclude jars:

```scala
excludedJars in assembly <<= (fullClasspath in assembly) map { cp => 
  cp filter {_.data.getName == "compile-0.1.0.jar"}
}
```

To exclude some class file,

```scala
excludedFiles in assembly := { (bases: Seq[File]) =>
  bases flatMap { base =>
    (base / "META-INF" * "*").get collect {
      case f if f.getName == "something" => f
      case f if f.getName.toLowerCase == "license" => f
      case f if f.getName.toLowerCase == "manifest.mf" => f
    }
  }}
```

To make a jar containing only the dependencies, type

    > assembly-package-dependency

To set an explicit main class,

```scala
mainClass in assembly := Some("com.example.Main")
```

What about Conflicts?
---------------------

If multiple files share the same relative path (e.g. a resource named
`application.conf` in multiple dependency JARs), the default strategy is to
verify that all candidates have the same contents and error out otherwise.
This behavior can be configured on a per-path basis using either one
of the following built-in strategies or writing a custom one:

* `MergeStrategy.first` picks the first of the conflicting files in classpath order
* `MergeStrategy.last` picks the last one
* `MergeStrategy.error` bails out with an error message
* `MergeStrategy.deduplicate` is the default described above
* `MergeStrategy.concat` simply concatenates all conflicting files and includes the result
* `MergeStrategy.filterDistinctLines` also concatenates, but leaves out duplicates along the way

The mapping of path names to merge strategies is done via the setting
`assembly-merge-strategy` which can be augmented like so:

```
mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case "application.conf" => MergeStrategy.concat
    case x => old(x)
  }
}
```

where the default is to

* `concat` "reference.conf",
* `filterDistinctLines` everything below META-INF/services, META-INF/spring.schemas, META-INF/spring.handlers and
* `deduplicate` the rest.

Custom `MergeStrategy`s can find out where a particular file comes
from using the `sourceOfFileForMerge` method on `sbtassembly.AssemblyUtils`,
which takes the temporary directory and one of the files passed into the
strategy as parameters.

License
-------

Copyright (c) 2010-2011 e.e d3si9n, Coda Hale

Published under The MIT License, see LICENSE
