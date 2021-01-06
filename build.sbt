name := "ousia"

organization := "top.emptystack"
version      := "0.1.0-SNAPSHOT"
scalaVersion := "2.12.12"

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
