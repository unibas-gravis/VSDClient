organization  := "org.statismo"

version       := "0.1"

name := "stkvsdconnect"

scalaVersion  := "2.10.3"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/"
)

libraryDependencies ++= {
  val akkaV = "2.1.4"
  val sprayV = "1.1.0"
  Seq( 
  	"io.spray"            %%  "spray-json"     % "1.2.5",
    "io.spray"            %   "spray-client"     % sprayV withSources(),
    "io.spray"            %   "spray-can"     % sprayV withSources(),
    "io.spray"            %   "spray-routing" % sprayV withSources(),
    "io.spray"            %   "spray-testkit" % sprayV withSources(),
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV,
    "org.specs2"          %%  "specs2"        % "2.2.3" % "test",
    "commons-io"          % "commons-io"      % "2.3",
    "org.scalatest" %% "scalatest" % "2.2.0" % "test"
  )
}

seq(Revolver.settings: _*)
