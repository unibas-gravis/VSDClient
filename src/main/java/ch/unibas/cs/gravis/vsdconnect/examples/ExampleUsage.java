package ch.unibas.cs.gravis.vsdconnect.examples;

import ch.unibas.cs.gravis.vsdconnect.*;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

/**
 * Main class showing a very simple example usage of the Scala library from Java.
 */
public class ExampleUsage {

    public static void main(String[] args) {

        // make sure to change the "demo" method call to "apply" when working with the real VSD
        VSDConnect vsd = VSDConnect.demo("demo@virtualskeleton.ch", "demo").get();

        try {
            // list folders
            VSDFolder[] folders = Await.result(vsd.listFolders(2), Duration.apply("2 seconds"));

            // list published objects
            VSDCommonObjectInfo[] objects = Await.result(vsd.listPublishedObjects(3), Duration.apply("5 seconds"));

            VSDCommonObjectInfo rawObjectInfo = null;
            for(int i =0; i < objects.length; i++) {
                if((int) objects[i].type().get() == 1 ) rawObjectInfo = objects[i];
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
