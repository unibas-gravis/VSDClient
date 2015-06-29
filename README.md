#VSDConnect

VSDConnect is a library providing  a client to the [REST interface](https://www.virtualskeleton.ch/api/Help) provided by the [Virtual Skeleton Database (VSD)](https://www.virtualskeleton.ch/)


The vision of the project is to provide an environment where :

* The VSD concepts, such as Objects, Folders, Links, Modalities, Ontologies, etc.. are implemented in classes against which the user can program and script
* The set of JSON serialization/deserialization protocols allowing to communicate with the VSD REST interface are already implemented and allow to retrieve and push the classes mentioned above

* Recurrent tasks such as uploading a Dicom directory, downloading a folder, creating object links etc.. are already implemented in an intuitive manner. 

The VSDConnect is intended to be used as a Software library to be included in the user's software project.

Although the library is written in [Scala](http://www.scala-lang.org/) (which of course makes it accessible to Scala projects), it is compatible with any JVM-based language, and **can notably be used from wihtin Java projects.**


## Using VSDConnect

To use VSDConnect in your own project, you can either : add the following lines to your build.sbt
```
libraryDependencies  ++= Seq(
            // other dependencies here
            "ch.unibas.cs.gravis" %% "vsdconnect" % "0.7.+"            
)
```

### Building VSDConnect 
To build VSDConnect, run ```sbt``` and then any of the following commands

* ```compile```: Compile sbt
* ```test```: Run the unit test
* ```doc```: Generate the api docs
* ```scalastyle```: Run the style checker


#### Publishing and packaging locally
You can publish the VSDConnect to your local ivy repository by running ```publishLocal``` into sbt.

The project can also be easily assembled into a single jar by running ```assembly``` into sbt.

## Participation

While VSDConnect is already usable for most common tasks on the VSD such as upload, download, setting object information, etc.., its API functionality does not yet encompass the full scope of possible actions on the VSD.

If you find VSDConnect useful for your work, you can help us to make it more complete by implementing missing features, in particular actions that reveal to be recurrent and could be added to the API.

We are also always grateful if you report bugs or give us feedback on how you use VSDConnect in your work and how you think we can improve it. 

## Maintainers
The project is currently developed and maintained by the Graphics and Vision Research group, University of Basel. 
The current maintainers of the project (people who can merge pull requests) are: 

* [Ghazi Bouabene](https://github.com/ghazi-bouabene)


## Related Projects
VSDConnect is closely related to the [Virtual Skeleton Database project](https://www.virtualskeleton.ch) as it seeks to implement the concepts and actions permitted by its REST interface.

VSDConnect can also be combined with the [Scalismo](https://github.com/unibas-gravis/scalismo) project that allows to read and process data downloaded from the VSD. In fact some of the developers of VSDConnect are also involved in Scalismo.

## Quick Start : 

### Creating a session with the VSD:
```scala
val vsd = VSDConnect("login", "password").get
```
This call will succeed only if the credentials are valid. Once you obtained such a VSDConnect instance, all of your interaction with the VSD can be done via the methods of this object.

In case you do not wish to write your credentials into the code directly, you can use another factory method for the VSDConnect object, taking as a parameter a credentials file. This is a normal file containing the login on the first line and the password on the second. You can then change the rights on the credentials file to be its only reader.


### Interactions with the VSD : Handling Asynchronicity

Interactions with a remote service are asynchronous by nature. Therefore all of the methods of the VSDConnect return a Future of the expected response type, as can be seen in the methods signatures.

An intuitive explanation for a Future in scala is simply as a wrapper for the result of an asynchronous task. Depending on whether the task is completed succesfully or not, the Future might either contain the result of the task, or an Exception indicating what went wrong. 

There are mainly three ways of dealing with Future's result: 

1- Use functions such as *map*, *flatMap*, and *recover* to operate on the Future's result.

2- Define callbacks to be triggered on the success or the failure of the asynchronous task

3- Explicitely block and wait for the completion of the Future, i.e. make the code synchronous

#### Chaining Futures
You can choose any of the methods above to handle the results. A recommended way however to combine several asynchronous tasks that are dependent sequentially is to use the first method combined with a *for comprehension*. Below is an example where we first retrieve the information of a folder, list its content, retrieve the information of its first contained object and finally update its description : 

```scala
val combinedFuture = for {
    folder <- vsd.getFolderFromPath("/demo/MyProjects/exampleFolder") // get folder info
    firstObjectURL = folder.get.containedObjects.get(0)  // get URL of first contained object
    info <- vsd.getVSDObjectInfo[VSDCommonObjectInfo](firstObjectURL)  // get the object's information
    updatedInfoStatus <- vsd.updateVSDObjectInfo( info.copy(description = Some("new description"))) // update the object's description
} yield updatedInfoStatus
```

Checkout this [official tutorial on Futures](http://docs.scala-lang.org/overviews/core/futures.html) for more details.

### Uploading data :

All uploaded data to the VSD via the REST interface, are first marked as "Unvalidated Data" or "Unpublished Data". 

**Attention: Published data cannot be deleted from the VSD. Only Unpublished data can.** 

#### Uploading a single file
```scala
val r : Future[FileUploadResponse]= vsd.sendFile(new File(path), 5)
```
This method returns a Future of a FileUploadResponse. Once the Future completed, this operation will either contain the successfull answer from the VSD or an Exception with a message indicating what went wrong.

The response from the VSD on a file upload is of type FileUpload Response that indicates:

1- The url of the uploaded file. 

2- The url of the VSD Object created as a result of this file upload.

The difference between a file and an object on the VSD can be best explained with Dicom files. In a case where a patient scan is uploaded as a Dicom, each Dicom file will be considered as a VSD File, while all files are related to one VSD Object that is the raw image representation of the patient.

Each VSD entity, be it an object, a file, an ontology etc. is uniquely identified and referred to with its URL.

These returned URLs are then used throughout the rest of the API to identify and operate on VSD objects.

#### Uploading a directory content (example Dicom directory)

```scala
val result : Future[Either[List[String], List[VSDURL]]] = vsd.sendDirectoryContent(new File(path))
```
The return type of this function might seem complicated, but in fact it is very simple. On completion, the Future will contain :

* Either a list of urls corresponding to the created VSD Objects due to this operation, in case all files were uploaded successfully. In case of success, this list can be accessed using ```result.right```.

* Or the list of file names of the files that failed to upload in case of Failure. In case of failure, this list can be accessed using ```result.left```.

Checkout the documentation of [Scala Either](http://www.scala-lang.org/api/rc2/scala/Either.html) for more details.

Important : This method does NOT create a folder into the VSD. It simply uploads the content of a folder on your file system.

### Listing content on the VSD : 
##### For objects 
```scala
val listUnpublished  = vsd.listUnpublishedObjects() // lists unpublished objects 
val listPublished : Future[Array[VSDCommonObjectInfo]] = vsd.listPublishedObjects() // list published objects
```

Both methods return the list containing information about all published, or unpublished VSD objects.

##### For folders 
```scala
val dirs :Future[Array[VSDFolder]] = vsd.listFolders()
```
This returns a list of informations about all the folders of the user on the VSD. Notice that this does not have hierarchical bounds and will return each and every sub-folder of a sub-folder.

The folder information of type VSDFolder contains information about the hierarchy-level, parent directory and contained sub-folders and objects.

In addition to the global list, information about a folder can be obtained by specifying the hierarchical path as seen on the VSD : 

```scala
val folder : Future[VSDFolder] = vsd.getFolderFromPath( "/userName/MyProjects/exampleFolder")
```
This will return the information about the folder if it exists.


### Download

##### For objects : 

Once you have the download url of an object you wish to download (obtained by either listing contents or folders) : 

```scala
val objFile : Future[File] = vsd.downloadVSDObject(objectURL, destinationDir, "objectName.zip")
```
Notice that the VSD ships all downloaded objects in one zip. This means, whether the desired object is a Dicom scan or a Nifti volume, in both cases a zip file is downloaded from the VSD.

##### For folders : 

It is also possible to download the content of folders once you have the information of the folder 

```scala 
val resultList : Future[Seq[(VSDCommonObjectInfo, File)]] = downloadFolder(folder: VSDFolder, destination: File)
```
On success, this returns the list of the downloaded VSD objects contained in the directory (and in its sub-directories) as well as the File in which each object was downloaded.



#### Usage from Java : 

Although the library is written in Scala, it can be easily accessed from within a Java program. Below is an example creating a session and performing several dependendent tasks, this time however by blocking for every Asynchronous call : 
```java 
public class ExampleUsage {

    public static void main(String[] args) {

        VSDConnect vsd = VSDConnect.demo("demo@virtualskeleton.ch", "demo").get();

        try {
            // list folders
            VSDFolder[] folders = Await.result(vsd.listFolders(2), Duration.apply("2 seconds"));
            System.out.println("first folder " + folders[0]);

            // list unpublished objects
            VSDCommonObjectInfo[] objects = Await.result(vsd.listUnpublishedObjects(3), Duration.apply("5 seconds"));

            // find the first contained object of type Raw	
            VSDCommonObjectInfo rawObjectInfo = null;
            for(int i =0; i < objects.length; i++) {
                System.out.println("object " + objects[i]);
                if((int) objects[i].type().get() == 1 ) rawObjectInfo = objects[i];
            }

            // retrieve raw image information
            VSDRawImageObjectInfo info =  Await.result(vsd.getVSDObjectInfo(new VSDURL(rawObjectInfo.selfUrl()), VSDJson.VSDRawImageObjectInfoProtocol()), Duration.apply("2 seconds"));
            
            // printout the modality
            System.out.println("first object modality " + info.modality());


        } catch (Exception e) {
            e.printStackTrace();
        }
        vsd.shutdown();
    }

}
```

#### Further functionality : 

Many other functionalities are possible using VSDConnect and go beyond the scope of this quick-start : 

* creating links between objects (example: link segmentation object to its original raw image)
* update information of an object (example : assign it a particular ontology item to declare which organ it depicts)
* assign rights to users and groups on objects and folders 
* creating folders and moving objects to them
* support for information of different types of objects (raw image, segmentation, statstical model)
* ...

To learn more about those, please check the API doc of the methods of VSDConnect class.

## Copyright and License

Copyright, University of Basel, 2015.
