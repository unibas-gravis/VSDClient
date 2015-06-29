organization  := "ch.unibas.cs.gravis"

version       := "0.7.11"

name := "vsdconnect"

scalaVersion  := "2.10.3"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/"
)

libraryDependencies ++= {
  val sprayV = "1.1.0"
  Seq(
    "io.spray"            %%  "spray-json"     % "1.2.5",
    "io.spray"            %   "spray-client"     % sprayV withSources(),
    "io.spray"            %   "spray-testkit" % sprayV withSources(),
    "commons-io"          % "commons-io"      % "2.3",
    "org.scalatest" %% "scalatest" % "2.2.0" % "test"
  )
}

seq(Revolver.settings: _*)




