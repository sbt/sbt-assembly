exportJars := false
scalaVersion := "2.13.12"

assembly / assemblyShadeRules := Seq(
  ShadeRule.rename("example.A" -> "example.B").inProject
)

TaskKey[Unit]("writeSealedJavaCodeIf17") := {
  if (scala.util.Properties.isJavaAtLeast("17")) {
    IO.write(
      file("A.java"),
      """package example;

      public sealed interface A {
        final class X implements A {}
      }
      """
    )
  }
}
