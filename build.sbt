lazy val `html-Definitions` = project

lazy val `html-InterpolationParser` = project

lazy val `html` =
  project.dependsOn(`html-InterpolationParser`, `html-Definitions`)

ThisBuild / organization := "com.yang-bo"

publish / skip := true

enablePlugins(ScalaUnidocPlugin)
