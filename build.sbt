// shadow sbt-scalajs' crossProject(JSPlatform, JVMPlatform) and CrossType from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

val dynamicanyref = crossProject(JSPlatform, JVMPlatform)

val dynamicanyrefJVM = dynamicanyref.jvm

val dynamicanyrefJS = dynamicanyref.js

val html = project.dependsOn(dynamicanyrefJS)

ThisBuild / organization := "org.lrng.binding"

publish / skip := true

credentials in Global += Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", sys.env.getOrElse("SONATYPE_USERNAME", ""), sys.env.getOrElse("SONATYPE_PASSWORD", ""))

pgpSecretRing := baseDirectory.value / "secring.asc"

pgpPublicRing := baseDirectory.value / "pubring.asc"

pgpPassphrase := Some(Array.empty)