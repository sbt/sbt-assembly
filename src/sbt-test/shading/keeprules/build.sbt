lazy val scala212 = "2.12.15"
lazy val scala213 = "2.13.7"

scalaVersion := scala212
crossScalaVersions := List(scala212, scala213)

lazy val keeprules = (project in file(".")).
  settings(
    version := "0.1",
    assembly / assemblyJarName := "foo.jar",
    libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.8.1",
    assembly / assemblyKeepRules := Seq("keep.**"),
    TaskKey[Unit]("check") := {
      IO.withTemporaryDirectory { dir ⇒
        IO.unzip(crossTarget.value / "foo.jar", dir)
        mustNotExist(dir / "removed" / "ShadeClass.class")
        mustNotExist(dir / "removed" / "ShadePackage.class")
        mustExist(dir / "keep" / "Keeped.class")

        val lang3 = dir / "org" / "apache" / "commons" / "lang3"
        val lang3Builder = lang3 / "builder"
        val lang3Text = lang3 / "text"
        val lang3Mutable = lang3 / "mutable"
        mustOnlyExistIn(lang3,
          lang3 / "ArrayUtils$1.class",
          lang3 / "ArrayUtils.class",
          lang3 / "BooleanUtils.class",
          lang3 / "CharSequenceUtils.class",
          lang3 / "CharUtils.class",
          lang3 / "ClassUtils$1$1.class",
          lang3 / "ClassUtils$1.class",
          lang3 / "ClassUtils$2$1.class",
          lang3 / "ClassUtils$2.class",
          lang3 / "ClassUtils$Interfaces.class",
          lang3 / "ClassUtils.class",
          lang3 / "ObjectUtils$Null.class",
          lang3 / "ObjectUtils.class",
          lang3 / "RegExUtils.class",
          lang3 / "StringEscapeUtils$CsvEscaper.class",
          lang3 / "StringEscapeUtils$CsvUnescaper.class",
          lang3 / "StringEscapeUtils.class",
          lang3 / "StringUtils.class",
          lang3 / "Validate.class",
          lang3Builder / "Builder.class",
          lang3Builder / "CompareToBuilder.class",
          lang3Builder / "EqualsBuilder.class",
          lang3Builder / "EqualsExclude.class",
          lang3Builder / "HashCodeBuilder.class",
          lang3Builder / "HashCodeExclude.class",
          lang3Builder / "IDKey.class",
          lang3Builder / "ReflectionToStringBuilder.class",
          lang3Builder / "ToStringBuilder.class",
          lang3Builder / "ToStringExclude.class",
          lang3Builder / "ToStringStyle$DefaultToStringStyle.class",
          lang3Builder / "ToStringStyle$JsonToStringStyle.class",
          lang3Builder / "ToStringStyle$MultiLineToStringStyle.class",
          lang3Builder / "ToStringStyle$NoClassNameToStringStyle.class",
          lang3Builder / "ToStringStyle$NoFieldNameToStringStyle.class",
          lang3Builder / "ToStringStyle$ShortPrefixToStringStyle.class",
          lang3Builder / "ToStringStyle$SimpleToStringStyle.class",
          lang3Builder / "ToStringStyle.class",
          lang3Builder / "ToStringSummary.class",
          lang3 / "exception" / "CloneFailedException.class",
          lang3 / "math" / "NumberUtils.class",
          lang3Mutable / "Mutable.class",
          lang3Mutable / "MutableInt.class",
          lang3Mutable / "MutableObject.class",
          lang3Text / "StrBuilder$StrBuilderReader.class",
          lang3Text / "StrBuilder$StrBuilderTokenizer.class",
          lang3Text / "StrBuilder$StrBuilderWriter.class",
          lang3Text / "StrBuilder.class",
          lang3Text / "StrMatcher$CharMatcher.class",
          lang3Text / "StrMatcher$CharSetMatcher.class",
          lang3Text / "StrMatcher$NoMatcher.class",
          lang3Text / "StrMatcher$StringMatcher.class",
          lang3Text / "StrMatcher$TrimMatcher.class",
          lang3Text / "StrMatcher.class",
          lang3Text / "StrTokenizer.class",
          lang3Text / "translate" / "AggregateTranslator.class",
          lang3Text / "translate" / "CharSequenceTranslator.class",
          lang3Text / "translate" / "CodePointTranslator.class",
          lang3Text / "translate" / "EntityArrays.class",
          lang3Text / "translate" / "JavaUnicodeEscaper.class",
          lang3Text / "translate" / "LookupTranslator.class",
          lang3Text / "translate" / "NumericEntityEscaper.class",
          lang3Text / "translate" / "NumericEntityUnescaper$OPTION.class",
          lang3Text / "translate" / "NumericEntityUnescaper.class",
          lang3Text / "translate" / "OctalUnescaper.class",
          lang3Text / "translate" / "UnicodeEscaper.class",
          lang3Text / "translate" / "UnicodeUnescaper.class",
          lang3Text / "translate" / "UnicodeUnpairedSurrogateRemover.class",
          lang3 / "tuple" / "ImmutablePair.class",
          lang3 / "tuple" / "Pair.class"
         )
      }
    })

def mustOnlyExistIn(directory: File, files: File*): Unit = {
  if (!directory.exists()) {
    sys.error("directory" + directory + " does not exist!")
  }

  if (!directory.isDirectory) {
    sys.error(directory + " is not a directory!")
  }

  def getAllFilesRecursively(f: File): Set[File] = {
    val allFiles = f.listFiles()
    val (directories, files) = allFiles.partition(_.isDirectory)
    directories.toSet.flatMap(getAllFilesRecursively) ++ files
  }

  val expectedFilesSet = files.toSet
  val actualFileSet = getAllFilesRecursively(directory)

  if (expectedFilesSet != actualFileSet) {
    sys.error("files expected in " + directory + " are not matching the actual files")
  }
}

def mustNotExist(f: File): Unit = {
  if (f.exists) sys.error("file" + f + " exists!")
}
def mustExist(f: File): Unit = {
  if (!f.exists) sys.error("file" + f + " does not exist!")
}
