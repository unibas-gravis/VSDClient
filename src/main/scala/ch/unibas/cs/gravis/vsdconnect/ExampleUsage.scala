package ch.unibas.cs.gravis.vsdconnect

/**
 * An example usage in Scala.
 *
 * This is equivalent to the Java example usage case
 */

// These imports is very important to include in your scala code

import scala.concurrent.ExecutionContext.Implicits.global
import VSDJson._

object ExampleUsage {

  def main(args: Array[String]): Unit = {

    // make sure to change the "demo" method call to "apply" when working with the real VSD
    val vsd: VSDConnect = VSDConnect.demo("demo@virtualskeleton.ch", "demo").get

    val futre = for {
      folders <- vsd.listFolders() // list folders
      objects <- vsd.listPublishedObjects() // list published object
      rawObjectInfo = objects.toIndexedSeq.filter(_.`type`.get == 1).last // find a raw object
      info <- vsd.getVSDObjectInfo[VSDRawImageObjectInfo](VSDURL(rawObjectInfo.selfUrl)) // get a more detailed information
      modality <- vsd.getModality(info.modality.get)
    } yield {
        println("object modality " + modality.name)
        vsd.shutdown()
      }

    futre.onFailure { case e: Throwable => println("failed " + e.printStackTrace()) }
  }
}
