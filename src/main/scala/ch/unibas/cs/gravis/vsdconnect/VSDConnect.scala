package ch.unibas.cs.gravis.vsdconnect

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.Files
import java.util.zip.{ZipEntry, ZipInputStream}

import akka.actor.ActorSystem
import akka.event.Logging
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout.durationToTimeout
import ch.unibas.cs.gravis.vsdconnect.VSDJson._
import org.apache.commons.io.FileUtils
import spray.can.Http
import spray.client.pipelining
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json.RootJsonFormat
import spray.util.pimpFuture

import scala.Array.canBuildFrom
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.io.Source
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/** *
  * Class representing an authenticated session with the VSD. Once successfully created, all operations on the VSD can be performed by accessing methods of this class
  */

class VSDConnect private(user: String, password: String, BASE_URL: String) {

  implicit val system = ActorSystem(s"VSDConnect-user-${user.replace("@", "-").replace(".", "-")}")

  import system.dispatcher

  private val log = Logging(system, getClass)
  val authChannel = addCredentials(BasicHttpCredentials(user, password)) ~> sendReceive
  val objInfoChannel = authChannel ~> unmarshal[VSDCommonObjectInfo]

  /**
   * Uploads a file to the VSD
   * @return the result of a file upload containing the generated VSD file URL along with to which object it belongs (URL) or an exception in case the send failed
   */
  def uploadFile(f: File, nbRetrials: Int = 0): Future[FileUploadResponse] = {

    val pipe = authChannel ~> unmarshal[FileUploadResponse]
    val bArray = Files.readAllBytes(f.toPath)
    val req = Post(s"$BASE_URL/upload/", MultipartFormData(Seq(
      BodyPart(
        HttpEntity(ContentType(MediaTypes.`multipart/form-data`), bArray),
        HttpHeaders.`Content-Disposition`("form-data", Map("filename" -> (f.getName /*+ ".dcm"*/))) :: Nil))))

    val t = pipe(req)
    t.recoverWith {
      case e =>
        if (nbRetrials > 0) {
          println(s"Retrying file ${f.getName}, nb Retries left ${nbRetrials}")
          uploadFile(f, nbRetrials - 1)
        } else Future { throw e } // the final recover after all retrials failed
    }
  }

  /**
   * Uploads the indicated folder's content to the VSD. This method does NOT create a new folder on the VSD
   * @return a (future) collection indicating for each contained file, whether the upload succeeded or not
   *
   */
  def uploadDirectoryContentDetailed(subjDir: File): Future[List[(String, Try[FileUploadResponse])]] = {
    val listFiles = subjDir.listFiles
    val send = for (f <- listFiles) yield {
      val sendF = uploadFile(f, 2).map(t => (f.getAbsolutePath, Success(t)))
      sendF.recover{ case e =>  (f.getAbsolutePath, Failure(e)) }
    }
    Future.sequence(send.toList)
  }

  /**
   * Uploads the indicated folder's content to the VSD, with a more summarized returned information on the success of the operation. This method does NOT create a new folder on the VSD
   * @return a Future containing either the list of VSD Object URLs created as a result of the upload, or the list of file names that failed to upload
   *
   */
  def uploadDirectoryContent(subjDir: File): Future[Either[List[String], List[VSDURL]]] = {

    val summary = for {
      detailed <- uploadDirectoryContentDetailed(subjDir)

      failedFiles = detailed.filter { case (_, t) => t.isFailure }.map(_._1)
      succeeded = detailed.filter { case (_, t) => t.isSuccess }
      distinctObjectIds = detailed.filter { case (_, t) => t.isSuccess }.map(_._2.get.relatedObject).distinct
    } yield {
        if (failedFiles.isEmpty) Right(distinctObjectIds) else Left(failedFiles)
      }

    summary
  }


  /**
   * Downloads a file from the VSD given its downloadURL.
   */
  def downloadFile(url: VSDURL, downloadDir: File, fileName: String): Future[File] = {
    if (!downloadDir.isDirectory) return Future.failed(new Exception("indicated destination directory is not a directory."))
    authChannel(Get(url.selfUrl)).map { r =>
      if (r.status.isSuccess) {
        val file = new File(downloadDir.getAbsolutePath(), fileName)
        val os = new FileOutputStream(file)
        os.write(r.entity.data.toByteArray)
        os.close
        file
      } else {
        throw new Exception(r.message.toString)
      }
    }
  }

