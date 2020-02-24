addSbtPlugin("com.thoughtworks.sbt-best-practice" % "sbt-best-practice" % "7.2.0")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.8.1")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.0.0")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")

addSbtPlugin("org.lyranthe.sbt" % "partial-unification" % "1.1.2")

addSbtPlugin("com.thoughtworks.example" % "sbt-example" % "7.0.0")

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.17.0")

libraryDependencies += "net.sourceforge.htmlunit" % "htmlunit" % "2.36.0"

libraryDependencies += "io.circe" %% "circe-generic" % "0.11.2"

libraryDependencies += "com.softwaremill.sttp" %% "circe" % "1.6.8"

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.1")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.32")
