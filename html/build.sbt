enablePlugins(ScalaJSBundlerPlugin)

enablePlugins(ScalaJSPlugin)

enablePlugins(Example)

import scala.meta._

exampleSuperTypes += ctor"_root_.org.scalatest.Inside"

libraryDependencies += "com.thoughtworks.binding" %%% "binding" % "11.8.1"

libraryDependencies += "com.thoughtworks.binding" %%% "bindable" % "1.1.0"

libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.8" % Test

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)

scalacOptions in Test += "-Xxml:-coalescing"

requireJsDomEnv in Test := true

installJsdom / version := "15.1.1"

libraryDependencies += "com.yang-bo" %%% "curried" % "2.0.0"

libraryDependencies += "org.lrng.binding" %% "namebasedxml" % "1.0.0+35-548ecb4c"

enablePlugins(Generators)