  /**
    * Retrieves information about the indicated object
    * @param url object's identifying URL
    * @tparam A expected type of Object information to be retrieved.Depending on the type of the object, this might be a [[VSDCommonObjectInfo]], [[VSDRawImageObjectInfo]], [[VSDSegmentationObjectInfo]], etc ..
    *
    *           For Java users, this method requires indicating one additional parameter (instead of the type parameter) that is the protocol for JSON serialization/deserialization of the expected returned information.
    *           This protocol variable can be found in the [[VSDJson]] object. For example, [[VSDJson.VSDCommonObjectInfoProtocol]] is the required formatter for [[VSDCommonObjectInfo]] information.
    **/
  def getVSDObjectInfo[A <: VSDObjectInfo : RootJsonFormat](url: VSDURL): Future[A] = {
    val pipe = authChannel ~> unmarshal[A]
    pipe(Get(url.selfUrl))
  }

  /**
    * Updates the information about a VSD object
    * @param info object's already updated information
    * @tparam A expected type of Object information to be updated. Depending on the type of the object, this might be a [[VSDCommonObjectInfo]], [[VSDRawImageObjectInfo]], [[VSDSegmentationObjectInfo]], etc ..
    *
    *           For Java users, this method requires indicating one additional parameter (instead of the type parameter) that is the protocol for JSON serialization/deserialization of the expected returned information.
    *           This protocol variable can be found in the [[VSDJson]] object. For example, [[VSDJson.VSDCommonObjectInfoProtocol]] is the required formatter for [[VSDCommonObjectInfo]] information.
    **/
  def updateVSDObjectInfo[A <: VSDObjectInfo : RootJsonFormat](info: A, nbRetrials: Int = 0): Future[Try[Unit]] = {

    val resp = authChannel(Put(s"$BASE_URL/objects/${info.id}", info)).map { s => Success(()) }
    resp.recoverWith {
      case e =>
        if (nbRetrials > 0) {
          println(s"Retrying object info update for ${info.id}, nb Retries left ${nbRetrials}")
          updateVSDObjectInfo(info, nbRetrials - 1)
        } else Future {
          Failure(e)
        } // the final recover after all retrials failed
    }
  }


  private def paginationRecursion[A: ClassTag](nextPage: String, nbRetrials: Int, channel: pipelining.WithTransformerConcatenation[HttpRequest, Future[VSDPaginatedList[A]]], nbRetrialsPerPage: Int = 3): Future[Array[A]] = {
    val f: Future[Array[A]] = channel(Get(nextPage)).flatMap { l =>
      val currentPageList: Array[A] = l.items
      if (l.nextPageUrl.isDefined) paginationRecursion(l.nextPageUrl.get, nbRetrialsPerPage, channel, nbRetrialsPerPage).map(nextPageList => currentPageList ++ nextPageList)
      else Future {
        currentPageList
      }
    }
    val t: Future[Array[A]] = f.recoverWith {
      case e =>
        if (nbRetrials > 0)
          paginationRecursion(nextPage, nbRetrials - 1, channel, nbRetrialsPerPage)
        else throw e
    }
    t
  }

  /**
   * Lists the ontologies supported by the VSD. See [[VSDOntology]] for more details on ontologies
   *
   */
  def listOntologies(): Future[VSDOntologies] = {
    val channel = authChannel ~> unmarshal[VSDOntologies]
    channel(Options(s"$BASE_URL/ontologies"))
  }

