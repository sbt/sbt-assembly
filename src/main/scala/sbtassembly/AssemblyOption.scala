/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbtassembly
/**
 * @param includeBin include compiled class files from itself or subprojects
 * @param includeDependency include class files from external dependencies
 * @param cacheUseHardLinks this is experimental but will use a hard link between the files in assemblyUnzipDirectory to assemblyDirectory to avoid additional copy IO
 */
final class AssemblyOption private (
  val assemblyDirectory: Option[java.io.File],
  val assemblyUnzipDirectory: Option[java.io.File],
  val includeBin: Boolean,
  val includeScala: Boolean,
  val includeDependency: Boolean,
  val excludedJars: sbt.Keys.Classpath,
  val excludedFiles: sbtassembly.Assembly.SeqFileToSeqFile,
  val mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy,
  val cacheOutput: Boolean,
  val cacheUnzip: Boolean,
  val cacheUseHardLinks: Boolean,
  val appendContentHash: Boolean,
  val prependShellScript: Option[sbtassembly.Assembly.SeqString],
  val maxHashLength: Option[Int],
  val shadeRules: sbtassembly.Assembly.SeqShadeRules,
  val scalaVersion: String,
  val level: sbt.Level.Value) extends Serializable {
  
  private def this() = this(None, None, true, true, true, Nil, sbtassembly.Assembly.defaultExcludedFiles, sbtassembly.MergeStrategy.defaultMergeStrategy, true, true, false, false, None, None, sbtassembly.Assembly.defaultShadeRules, "", sbt.Level.Info)
  private def this(assemblyDirectory: Option[java.io.File], includeBin: Boolean, includeScala: Boolean, includeDependency: Boolean, excludedJars: sbt.Keys.Classpath, excludedFiles: sbtassembly.Assembly.SeqFileToSeqFile, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy, cacheOutput: Boolean, cacheUnzip: Boolean, appendContentHash: Boolean, prependShellScript: Option[sbtassembly.Assembly.SeqString], maxHashLength: Option[Int], shadeRules: sbtassembly.Assembly.SeqShadeRules, scalaVersion: String, level: sbt.Level.Value) = this(assemblyDirectory, None, includeBin, includeScala, includeDependency, excludedJars, excludedFiles, mergeStrategy, cacheOutput, cacheUnzip, false, appendContentHash, prependShellScript, maxHashLength, shadeRules, scalaVersion, level)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: AssemblyOption => (this.assemblyDirectory == x.assemblyDirectory) && (this.assemblyUnzipDirectory == x.assemblyUnzipDirectory) && (this.includeBin == x.includeBin) && (this.includeScala == x.includeScala) && (this.includeDependency == x.includeDependency) && (this.excludedJars == x.excludedJars) && (this.excludedFiles == x.excludedFiles) && (this.mergeStrategy == x.mergeStrategy) && (this.cacheOutput == x.cacheOutput) && (this.cacheUnzip == x.cacheUnzip) && (this.cacheUseHardLinks == x.cacheUseHardLinks) && (this.appendContentHash == x.appendContentHash) && (this.prependShellScript == x.prependShellScript) && (this.maxHashLength == x.maxHashLength) && (this.shadeRules == x.shadeRules) && (this.scalaVersion == x.scalaVersion) && (this.level == x.level)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbtassembly.AssemblyOption".##) + assemblyDirectory.##) + assemblyUnzipDirectory.##) + includeBin.##) + includeScala.##) + includeDependency.##) + excludedJars.##) + excludedFiles.##) + mergeStrategy.##) + cacheOutput.##) + cacheUnzip.##) + cacheUseHardLinks.##) + appendContentHash.##) + prependShellScript.##) + maxHashLength.##) + shadeRules.##) + scalaVersion.##) + level.##)
  }
  override def toString: String = {
    "AssemblyOption(" + assemblyDirectory + ", " + assemblyUnzipDirectory + ", " + includeBin + ", " + includeScala + ", " + includeDependency + ", " + excludedJars + ", " + excludedFiles + ", " + mergeStrategy + ", " + cacheOutput + ", " + cacheUnzip + ", " + cacheUseHardLinks + ", " + appendContentHash + ", " + prependShellScript + ", " + maxHashLength + ", " + shadeRules + ", " + scalaVersion + ", " + level + ")"
  }
  private[this] def copy(assemblyDirectory: Option[java.io.File] = assemblyDirectory, assemblyUnzipDirectory: Option[java.io.File] = assemblyUnzipDirectory, includeBin: Boolean = includeBin, includeScala: Boolean = includeScala, includeDependency: Boolean = includeDependency, excludedJars: sbt.Keys.Classpath = excludedJars, excludedFiles: sbtassembly.Assembly.SeqFileToSeqFile = excludedFiles, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy = mergeStrategy, cacheOutput: Boolean = cacheOutput, cacheUnzip: Boolean = cacheUnzip, cacheUseHardLinks: Boolean = cacheUseHardLinks, appendContentHash: Boolean = appendContentHash, prependShellScript: Option[sbtassembly.Assembly.SeqString] = prependShellScript, maxHashLength: Option[Int] = maxHashLength, shadeRules: sbtassembly.Assembly.SeqShadeRules = shadeRules, scalaVersion: String = scalaVersion, level: sbt.Level.Value = level): AssemblyOption = {
    new AssemblyOption(assemblyDirectory, assemblyUnzipDirectory, includeBin, includeScala, includeDependency, excludedJars, excludedFiles, mergeStrategy, cacheOutput, cacheUnzip, cacheUseHardLinks, appendContentHash, prependShellScript, maxHashLength, shadeRules, scalaVersion, level)
  }
  def withAssemblyDirectory(assemblyDirectory: Option[java.io.File]): AssemblyOption = {
    copy(assemblyDirectory = assemblyDirectory)
  }
  def withAssemblyDirectory(assemblyDirectory: java.io.File): AssemblyOption = {
    copy(assemblyDirectory = Option(assemblyDirectory))
  }
  def withAssemblyUnzipDirectory(assemblyUnzipDirectory: Option[java.io.File]): AssemblyOption = {
    copy(assemblyUnzipDirectory = assemblyUnzipDirectory)
  }
  def withAssemblyUnzipDirectory(assemblyUnzipDirectory: java.io.File): AssemblyOption = {
    copy(assemblyUnzipDirectory = Option(assemblyUnzipDirectory))
  }
  def withIncludeBin(includeBin: Boolean): AssemblyOption = {
    copy(includeBin = includeBin)
  }
  def withIncludeScala(includeScala: Boolean): AssemblyOption = {
    copy(includeScala = includeScala)
  }
  def withIncludeDependency(includeDependency: Boolean): AssemblyOption = {
    copy(includeDependency = includeDependency)
  }
  def withExcludedJars(excludedJars: sbt.Keys.Classpath): AssemblyOption = {
    copy(excludedJars = excludedJars)
  }
  def withExcludedFiles(excludedFiles: sbtassembly.Assembly.SeqFileToSeqFile): AssemblyOption = {
    copy(excludedFiles = excludedFiles)
  }
  def withMergeStrategy(mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy): AssemblyOption = {
    copy(mergeStrategy = mergeStrategy)
  }
  def withCacheOutput(cacheOutput: Boolean): AssemblyOption = {
    copy(cacheOutput = cacheOutput)
  }
  def withCacheUnzip(cacheUnzip: Boolean): AssemblyOption = {
    copy(cacheUnzip = cacheUnzip)
  }
  def withCacheUseHardLinks(cacheUseHardLinks: Boolean): AssemblyOption = {
    copy(cacheUseHardLinks = cacheUseHardLinks)
  }
  def withAppendContentHash(appendContentHash: Boolean): AssemblyOption = {
    copy(appendContentHash = appendContentHash)
  }
  def withPrependShellScript(prependShellScript: Option[sbtassembly.Assembly.SeqString]): AssemblyOption = {
    copy(prependShellScript = prependShellScript)
  }
  def withPrependShellScript(prependShellScript: sbtassembly.Assembly.SeqString): AssemblyOption = {
    copy(prependShellScript = Option(prependShellScript))
  }
  def withMaxHashLength(maxHashLength: Option[Int]): AssemblyOption = {
    copy(maxHashLength = maxHashLength)
  }
  def withMaxHashLength(maxHashLength: Int): AssemblyOption = {
    copy(maxHashLength = Option(maxHashLength))
  }
  def withShadeRules(shadeRules: sbtassembly.Assembly.SeqShadeRules): AssemblyOption = {
    copy(shadeRules = shadeRules)
  }
  def withScalaVersion(scalaVersion: String): AssemblyOption = {
    copy(scalaVersion = scalaVersion)
  }
  def withLevel(level: sbt.Level.Value): AssemblyOption = {
    copy(level = level)
  }
}
object AssemblyOption {
  
