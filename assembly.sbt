import AssemblyKeys._ // put this at the top of the file

assemblySettings

jarName in assembly := "VSDConnect.jar"

//mainClass in assembly := Some("ch.unibas.cs.gravis.shapemodelling.Launcher")


  mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
    {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", s) if s.endsWith(".SF") || s.endsWith(".DSA") || s.endsWith(".RSA") => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
  }

fork in run := true


