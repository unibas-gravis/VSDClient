organization  := "ch.unibas.cs.gravis"

version       := "0.2.0"

name := "vsdclient"

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

publishTo :=  Some(Resolver.file("file", new File("/export/contrib/statismo/repo/public")))

seq(Revolver.settings: _*)

seq(site.settings: _*)

seq(ghpages.settings: _*)

site.includeScaladoc()

git.remoteRepo := "git@github.com:unibas-gravis/VSDClient.git"
