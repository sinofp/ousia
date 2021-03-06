name := "ousia"

organization := "top.emptystack"
version      := "0.1.0-SNAPSHOT"
scalaVersion := "2.12.13"

lazy val `api-config-chipsalliance` = project in file("tool/api-config-chipsalliance/build-rules/sbt")
lazy val ousia                      = (project in file(".")).dependsOn(`api-config-chipsalliance`)

libraryDependencies ++= Seq(
  "edu.berkeley.cs" %% "chisel3"    % "3.4.+",
  "edu.berkeley.cs" %% "chiseltest" % "0.3.0" % "test",
)

scalacOptions ++= Seq(
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
