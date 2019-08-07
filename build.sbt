name := "html"

ThisBuild / organization := "com.concentricsky"

enablePlugins(ScalaJSBundlerPlugin)

enablePlugins(ScalaJSPlugin)

enablePlugins(Example)

import scala.meta._
exampleSuperTypes += ctor"_root_.org.scalatest.Inside"

libraryDependencies += "com.thoughtworks.binding" %%% "binding" % "11.8.1"

libraryDependencies += "com.thoughtworks.binding" %%% "bindable" % "1.1.0"

libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.8" % Test

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)

libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.7"

dependsOn(RootProject(file("nameBasedXml.scala")))

requireJsDomEnv in Test := true
