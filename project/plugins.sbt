addSbtPlugin("com.thoughtworks.sbt-best-practice" % "sbt-best-practice" % "7.2.0")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.7")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2-1")

addSbtPlugin("org.lyranthe.sbt" % "partial-unification" % "1.1.2")

addSbtPlugin("com.thoughtworks.example" % "sbt-example" % "7.0.1")

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0")

libraryDependencies += "net.sourceforge.htmlunit" % "htmlunit" % "2.46.0"

libraryDependencies += "io.circe" %% "circe-generic" % "0.12.3"

libraryDependencies += "com.softwaremill.sttp" %% "circe" % "1.7.2"

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.0.1")

addSbtPlugin("com.thoughtworks.sbt-scala-js-map" % "sbt-scala-js-map" % "4.0.0")
