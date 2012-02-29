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

For sbt 0.11, add sbt-assembly as a dependency in `project/plugins.sbt`:

```scala
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.7.3")

resolvers += Resolver.url("sbt-plugin-releases",
  new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
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

seq(assemblySettings: _*)
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

License
-------

Copyright (c) 2010-2011 e.e d3si9n, Coda Hale

Published under The MIT License, see LICENSE
