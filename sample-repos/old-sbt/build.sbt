val scala3Version = "3.0.0"

lazy val root = project
  .in(file("."))
  .settings(
    name := "old-sbt",
    organization := "com.example",
    version := "0.0.1",
    scalaVersion := scala3Version
  )