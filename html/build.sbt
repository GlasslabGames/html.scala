enablePlugins(ScalaJSPlugin)

enablePlugins(ScalaJSBundlerPlugin)

enablePlugins(Example)

Test / requireJsDomEnv := true

libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.15" % Test

libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.3.0"

libraryDependencies += "com.thoughtworks.binding" %%% "bindable" % "2.1.3+76-44eca958"

libraryDependencies += "com.thoughtworks.binding" %%% "binding" % "12.1.0+109-0a838a95"

libraryDependencies += "net.sourceforge.htmlunit" % "neko-htmlunit" % "2.58.0"

libraryDependencies += "com.thoughtworks.dsl" %%% "macros-reset" % "2.0.0-M2+339-69c389a4"

libraryDependencies += "com.thoughtworks.dsl" %%% "domains-continuation" % "2.0.0-M2+339-69c389a4"

libraryDependencies += "com.thoughtworks.dsl" %%% "keywords-yield" % "2.0.0-M2+339-69c389a4"

libraryDependencies += "com.thoughtworks.dsl" %%% "keywords-await" % "2.0.0-M2+339-69c389a4" % Test

webpack / version := "5.75.0"
