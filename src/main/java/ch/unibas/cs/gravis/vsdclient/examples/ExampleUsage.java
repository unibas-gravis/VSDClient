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


package ch.unibas.cs.gravis.vsdclient.examples;

import ch.unibas.cs.gravis.vsdclient.*;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

/**
 * Main class showing a very simple example usage of the Scala library from Java.
 */
public class ExampleUsage {

    public static void main(String[] args) {

        // make sure to change the "demo" method call to "apply" when working with the real VSD
        VSDClient vsd = VSDClient.demo("demo@virtualskeleton.ch", "demo").get();

        try {
            // list folders
            VSDFolder[] folders = Await.result(vsd.listFolders(2), Duration.apply("2 seconds"));

            // list published objects
            VSDCommonObjectInfo[] objects = Await.result(vsd.listPublishedObjects(3), Duration.apply("5 seconds"));

            VSDCommonObjectInfo rawObjectInfo = null;
            for(int i =0; i < objects.length; i++) {
                if((Integer) objects[i].type().get() == 1 ) rawObjectInfo = objects[i];
            }

            VSDRawImageObjectInfo info =  Await.result(vsd.getVSDObjectInfo(new VSDURL(rawObjectInfo.selfUrl()), VSDJson.VSDRawImageObjectInfoProtocol()), Duration.apply("2 seconds"));

            VSDModality modality = Await.result(vsd.getModality(new VSDURL(info.modality().get().selfUrl())), Duration.apply("5 seconds"));

            System.out.println("object modality " + modality.name());

        } catch (Exception e) {
            e.printStackTrace();
        }
        vsd.shutdown();
    }

}
