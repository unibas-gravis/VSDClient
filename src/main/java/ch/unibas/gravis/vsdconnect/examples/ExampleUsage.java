package ch.unibas.gravis.vsdconnect.examples;

import ch.unibas.gravis.vsdconnect.*;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

/**
 * Main class showing a very simple example usage of the Scala library from Java.
 */
public class ExampleUsage {

    public static void main(String[] args) {

        VSDConnect vsd = VSDConnect.demo("demo@virtualskeleton.ch", "demo").get();

        try {
            // list folders
            VSDFolder[] folders = Await.result(vsd.listFolders(2), Duration.apply("2 seconds"));
            System.out.println("first folder " + folders[0]);

            // list unpublished objects
            VSDCommonObjectInfo[] objects = Await.result(vsd.listUnpublishedObjects(3), Duration.apply("5 seconds"));

            VSDCommonObjectInfo rawObjectInfo = null;
            for(int i =0; i < objects.length; i++) {
                System.out.println("object " + objects[i]);

                if((int) objects[i].type().get() == 1 ) rawObjectInfo = objects[i];
            }

            VSDRawImageObjectInfo info =  Await.result(vsd.getVSDObjectInfo(new VSDURL(rawObjectInfo.selfUrl()), VSDJson.VSDRawImageObjectInfoProtocol()), Duration.apply("2 seconds"));
            System.out.println("first object modality " + info.modality());


        } catch (Exception e) {
            e.printStackTrace();
        }
        vsd.shutdown();
    }

}