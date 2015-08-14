organization  := "ch.unibas.cs.gravis"

version       := "0.1.0"

name := "vsdconnect"

scalaVersion  := "2.11.6"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")


libraryDependencies ++= {
  val sprayV = "1.3.3"
  Seq(
    "io.spray"            %%  "spray-json"     % "1.3.2",
    "io.spray"            %%   "spray-client"     % sprayV,
    "io.spray"            %%   "spray-testkit" % sprayV,
    "commons-io"          % "commons-io"      % "2.3",
    "org.scalatest" %% "scalatest" % "2.2.0" % "test"
  )
}

seq(Revolver.settings: _*)




