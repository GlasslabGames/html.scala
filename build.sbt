// shadow sbt-scalajs' crossProject(JSPlatform, JVMPlatform) and CrossType from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

val dynamicanyref = crossProject(JSPlatform, JVMPlatform)

val dynamicanyrefJVM = dynamicanyref.jvm

val dynamicanyrefJS = dynamicanyref.js

val html = project.dependsOn(dynamicanyrefJS)

ThisBuild / organization := "com.concentricsky.binding"

publish / skip := true