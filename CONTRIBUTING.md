  [1]: https://github.com/sbt/sbt-assembly/issues
  [2]: http://groups.google.com/group/simple-build-tool
  [3]: http://stackoverflow.com/questions/tagged/sbt-assembly

issue reporting guideline
-------------------------

Effective bug reports are more likely to be fixed. These guidelines explain how to write such reports.

## preliminaries

- Make sure your software is up to date.
- Search [stackoverflow][3] to see whether your question has been asked.
- Search [github issues][1] to see whether your bug has already been reported.
- Open one case for each problem.
- Proceed to the next steps for details.

## where to file a bug report

- For questions and specific support issues use [stackoverflow][3] with tags `sbt` and `sbt-assembly`.
- For enhancement ideas and confirmed bugs use [github issues][1] to report them.
- If it's a general discussion, try [sbt mailing list][2] and ping me on twitter (@eed3si9n) about it.

## how to file a bug report

The developers need three things from you: **steps**, **problem**, and **expectation**.

### steps

First and foremost, the developers need **exact steps to reproduce your problems on his/her computer**. This is called reproduction steps, which is often shortened to "repro steps" or "steps." Describe your method of running it. Provide the build files that was used that caused the problem.

**Repro steps are the most important part of a bug report.** If the developers cannot reproduce the problem in one way or the other, the problem can't be fixed. Telling them the error messages is not enough.

### problem

Next, describe the problem, or what you think is the problem. It might be "obvious" to you that it's a problem, but it could actually be an intentional behavior for some backward compatibility etc. When available, include stack trace. More raw info the better.

### expectation

Same as the problem. Describe what you think should've happened.

### notes

Add an optional notes section to describe your analysis.

### subject

The subject of the bug report doesn't matter. A more descriptive subject is certainly better, but a good subject really depends on the analysis of the problem, so don't worry too much about it.

### formatting

If possible, use Markdown to highlight outputs and code snipplets:

    ```scala
    import sbt._
    import Keys._
    import sbtassembly.Plugin._
    import AssemblyKeys._

    object Builds extends Build {
      lazy val buildSettings = Defaults.defaultSettings ++ Seq(
        version := "0.1-SNAPSHOT",
        organization := "com.example",
        scalaVersion := "2.10.1"
      )

      lazy val app = Project("app", file("app"),
        settings = buildSettings ++ assemblySettings) settings(
          // your settings here
        )
    }
    ```

The above will be displayed nicely in fixed width font with Scala syntax highlights on Github.

## pull reqs

The ultimate way of reporting a bug is to actually fix it and send us a pull request. The same principle appies here. Document what you changed and why using reproducible cases. Please add scripted tests if possible to demonstrate your case.

By sending the pull request, we assume that you agree to release your work under the license that covers this software.

### .gitignore

Please use [global .gitignore](http://help.github.com/ignore-files/) instead of adding editor junk files to `.gitignore`.