  def apply(): AssemblyOption = new AssemblyOption()
  def apply(assemblyDirectory: Option[java.io.File], includeBin: Boolean, includeScala: Boolean, includeDependency: Boolean, excludedJars: sbt.Keys.Classpath, excludedFiles: sbtassembly.Assembly.SeqFileToSeqFile, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy, cacheOutput: Boolean, cacheUnzip: Boolean, appendContentHash: Boolean, prependShellScript: Option[sbtassembly.Assembly.SeqString], maxHashLength: Option[Int], shadeRules: sbtassembly.Assembly.SeqShadeRules, scalaVersion: String, level: sbt.Level.Value): AssemblyOption = new AssemblyOption(assemblyDirectory, includeBin, includeScala, includeDependency, excludedJars, excludedFiles, mergeStrategy, cacheOutput, cacheUnzip, appendContentHash, prependShellScript, maxHashLength, shadeRules, scalaVersion, level)
  def apply(assemblyDirectory: java.io.File, includeBin: Boolean, includeScala: Boolean, includeDependency: Boolean, excludedJars: sbt.Keys.Classpath, excludedFiles: sbtassembly.Assembly.SeqFileToSeqFile, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy, cacheOutput: Boolean, cacheUnzip: Boolean, appendContentHash: Boolean, prependShellScript: sbtassembly.Assembly.SeqString, maxHashLength: Int, shadeRules: sbtassembly.Assembly.SeqShadeRules, scalaVersion: String, level: sbt.Level.Value): AssemblyOption = new AssemblyOption(Option(assemblyDirectory), includeBin, includeScala, includeDependency, excludedJars, excludedFiles, mergeStrategy, cacheOutput, cacheUnzip, appendContentHash, Option(prependShellScript), Option(maxHashLength), shadeRules, scalaVersion, level)
  def apply(assemblyDirectory: Option[java.io.File], assemblyUnzipDirectory: Option[java.io.File], includeBin: Boolean, includeScala: Boolean, includeDependency: Boolean, excludedJars: sbt.Keys.Classpath, excludedFiles: sbtassembly.Assembly.SeqFileToSeqFile, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy, cacheOutput: Boolean, cacheUnzip: Boolean, cacheUseHardLinks: Boolean, appendContentHash: Boolean, prependShellScript: Option[sbtassembly.Assembly.SeqString], maxHashLength: Option[Int], shadeRules: sbtassembly.Assembly.SeqShadeRules, scalaVersion: String, level: sbt.Level.Value): AssemblyOption = new AssemblyOption(assemblyDirectory, assemblyUnzipDirectory, includeBin, includeScala, includeDependency, excludedJars, excludedFiles, mergeStrategy, cacheOutput, cacheUnzip, cacheUseHardLinks, appendContentHash, prependShellScript, maxHashLength, shadeRules, scalaVersion, level)
  def apply(assemblyDirectory: java.io.File, assemblyUnzipDirectory: java.io.File, includeBin: Boolean, includeScala: Boolean, includeDependency: Boolean, excludedJars: sbt.Keys.Classpath, excludedFiles: sbtassembly.Assembly.SeqFileToSeqFile, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy, cacheOutput: Boolean, cacheUnzip: Boolean, cacheUseHardLinks: Boolean, appendContentHash: Boolean, prependShellScript: sbtassembly.Assembly.SeqString, maxHashLength: Int, shadeRules: sbtassembly.Assembly.SeqShadeRules, scalaVersion: String, level: sbt.Level.Value): AssemblyOption = new AssemblyOption(Option(assemblyDirectory), Option(assemblyUnzipDirectory), includeBin, includeScala, includeDependency, excludedJars, excludedFiles, mergeStrategy, cacheOutput, cacheUnzip, cacheUseHardLinks, appendContentHash, Option(prependShellScript), Option(maxHashLength), shadeRules, scalaVersion, level)
}
