/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbtassembly
final class AssemblyShadeRule private (
  val rule: com.eed3si9n.jarjarabrams.ShadeRule,
  val moduleIds: Vector[sbt.librarymanagement.ModuleID]) extends Serializable {
  def inAll: AssemblyShadeRule = this.withRule(rule.inAll)
  def inProject: AssemblyShadeRule = this.withRule(rule.inProject)
  def inModuleCoordinates(modules: com.eed3si9n.jarjarabrams.ModuleCoordinate*): AssemblyShadeRule =
  this.withRule(rule.inModuleCoordinates(modules: _*))
  def inLibrary(modules: sbt.librarymanagement.ModuleID*): AssemblyShadeRule = this.withModuleIds(moduleIds ++ modules)
  def toShadeRule(scalaVersion: String, scalaBinaryVersion: String): com.eed3si9n.jarjarabrams.ShadeRule =
  rule.inModuleCoordinates(
  moduleIds
  .map(sbt.librarymanagement.CrossVersion(scalaVersion, scalaBinaryVersion))
  .map(m => com.eed3si9n.jarjarabrams.ModuleCoordinate(m.organization, m.name, m.revision)): _*
  )
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: AssemblyShadeRule => (this.rule == x.rule) && (this.moduleIds == x.moduleIds)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbtassembly.AssemblyShadeRule".##) + rule.##) + moduleIds.##)
  }
  override def toString: String = {
    "AssemblyShadeRule(" + rule + ", " + moduleIds + ")"
  }
  private[this] def copy(rule: com.eed3si9n.jarjarabrams.ShadeRule = rule, moduleIds: Vector[sbt.librarymanagement.ModuleID] = moduleIds): AssemblyShadeRule = {
    new AssemblyShadeRule(rule, moduleIds)
  }
  def withRule(rule: com.eed3si9n.jarjarabrams.ShadeRule): AssemblyShadeRule = {
    copy(rule = rule)
  }
  def withModuleIds(moduleIds: Vector[sbt.librarymanagement.ModuleID]): AssemblyShadeRule = {
    copy(moduleIds = moduleIds)
  }
}
object AssemblyShadeRule {
  trait implicits {
    implicit def assemblyShadeRuleFromShareRule(rule: com.eed3si9n.jarjarabrams.ShadeRule): AssemblyShadeRule = AssemblyShadeRule(rule)
    implicit def assemblyShadeRuleFromShadePattern(pattern: com.eed3si9n.jarjarabrams.ShadePattern): AssemblyShadeRule = AssemblyShadeRule(pattern)
  }
  def apply(rule: com.eed3si9n.jarjarabrams.ShadeRule): AssemblyShadeRule = AssemblyShadeRule(rule, Vector.empty)
  def apply(pattern: com.eed3si9n.jarjarabrams.ShadePattern): AssemblyShadeRule = AssemblyShadeRule(com.eed3si9n.jarjarabrams.ShadeRule(pattern, Vector.empty))
  def apply(rule: com.eed3si9n.jarjarabrams.ShadeRule, moduleIds: Vector[sbt.librarymanagement.ModuleID]): AssemblyShadeRule = new AssemblyShadeRule(rule, moduleIds)
}
