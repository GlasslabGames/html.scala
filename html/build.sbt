enablePlugins(ScalaJSBundlerPlugin)

enablePlugins(ScalaJSPlugin)

enablePlugins(Example)

scalaVersion := "2.13.7"

import scala.meta._

exampleSuperTypes += ctor"_root_.org.scalatest.Inside"

libraryDependencies += "com.thoughtworks.binding" %%% "binding" % "12.1.0+35-b6fe2621"
libraryDependencies += "com.thoughtworks.binding" %%% "bindable" % "2.1.3+3-f9b6eb7e"
libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.3" % Test

// Enable macro annotations by setting scalac flags for Scala 2.13
scalacOptions ++= {
  import Ordering.Implicits._
  if (VersionNumber(scalaVersion.value).numbers >= Seq(2L, 13L)) {
    Seq("-Ymacro-annotations")
  } else {
    Nil
  }
}

// Enable macro annotations by adding compiler plugins for Scala 2.11 and 2.12
libraryDependencies ++= {
  import Ordering.Implicits._
  if (VersionNumber(scalaVersion.value).numbers >= Seq(2L, 13L)) {
    Nil
  } else {
    Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full))
  }
}

scalacOptions in Test += "-Xxml:-coalescing"

requireJsDomEnv in Test := true

libraryDependencies += "com.yang-bo" %%% "curried" % "2.0.1"

libraryDependencies += "org.lrng.binding" %% "namebasedxml" % "1.0.2"

enablePlugins(Generators)
