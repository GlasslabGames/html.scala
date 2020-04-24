import Ordering.Implicits._
// To prevent duplicate JVM releases, only publish JVM artifacts when Scala.js version is 1.x
publish / skip := VersionNumber(scalaJSVersion).numbers < Seq(1) && VersionNumber(scalaBinaryVersion.value).numbers >= Seq(2, 13)