  /**
   * Lists the member items of a given ontology (identified by its type)
   *
   */
  def listOntologyItemsForType(typ: Int, nbRetrialsPerPage: Int = 3): Future[Array[VSDOntologyItem]] = {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDOntologyItem]]
    paginationRecursion(s"$BASE_URL/ontologies/${typ}?rpp=500", nbRetrialsPerPage, channel, nbRetrialsPerPage)
  }

  /**
   * Lists uploaded but yet unvalidated VSD objects
   *
   */
  def listUnpublishedObjects(nbRetrialsPerPage: Int = 3): Future[Array[VSDCommonObjectInfo]] = {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDCommonObjectInfo]]
    paginationRecursion(s"$BASE_URL/objects/unpublished/", nbRetrialsPerPage, channel, nbRetrialsPerPage)
  }

  /**
   * returns the list of already validated objects
   *
   */
  def listPublishedObjects(nbRetrialsPerPage: Int = 3): Future[Array[VSDCommonObjectInfo]] = {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDCommonObjectInfo]]
    paginationRecursion(s"$BASE_URL/objects/published/", nbRetrialsPerPage, channel, nbRetrialsPerPage)
  }

  /**
   * Returns details of an ontonolgy item
   */
  def getOntologyItemInfo(url: VSDURL): Future[VSDOntologyItem] = {
    val channel = authChannel ~> unmarshal[VSDOntologyItem]
    channel(Get(url.selfUrl))
  }

  /**
   * Creates an association between a VSD Object (be it validated or not), with an ontology item. In normal words, this allows to say
   * that the object depicts a certain organ.
   *
   * @param objectURL url of the object
   * @param ontologyItemURL url of the ontology item
   * @return
   */
  def createObjectOntologyItemRelation(objectURL: VSDURL, ontologyItemURL: VSDURL): Future[VSDObjectOntologyItem] = {
    val channel = authChannel ~> unmarshal[VSDObjectOntologyItem]
    getOntologyItemInfo(ontologyItemURL).flatMap { ontologyItemInfo =>
      val newRelation = VSDObjectOntologyItem(1, 0, ontologyItemInfo.`type`, objectURL, ontologyItemURL, "")
      channel(Post(s"$BASE_URL/object-ontologies/${ontologyItemInfo.`type`}", newRelation))
    }
  }

  /**
   * Updated an association between a VSD Object (be it validated or not), with an ontology item. In normal words, this allows to say
   * that the object depicts a certain organ.
   *
   * @param relation existing relation to be updated
   * @param ontologyItemURL url of the ontology item
   * @return
   */
  def updateObjectOntologyItemRelation(relation: VSDObjectOntologyItem, objectInfo: VSDObjectInfo, ontologyItemURL: VSDURL): Future[VSDObjectOntologyItem] = {
    val channel = authChannel ~> unmarshal[VSDObjectOntologyItem]
    val position = objectInfo.ontologyItemRelations.map(_.size).getOrElse(0)
    getOntologyItemInfo(ontologyItemURL).flatMap { ontologyItemInfo =>
      val newRelation = VSDObjectOntologyItem(relation.id, position, ontologyItemInfo.`type`, VSDURL(objectInfo.selfUrl), ontologyItemURL, "")
      channel(Put(s"$BASE_URL/object-ontologies/${ontologyItemInfo.`type`}/${relation.id}", newRelation))
    }
  }

  /**
   * Deletes the indicated unpublished object from the VSD
   */
  def deleteUnpublishedVSDObject(url: VSDURL): Future[Try[Unit]] = {
    val channel = authChannel
    channel(Delete(url.selfUrl)).map { r =>
      if (r.status.isSuccess) Success(()) else Failure(new Exception(s"failed to delete unpublished vsd object ${url.selfUrl}" + r.entity.toString()))
    }
  }


  private def unZipIt(zipFile: File, outputFolder: File): File = {

    val buffer = new Array[Byte](1024)

    //zip file content
    val zis: ZipInputStream = new ZipInputStream(new FileInputStream(zipFile));
    //get the zipped file list entry
    var ze: ZipEntry = zis.getNextEntry();

    val unzippedDir = new File(outputFolder + File.separator +  ze.getName.split(File.separator).head)

    while (ze != null) {

      val fileName = ze.getName();
      val newFile = new File(outputFolder + File.separator + fileName);

      //create folders
      new File(newFile.getParent()).mkdirs();

      val fos = new FileOutputStream(newFile);

      var len: Int = zis.read(buffer);

      while (len > 0) {

        fos.write(buffer, 0, len)
        len = zis.read(buffer)
      }

      fos.close()
      ze = zis.getNextEntry()
    }

    zis.closeEntry()
    zis.close()
    unzippedDir
  }

  /**
   * Downloads the indicated VSD object into the indicated directory. Objects downloaded from the VSD are always shipped as Zip files
   */
  def downloadVSDObject(url: VSDURL, downloadDir: File): Future[File] = {
    val r = getVSDObjectInfo[VSDCommonObjectInfo](url).flatMap { info =>
      val f = File.createTempFile("temporaryVSDobj", ".zip")
      val name = f.getName ; f.delete
      downloadFile(VSDURL(info.downloadUrl), downloadDir, name)
    }

    r.map { f =>
      Try {unZipIt(f,downloadDir) } match {
        case Success(s) => f.delete; s
        case Failure(e) => f.delete; throw new Exception("failed to unzip downloaded object : " + e.getMessage)
      }
    }
  }

  /**
   * Returns a string indicating the anatomical side of the VSDObject.
   * Possible values : Right, Left, None if the object is unique
   * */
  def getAnatomicalSide(objUrl: VSDURL): Future[Option[String]] = {
    for {
      info <- getVSDObjectInfo[VSDCommonObjectInfo](objUrl)
      onto <- getOntologyItemInfo(info.ontologyItems.get.head)
    } yield {
      val t = onto.term.split(" ").headOption

      if(t.isDefined && t.get != "Left" && t.get != "Right")
          None
      else t
    }
  }

  /**
   * Lists folders on the VSD
   *
   */
  def listFolders(nbRetrialsPerPage: Int = 3): Future[Array[VSDFolder]] = {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDFolder]]
    paginationRecursion(s"$BASE_URL/folders", nbRetrialsPerPage, channel, nbRetrialsPerPage)
  }

  /**
   * Gets information of a given folder
   */
  def getFolderInfo(url: VSDURL): Future[VSDFolder] = {
    val channel = authChannel ~> unmarshal[VSDFolder]
    channel(Get(url.selfUrl))
  }

  /**
   * Gets object ontology item relation
   */
  def getObjectOntologyItem (url: VSDURL): Future[VSDObjectOntologyItem] = {
    val channel = authChannel ~> unmarshal[VSDObjectOntologyItem]
    channel(Get(url.selfUrl))
  }



  private def createFolder(name: String, parentFolder: VSDFolder): Future[VSDFolder] = {
    val channel = authChannel ~> unmarshal[VSDFolder]
    val folderModel = VSDFolder(1, name, parentFolder.level + 1, Some(VSDURL(parentFolder.selfUrl)), None, None, None, None, "dummy")
    channel(Post(s"$BASE_URL/folders", folderModel))
  }

  /**
   * Creates a folder with the given name under the indicated parent folder
   *
   */
  def createFolder(name: String, parentFolder: VSDURL): Future[VSDFolder] = {
    val channel = authChannel ~> unmarshal[VSDFolder]
    for {
      parentFolder <- getFolderInfo(parentFolder)
      res <- createFolder(name: String, parentFolder)
    } yield {
      res
    }
  }



  /**
   * Deletes the indicated folder from the VSD
   *
   */
  def deleteFolder(url: VSDURL): Future[Try[Unit]] = {
    val channel = authChannel
    channel(Delete(url.selfUrl)).map { r =>
      if (r.status.isSuccess) Success(()) else Failure(new Exception(s"failed to delete directory ${url.selfUrl}" + r.entity.toString()))
    }
  }


  /**
   * Adds a VSD object to an existing folder
   *
   * @return the updated information of the folder
   *
   */
  def addObjectToFolder(obj: VSDURL, folder: VSDFolder): Future[VSDFolder] = {
    val channel = authChannel ~> unmarshal[VSDFolder]
    val folderModel = folder.copy(containedObjects = Some(folder.containedObjects.getOrElse(Seq[VSDURL]()) :+ obj))
    channel(Put(s"$BASE_URL/folders", folderModel))
  }


  /**
   * Removes a VSD object from an existing folder
   *
   * @return the updated information of the folder
   */
  def removeObjectFromFolder(obj: VSDURL, folder: VSDFolder): Future[VSDFolder] = {
    val channel = authChannel ~> unmarshal[VSDFolder]
    val r = folder.containedObjects.getOrElse(Seq[VSDURL]()).filterNot(l => l == obj)
    val folderModel = folder.copy(containedObjects = if (r.isEmpty) None else Some(r))
    channel(Put(s"$BASE_URL/folders", folderModel))
  }

  /**
   * Introduced this recursive download at some point to avoid using Future.sequence that triggers all file downloads in parallel and
   * can result in too much overhead for the server and potential timeouts.
   *
   * This has however the disadvantage of making operations such as downloading a folder much slower as it is now done sequentially
   * */
   private def sequentialObjectDownload(urlList : Seq[VSDURL], tempDirectory : File, partialRes : Seq[(VSDCommonObjectInfo, File)] ):  Future[Seq[(VSDCommonObjectInfo, File)]] = {
    if(urlList.isEmpty == false) {
      val url = urlList.head
      println("downloading object " + url)
      for{
        info <- objInfoChannel(Get(url.selfUrl))
        dl <- downloadVSDObject(url, tempDirectory)
        others <- sequentialObjectDownload(urlList.tail, tempDirectory, partialRes :+ (info, dl) )
      } yield others

    }
    else Future.successful(partialRes)
  }

  /** *
    * This method is intended as an alternative to Future.sequence(vsd.downloadVSDObject(..)). In better words, this takes a list of VSDURLs
    * and download the corresponding objects ONE by ONE and stops at the first failure. In contrast, Future.sequence, would trigger all download Futures
    * in parallel which might result in a too heavy load and eventually timeouts.
    *
    *
    * @return list of downloaded objects informations and corresponding file.
    */
  def sequentiallyDownloadObjects(urlList : Seq[VSDURL], destDirectory : File):  Future[Seq[(VSDCommonObjectInfo, File)]] = {
    sequentialObjectDownload(urlList, destDirectory, Seq[(VSDCommonObjectInfo, File)]())
  }

  /**
   * Downloads the content of the folder to the indicated File destination. If the destination folder already exists in the file system, the function will abort,
   * in order to avoid overwriting existing data.
   * In case the VSD folder contains sub-folders, this call will result in a recursion
   *
   * @return List of downloaded VSDObjectInfos along with their corresponding File.
   *
   *         The downloaded files are first stored in a temporary directory, and only on success of all files moved to the indicated destination.
   **/
  def downloadFolder(folder: VSDFolder, destination: File): Future[Seq[(VSDCommonObjectInfo, File)]] = {
    val infoChannel = authChannel ~> unmarshal[VSDFolder]


    if (destination.isDirectory()) return Future.failed(new Exception("Indicated folder already exists"))

    val tempDestination = File.createTempFile("VSDFolder_" + folder.id, "")
    tempDestination.delete();
    tempDestination.mkdir()

     // If any subdirectories, we recurse first
    val downloadedObjsInSubdirsF = Future.sequence(folder.childFolders.getOrElse(Seq[VSDURL]()).map { sub =>
      for {
        info <- infoChannel(Get(sub.selfUrl))
        subFolderList <- downloadFolder(info, new File(tempDestination.getAbsolutePath + File.pathSeparator + info.name))
      } yield (subFolderList)
    }).map(_.flatten)

    // now download the objects contained in the file itself
    val downloadedObjsInFolderF = sequentialObjectDownload(folder.containedObjects.getOrElse(Seq[VSDURL]()), tempDestination, Seq[(VSDCommonObjectInfo, File)]())

    for {
      downloadedObjsInSubdirs <- downloadedObjsInSubdirsF
      downloadedObjsInFolder <- downloadedObjsInFolderF
    } yield {
      val r = downloadedObjsInSubdirs ++ downloadedObjsInFolder
      // copy the temp dir into destination
      destination.delete()
      FileUtils.moveDirectory(tempDestination, destination)
      // replace the file paths with the new one
      r.map { case (o, f) => (o, new File(f.getAbsolutePath.replace(tempDestination.getAbsolutePath, destination.getAbsolutePath))) }
    }
  }


  /**
   * Gets user information
   */
  def getUserInfo(url: VSDURL): Future[VSDUser] = {
    val channel = authChannel ~> unmarshal[VSDUser]
    channel(Get(url.selfUrl))
  }

  /**
   * Properly shuts down the client
   */
  def shutdown(): Unit = {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }


  /**
   * Creates a link between 2 VSD objects
   **/
  def addLink(object1: VSDURL, object2: VSDURL): Future[VSDLink] = {
    val channel = authChannel ~> unmarshal[VSDLink]
    val l = VSDLink(0, "dummy", object1, object2, "dummy")
    channel(Post(s"$BASE_URL/object-links", l))
  }


  /**
   * Gets link information
   **/
  def getLinkInfo(url: VSDURL): Future[VSDLink] = {
    val channel = authChannel ~> unmarshal[VSDLink];
    channel(Get(url.selfUrl))
  }


  /**
   * Deletes an existing link
   **/
  def deleteLink(url: VSDURL): Future[Try[Unit]] = {
    val channel = authChannel
    channel(Delete(url.selfUrl)).map { r =>
      if (r.status.isSuccess) Success(()) else Failure(new Exception(s"failed to delete vsd link  ${url.selfUrl}" + r.entity.toString()))
    }
  }

  /**
   * Lists modalities supported by the VSD
   */
  def listModalities(): Future[Array[VSDModality]] = {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDModality]]
    paginationRecursion(s"$BASE_URL/modalities", 3, channel, 3)
  }


  /**
   * Get a specific modality
   */
  def getModality(url : VSDURL) : Future[VSDModality] = {
    val channel = authChannel ~> unmarshal[VSDModality];
    channel(Get(url.selfUrl))
  }

  /**
   * Lists supported segmentation method
   */
  def listSegmentationMethods() = {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDSegmentationMethod]]
    paginationRecursion(s"$BASE_URL/segmentation_methods", 3, channel, 3)
  }

  /**
   * Lists the types of objects supported by the VSD (e.g. Raw, Segmentation, ..)
   */
  def listObjectTypes(): Future[Seq[(Int, String)]] = {
    println("Listing object types")
    val channel = authChannel ~> unmarshal[VSDObjectOptions]
    channel(Options(s"$BASE_URL/objects")).map {
      _.types.map(kv => (kv.key, kv.value))
    }
  }

  /**
   * Publishes (validates) an object
   *
   */
  def publishObject(url: VSDURL): Future[VSDCommonObjectInfo] = {
    val channel = authChannel ~> unmarshal[VSDCommonObjectInfo]
    for {
      info <- getVSDObjectInfo[VSDCommonObjectInfo](url)
      res <- channel(Put(s"$BASE_URL/objects/${info.id}/publish"))
    } yield res
  }

  /**
   * Sets group rights for a VSD object
   *
   * The rights are collection of the pre-defined objects :
   * [[VSDNoneRight]], [[VSDReadRight]], [[VSDEditRight]], [[VSDVisitRight]],[[VSDManageRight]],[[VSDDownloadRight]], [[VSDOwnerRight]]
   **/

  def setObjectGroupRights(obj: VSDURL, group: VSDURL, rights: Seq[VSDObjectRight]): Future[VSDObjectGroupRight] = {
    val channel = authChannel ~> unmarshal[VSDObjectGroupRight]
    val req = VSDObjectGroupRight(0, obj, group, rights.map(r => VSDURL(r.selfUrl)), "dummy")
    channel(Post(s"$BASE_URL/object-group-rights", req))
  }


  /**
   * Sets user rights for a VSD object
   *
   * The rights are collection of the pre-defined objects :
   * [[VSDNoneRight]], [[VSDReadRight]], [[VSDEditRight]], [[VSDVisitRight]],[[VSDManageRight]],[[VSDDownloadRight]], [[VSDOwnerRight]]
   **/

  def setObjectUserRights(obj: VSDURL, user: VSDURL, rights: Seq[VSDObjectRight]): Future[VSDObjectUserRight] = {
    val channel = authChannel ~> unmarshal[VSDObjectUserRight]
    val req = VSDObjectUserRight(0, obj, user, rights.map(r => VSDURL(r.selfUrl)), "dummy")
    channel(Post(s"$BASE_URL/object-user-rights", req))
  }


  /**
    * Get a list of groups
    */
  def listGroups(): Future[Array[VSDGroup]] = {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDGroup]]
    paginationRecursion(s"$BASE_URL/groups", 3, channel, 3)
  }

  /**
    * Returns the details of an object group right relation, given its url
    *
    */
  def getObjectGroupRight(url: VSDURL): Future[VSDObjectGroupRight] = {
    val channel = authChannel ~> unmarshal[VSDObjectGroupRight]
    channel(Get(url.selfUrl))
  }


  /**
    * Returns the details of an object user right relation, given its url
    *
    */
  def getObjectUserRight(url: VSDURL): Future[VSDObjectUserRight] = {
    val channel = authChannel ~> unmarshal[VSDObjectUserRight]
    channel(Get(url.selfUrl))
  }


  /**
   * Returns a String representing the hierarchical path to the indicated folder
   *
   */
  def getFolderPath(url: VSDURL): Future[String] = {
    getFolderInfo(url).flatMap { info =>
      if (info.parentFolder.isEmpty) Future {
        "/" + info.name
      }
      else getFolderPath(info.parentFolder.get).map(p => p + "/" + info.name)
    }
  }


  /**
   * Returns a VSDFolder given its hierarchical path (if it exists, None otherwise)
   */
  def getFolderFromPath(path: String): Future[Option[VSDFolder]] = {

    val lastName = path.split('/').last
    val matchingF = listFolders().map { l => l.filter(_.name == lastName) }

    // for all folders matching the last name, get their full path and compare it to requested
    val matchingPathsF = matchingF.flatMap { matching => Future.sequence(matching.toIndexedSeq.map(i => getFolderPath(VSDURL(i.selfUrl)).map(pth => (i, pth)))) }
    matchingPathsF.map { matchingPaths => matchingPaths.find(pair => pair._2 == path).map(_._1) }
  }

  /**
   * Returns a list of ontology items containing the given string
   *
   */

  def findOntologyItemsContaining(string: String) : Future[IndexedSeq[VSDOntologyItem]] = {
    for {
      ontologies <- listOntologies()
      filteredOntologyItems <- Future.sequence(ontologies.types.toIndexedSeq.map { o =>
         listOntologyItemsForType(o.key)
      }).map(_.flatten.filter(it => it.term.contains(string)))
    } yield filteredOntologyItems
  }


}

