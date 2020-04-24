publish / skip := VersionNumber(scalaJSVersion).numbers < Seq(1) && scalaBinaryVersion.value == "2.13"
