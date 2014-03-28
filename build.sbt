import AssemblyKeys._

assemblySettings

name := "semeval2014"

version := "0.1-SNAPSHOT"

organization := "edu.cmu.cs.ark"

scalaVersion := "2.10.2"

scalacOptions += "-optimize"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.9.2",
  "edu.washington.cs.knowitall" % "morpha-stemmer" % "1.0.4",
  "com.beust" % "jcommander" % "1.30",
  "com.jsuereth" %% "scala-arm" % "1.3"
)

mainClass := Some("edu.cmu.cs.ark.semeval2014.lr.LRParser")