/**
 * Factory for VSDConnect class
 */
object VSDConnect {

  private def connect(username: String, password: String, BASE_URL: String): Try[VSDConnect] = {
    val conn = new VSDConnect(username, password, BASE_URL)
    implicit val system = conn.system
    implicit val ex = system.dispatcher

    conn.authChannel(Get(s"$BASE_URL/objects/unpublished/")).map { r =>
      if (r.status.isSuccess) {
        Success(conn)
      } else {
        println(r)
        Failure(new Exception("Login unsuccessful"))
      }
    }.await
  }

  /**
   * Creates a VSDConnect object given the indicated credentials
   *
   * @return a Successfull VSDConnect object is only returned if the authentication and session establishment succeeded.
   *         Otherwise, a failure is returned
   *
   */
  def apply(username: String, password: String): Try[VSDConnect] = {
    val BASE_URL = "https://www.virtualskeleton.ch/api"
    connect(username, password, BASE_URL)
  }

  /**
   * Factory method that avoids writing credentials in source code. The indicated file must contain the login on the first line
   * and the password on the second
   *
   * @return a Successfull VSDConnect object is only returned if the authentication and session establishment succeeded.
   *         Otherwise, a failure is returned
   */
  def apply(credentialsFile: File): Try[VSDConnect] = {
    val lines = Source.fromFile(credentialsFile).getLines().toIndexedSeq
    val login = lines(0);
    val pass = lines(1)
    apply(login, pass)
  }

  /**
   * Factory method to create a VSD session to the demo server, This is used for unit tests, and can as well be used for sandbox trials
   *
   * @return a Successfull VSDConnect object is only returned if the authentication and session establishment succeeded.
   *         Otherwise, a failure is returned
   */
  def demo(username: String, password: String): Try[VSDConnect] = {
    val BASE_URL = "https://demo.virtualskeleton.ch/api"
    connect(username, password, BASE_URL)
  }

  private def printStep(resp: HttpResponse): HttpResponse = {
    println("*** Response : " + resp.entity.asString)
    resp
  }
}