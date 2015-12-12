/*
* Copyright 2015 University of Basel, Graphics and Vision Research Group
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package ch.unibas.cs.gravis.vsdclient

/**
 * An example usage in Scala.
 *
 * This is equivalent to the Java example usage case
 */

import scala.concurrent.ExecutionContext.Implicits.global
import VSDJson._

object ExampleUsage {

  def main(args: Array[String]): Unit = {

    // make sure to change the "demo" method call to "apply" when working with the real VSD
    val vsd: VSDClient = VSDClient.demo("demo@virtualskeleton.ch", "demo").get

    val futre = for {
      folders <- vsd.listFolders() // list folders
      objects <- vsd.listPublishedObjects() // list published object
      rawObjectInfo = objects.toIndexedSeq.filter(_.`type`.name == VSDRawImageObjectType.name).last // find a raw object
      info <- vsd.getVSDObjectInfo[VSDRawImageObjectInfo](VSDURL(rawObjectInfo.selfUrl)) // get a more detailed information
      modality <- vsd.getModality(info.rawImage.modality.get)
    } yield {
        println("object modality " + modality.name)
        vsd.shutdown()
      }

    futre.onFailure { case e: Throwable => println("failed " + e.printStackTrace()) }
  }
}
