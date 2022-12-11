/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbtassembly
/**
 * @param includeBin include compiled class files from itself or subprojects
 * @param includeDependency include class files from external dependencies
 */
final class AssemblyOption private (
  val includeBin: Boolean,
  val includeScala: Boolean,
  val includeDependency: Boolean,
  val excludedJars: sbt.Keys.Classpath,
  val repeatableBuild: Boolean,
  val mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy,
  val cacheOutput: Boolean,
  val appendContentHash: Boolean,
  val prependShellScript: Option[sbtassembly.Assembly.SeqString],
  val maxHashLength: Option[Int],
  val shadeRules: sbtassembly.Assembly.SeqShadeRules,
  val keepRules: sbtassembly.Assembly.SeqString,
  val scalaVersion: String,
  val level: sbt.Level.Value) extends Serializable {
  
  private def this() = this(true, true, true, Nil, true, sbtassembly.MergeStrategy.defaultMergeStrategy, true, false, None, None, sbtassembly.Assembly.defaultShadeRules, sbtassembly.Assembly.defaultKeepRules, "", sbt.Level.Info)
  private def this(includeBin: Boolean, includeScala: Boolean, includeDependency: Boolean, excludedJars: sbt.Keys.Classpath, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy, cacheOutput: Boolean, appendContentHash: Boolean, prependShellScript: Option[sbtassembly.Assembly.SeqString], maxHashLength: Option[Int], shadeRules: sbtassembly.Assembly.SeqShadeRules, scalaVersion: String, level: sbt.Level.Value) = this(includeBin, includeScala, includeDependency, excludedJars, true, mergeStrategy, cacheOutput, appendContentHash, prependShellScript, maxHashLength, shadeRules, sbtassembly.Assembly.defaultKeepRules, scalaVersion, level)
  private def this(includeBin: Boolean, includeScala: Boolean, includeDependency: Boolean, excludedJars: sbt.Keys.Classpath, repeatableBuild: Boolean, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy, cacheOutput: Boolean, appendContentHash: Boolean, prependShellScript: Option[sbtassembly.Assembly.SeqString], maxHashLength: Option[Int], shadeRules: sbtassembly.Assembly.SeqShadeRules, scalaVersion: String, level: sbt.Level.Value) = this(includeBin, includeScala, includeDependency, excludedJars, repeatableBuild, mergeStrategy, cacheOutput, appendContentHash, prependShellScript, maxHashLength, shadeRules, sbtassembly.Assembly.defaultKeepRules, scalaVersion, level)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: AssemblyOption => (this.includeBin == x.includeBin) && (this.includeScala == x.includeScala) && (this.includeDependency == x.includeDependency) && (this.excludedJars == x.excludedJars) && (this.repeatableBuild == x.repeatableBuild) && (this.mergeStrategy == x.mergeStrategy) && (this.cacheOutput == x.cacheOutput) && (this.appendContentHash == x.appendContentHash) && (this.prependShellScript == x.prependShellScript) && (this.maxHashLength == x.maxHashLength) && (this.shadeRules == x.shadeRules) && (this.keepRules == x.keepRules) && (this.scalaVersion == x.scalaVersion) && (this.level == x.level)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbtassembly.AssemblyOption".##) + includeBin.##) + includeScala.##) + includeDependency.##) + excludedJars.##) + repeatableBuild.##) + mergeStrategy.##) + cacheOutput.##) + appendContentHash.##) + prependShellScript.##) + maxHashLength.##) + shadeRules.##) + keepRules.##) + scalaVersion.##) + level.##)
  }
  override def toString: String = {
    "AssemblyOption(" + includeBin + ", " + includeScala + ", " + includeDependency + ", " + excludedJars + ", " + repeatableBuild + ", " + mergeStrategy + ", " + cacheOutput + ", " + appendContentHash + ", " + prependShellScript + ", " + maxHashLength + ", " + shadeRules + ", " + keepRules + ", " + scalaVersion + ", " + level + ")"
  }
  private[this] def copy(includeBin: Boolean = includeBin, includeScala: Boolean = includeScala, includeDependency: Boolean = includeDependency, excludedJars: sbt.Keys.Classpath = excludedJars, repeatableBuild: Boolean = repeatableBuild, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy = mergeStrategy, cacheOutput: Boolean = cacheOutput, appendContentHash: Boolean = appendContentHash, prependShellScript: Option[sbtassembly.Assembly.SeqString] = prependShellScript, maxHashLength: Option[Int] = maxHashLength, shadeRules: sbtassembly.Assembly.SeqShadeRules = shadeRules, keepRules: sbtassembly.Assembly.SeqString = keepRules, scalaVersion: String = scalaVersion, level: sbt.Level.Value = level): AssemblyOption = {
    new AssemblyOption(includeBin, includeScala, includeDependency, excludedJars, repeatableBuild, mergeStrategy, cacheOutput, appendContentHash, prependShellScript, maxHashLength, shadeRules, keepRules, scalaVersion, level)
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
  def withRepeatableBuild(repeatableBuild: Boolean): AssemblyOption = {
    copy(repeatableBuild = repeatableBuild)
  }
  def withMergeStrategy(mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy): AssemblyOption = {
    copy(mergeStrategy = mergeStrategy)
  }
  def withCacheOutput(cacheOutput: Boolean): AssemblyOption = {
    copy(cacheOutput = cacheOutput)
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
  def withKeepRules(keepRules: sbtassembly.Assembly.SeqString): AssemblyOption = {
    copy(keepRules = keepRules)
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
  def apply(includeBin: Boolean, includeScala: Boolean, includeDependency: Boolean, excludedJars: sbt.Keys.Classpath, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy, cacheOutput: Boolean, appendContentHash: Boolean, prependShellScript: Option[sbtassembly.Assembly.SeqString], maxHashLength: Option[Int], shadeRules: sbtassembly.Assembly.SeqShadeRules, scalaVersion: String, level: sbt.Level.Value): AssemblyOption = new AssemblyOption(includeBin, includeScala, includeDependency, excludedJars, mergeStrategy, cacheOutput, appendContentHash, prependShellScript, maxHashLength, shadeRules, scalaVersion, level)
  def apply(includeBin: Boolean, includeScala: Boolean, includeDependency: Boolean, excludedJars: sbt.Keys.Classpath, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy, cacheOutput: Boolean, appendContentHash: Boolean, prependShellScript: sbtassembly.Assembly.SeqString, maxHashLength: Int, shadeRules: sbtassembly.Assembly.SeqShadeRules, scalaVersion: String, level: sbt.Level.Value): AssemblyOption = new AssemblyOption(includeBin, includeScala, includeDependency, excludedJars, mergeStrategy, cacheOutput, appendContentHash, Option(prependShellScript), Option(maxHashLength), shadeRules, scalaVersion, level)
  def apply(includeBin: Boolean, includeScala: Boolean, includeDependency: Boolean, excludedJars: sbt.Keys.Classpath, repeatableBuild: Boolean, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy, cacheOutput: Boolean, appendContentHash: Boolean, prependShellScript: Option[sbtassembly.Assembly.SeqString], maxHashLength: Option[Int], shadeRules: sbtassembly.Assembly.SeqShadeRules, scalaVersion: String, level: sbt.Level.Value): AssemblyOption = new AssemblyOption(includeBin, includeScala, includeDependency, excludedJars, repeatableBuild, mergeStrategy, cacheOutput, appendContentHash, prependShellScript, maxHashLength, shadeRules, scalaVersion, level)
  def apply(includeBin: Boolean, includeScala: Boolean, includeDependency: Boolean, excludedJars: sbt.Keys.Classpath, repeatableBuild: Boolean, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy, cacheOutput: Boolean, appendContentHash: Boolean, prependShellScript: sbtassembly.Assembly.SeqString, maxHashLength: Int, shadeRules: sbtassembly.Assembly.SeqShadeRules, scalaVersion: String, level: sbt.Level.Value): AssemblyOption = new AssemblyOption(includeBin, includeScala, includeDependency, excludedJars, repeatableBuild, mergeStrategy, cacheOutput, appendContentHash, Option(prependShellScript), Option(maxHashLength), shadeRules, scalaVersion, level)
  def apply(includeBin: Boolean, includeScala: Boolean, includeDependency: Boolean, excludedJars: sbt.Keys.Classpath, repeatableBuild: Boolean, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy, cacheOutput: Boolean, appendContentHash: Boolean, prependShellScript: Option[sbtassembly.Assembly.SeqString], maxHashLength: Option[Int], shadeRules: sbtassembly.Assembly.SeqShadeRules, keepRules: sbtassembly.Assembly.SeqString, scalaVersion: String, level: sbt.Level.Value): AssemblyOption = new AssemblyOption(includeBin, includeScala, includeDependency, excludedJars, repeatableBuild, mergeStrategy, cacheOutput, appendContentHash, prependShellScript, maxHashLength, shadeRules, keepRules, scalaVersion, level)
  def apply(includeBin: Boolean, includeScala: Boolean, includeDependency: Boolean, excludedJars: sbt.Keys.Classpath, repeatableBuild: Boolean, mergeStrategy: sbtassembly.MergeStrategy.StringToMergeStrategy, cacheOutput: Boolean, appendContentHash: Boolean, prependShellScript: sbtassembly.Assembly.SeqString, maxHashLength: Int, shadeRules: sbtassembly.Assembly.SeqShadeRules, keepRules: sbtassembly.Assembly.SeqString, scalaVersion: String, level: sbt.Level.Value): AssemblyOption = new AssemblyOption(includeBin, includeScala, includeDependency, excludedJars, repeatableBuild, mergeStrategy, cacheOutput, appendContentHash, Option(prependShellScript), Option(maxHashLength), shadeRules, keepRules, scalaVersion, level)
}
