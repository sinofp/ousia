import mill._
import mill.scalalib._
import mill.scalalib.scalafmt.ScalafmtModule
import coursier.maven.MavenRepository

val sv = "2.12.13"

object ousia extends ScalaModule with ScalafmtModule {
  def scalaVersion = sv

  override def repositoriesTask = T.task {
    super.repositoriesTask() ++ Seq(MavenRepository("https://oss.sonatype.org/content/repositories/snapshots"))
  }

  override def ivyDeps = Agg(ivy"edu.berkeley.cs::chisel3:3.5-SNAPSHOT")

  override def scalacOptions = super.scalacOptions() ++ Seq(
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
