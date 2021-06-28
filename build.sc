import mill._
import mill.scalalib._
import mill.scalalib.scalafmt.ScalafmtModule

val sv = "2.12.13"

object ousia extends ScalaModule with ScalafmtModule {
  def scalaVersion = sv

  def moduleDeps = Seq(`api-config-chipsalliance`)

  def ivyDeps = Agg(ivy"edu.berkeley.cs::chisel3:3.4.+")

  def scalacOptions = super.scalacOptions() ++ Seq(
    // Required options for Chisel code
    "-Xsource:2.11",
    // Recommended options
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    // Features I like
    "-language:implicitConversions",
    "-language:postfixOps",
  )
}

object `api-config-chipsalliance` extends ScalaModule {
  def millSourcePath = os.pwd / "3rd_party" / "api-config-chipsalliance" / "design" / "craft"
  def scalaVersion   = sv
}
// vim:ft=scala
