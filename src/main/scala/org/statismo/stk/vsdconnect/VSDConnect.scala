package org.statismo.stk.vsdconnect

import java.io.{File, FileOutputStream}
import java.nio.file.Files

import akka.actor.ActorSystem
import akka.event.Logging
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout.durationToTimeout
import org.apache.commons.io.FileUtils
import org.statismo.stk.vsdconnect.VSDJson._
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

class VSDConnect private (user: String, password: String, BASE_URL: String) {

  implicit val system = ActorSystem(s"VSDConnect-user-${user.replace("@", "-").replace(".", "-")}")
  import system.dispatcher

  private val log = Logging(system, getClass)
  val authChannel =  addCredentials(BasicHttpCredentials(user, password)) ~> sendReceive

  /**
   * Performs an file upload to the VSD
   * @return the result of a file upload containing the generated VSD file URL along with to which object it belongs (URL) or an exception in case the send failed
   */
  def sendFile(f: File, nbRetrials: Int = 0): Future[Try[FileUploadResponse]] = {

    val pipe = authChannel ~> unmarshal[FileUploadResponse]
    val bArray = Files.readAllBytes(f.toPath)
    val req = Post(s"$BASE_URL/upload/", MultipartFormData(Seq(
      BodyPart(
        HttpEntity(ContentType(MediaTypes.`multipart/form-data`), bArray),
        HttpHeaders.`Content-Disposition`("form-data", Map("filename" -> (f.getName /*+ ".dcm"*/))) :: Nil))))

    val t = pipe(req) map { r => Success(r) }
    t.recoverWith {
      case e =>
        if (nbRetrials > 0) {
          println(s"Retrying file ${f.getName}, nb Retries left ${nbRetrials}")
          sendFile(f, nbRetrials - 1)
        } else Future { Failure(e) } // the final recover after all retrials failed
    }
  }

  /**
   * Uploads the indicated folder's content to the VSD
   * @return a (future) collection indicating for each contained file, whether the upload succeeded or not
   *
   */
  def sendDirectoryContentDetailed(subjDir: File): Future[List[(String, Try[FileUploadResponse])]] = {
    val listFiles = subjDir.listFiles
    val send = for (f <- listFiles) yield { sendFile(f, 2).map(t => (f.getAbsolutePath(), t)) }
    Future.sequence(send.toList)
  }

  /**
   * Uploads the indicated folder's content to the VSD, with a more summarized returned information on the success of the operation
   * @return a Future containing either the list of VSD Object URLs created as a result of the upload, or the list of file names that failed to upload
   *
   */
  def sendDirectoryContent(subjDir: File): Future[Either[List[String], List[VSDURL]]] = {

    val summary = for {
      detailed <- sendDirectoryContentDetailed(subjDir)

      failedFiles = detailed.filter { case (_, t) => t.isFailure }.map(_._1)
      succeeded = detailed.filter { case (_, t) => t.isSuccess }
      distinctObjectIds = detailed.filter { case (_, t) => t.isSuccess }.map(_._2.get.relatedObject).distinct
    } yield {
      if (failedFiles.isEmpty) Right(distinctObjectIds) else Left(failedFiles)
    }

    summary
  }

  /**
   * Downloads a file from the VSD given its downloadURL
   */
  def downloadFile(url: VSDURL, downloadDir: File, fileName: String): Future[Try[File]] = {
    if(!downloadDir.isDirectory) return Future.failed(new Exception("indicated destination directory is not a directory."))
    authChannel(Get(url.selfUrl)).map { r =>
      if (r.status.isSuccess) {
        val file = new File(downloadDir.getAbsolutePath(), fileName)
        val os = new FileOutputStream(file)
        os.write(r.entity.data.toByteArray)
        os.close
        Success(file)
      } else {
        Failure(new Exception(r.message.toString))
      }
    }
  }

  def getVSDObjectInfo[A <: VSDObjectInfo : RootJsonFormat](url : VSDURL) : Future[A] = {
    val pipe = authChannel ~> unmarshal[A]
    pipe(Get(url.selfUrl))
  }


  def updateVSDObjectInfo[A <: VSDObjectInfo: RootJsonFormat](info: A, nbRetrials: Int = 0): Future[Try[HttpResponse]] = {

    val resp = authChannel(Put(s"$BASE_URL/objects/${info.id}", info)).map { s => Success(s) }
    resp.recoverWith {
      case e =>
        if (nbRetrials > 0) {
          println(s"Retrying object info update for ${info.id}, nb Retries left ${nbRetrials}")
          updateVSDObjectInfo(info, nbRetrials - 1)
        } else Future { Failure(e) } // the final recover after all retrials failed
    }
  }


  private def paginationRecursion[A : ClassTag](nextPage: String, nbRetrials: Int,  channel : pipelining.WithTransformerConcatenation[HttpRequest, Future[VSDPaginatedList[A]]], nbRetrialsPerPage: Int = 3): Future[Array[A]] = {
    val f : Future[Array[A]] = channel(Get(nextPage)).flatMap { l =>
      val currentPageList : Array[A] = l.items
      if (l.nextPageUrl.isDefined) paginationRecursion(l.nextPageUrl.get, nbRetrialsPerPage, channel, nbRetrialsPerPage).map(nextPageList => currentPageList ++ nextPageList ) else Future { currentPageList }
    }
    val t : Future[Array[A]]= f.recoverWith {
      case e =>
        if (nbRetrials > 0)
          paginationRecursion(nextPage, nbRetrials - 1, channel, nbRetrialsPerPage)
        else throw e
    }
    t
  }

  def listOntologies(): Future[VSDOntologies] = {
    val channel = authChannel ~> unmarshal[VSDOntologies]
    channel(Options(s"$BASE_URL/ontologies"))
  }

  def listOntologyItemsForType(typ: Int, nbRetrialsPerPage: Int = 3): Future[Array[VSDOntologyItem]] = {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDOntologyItem]]
    paginationRecursion(s"$BASE_URL/ontologies/${typ}/", nbRetrialsPerPage,channel, nbRetrialsPerPage)
  }

  def listUnpublishedObjects(nbRetrialsPerPage: Int = 3): Future[Array[VSDCommonObjectInfo]] = {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDCommonObjectInfo]]
    paginationRecursion(s"$BASE_URL/objects/unpublished/", nbRetrialsPerPage,channel, nbRetrialsPerPage)
  }

  /**
   * returns the list of already validated objects
   *
   */
  def listPublishedObjects(nbRetrialsPerPage: Int = 3) : Future[Array[VSDCommonObjectInfo]] = {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDCommonObjectInfo]]
    paginationRecursion(s"$BASE_URL/objects/published/", nbRetrialsPerPage,channel, nbRetrialsPerPage)
  }

  def getOntologyItemInfo(url: VSDURL): Future[VSDOntologyItem] = {
    val channel = authChannel ~> unmarshal[VSDOntologyItem]
    channel(Get(url.selfUrl))
  }


  def createObjectOntologyItemRelation(objectURL: VSDURL, ontologyItemURL: VSDURL): Future[VSDObjectOntologyItem] = {
    val channel = authChannel ~> unmarshal[VSDObjectOntologyItem]
    getOntologyItemInfo(ontologyItemURL).flatMap { ontologyItemInfo =>
      val newRelation = VSDObjectOntologyItem(1, 0, ontologyItemInfo.`type`, objectURL, ontologyItemURL, "")
      channel(Post(s"$BASE_URL/object-ontologies/${ontologyItemInfo.`type`}", newRelation))
    }
  }

  def updateObjectOntologyItemRelation(relation: VSDObjectOntologyItem, objectInfo: VSDObjectInfo, ontologyItemURL: VSDURL): Future[VSDObjectOntologyItem] = {
    val channel = authChannel ~> unmarshal[VSDObjectOntologyItem]
    val position = objectInfo.ontologyItemRelations.map(_.size).getOrElse(0)
    getOntologyItemInfo(ontologyItemURL).flatMap { ontologyItemInfo =>
      val newRelation = VSDObjectOntologyItem(relation.id, position, ontologyItemInfo.`type`, VSDURL(objectInfo.selfUrl), ontologyItemURL, "")
      channel(Put(s"$BASE_URL/object-ontologies/${ontologyItemInfo.`type`}/${relation.id}", newRelation))
    }
  }

  def deleteUnpublishedVSDObject(url : VSDURL): Future[Try[Unit]] = {
    val channel = authChannel
    channel(Delete(url.selfUrl)).map { r =>
      if (r.status.isSuccess) Success(()) else Failure(new Exception(s"failed to delete unpublished vsd object ${url.selfUrl}" + r.entity.toString()))
    }
  }


  /**
   * Download of object is always shipped in one zip file
   */
  def downloadVSDObject(url: VSDURL, downloadDir: File, fileName: String): Future[Try[File]] = {
      getVSDObjectInfo[VSDCommonObjectInfo](url).flatMap { info =>
        downloadFile(VSDURL(info.downloadUrl), downloadDir, fileName)
      }
  }


    /**
     * Lists folders
     *
     */
  def listFolders(nbRetrialsPerPage: Int = 3):  Future[Array[VSDFolder]] = {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDFolder]]
      paginationRecursion(s"$BASE_URL/folders", nbRetrialsPerPage, channel, nbRetrialsPerPage)
    }

  /**
   * Gets information given a folder id
   */
  def getFolderInfo(url: VSDURL): Future[VSDFolder] = {
    val channel = authChannel ~> unmarshal[VSDFolder]
    channel(Get(url.selfUrl))
  }



  private def createFolder(name: String, parentFolder: VSDFolder) : Future[VSDFolder] = {
    val channel = authChannel ~> unmarshal[VSDFolder]
    val folderModel =  VSDFolder(1, name, parentFolder.level + 1, Some(VSDURL(parentFolder.selfUrl)), None, None, None, None, "dummy")
    channel(Post(s"$BASE_URL/folders",folderModel))
  }

  /**
   * creates a folder with the given name and parent folder ID on the VSD
   *
   */
  def createFolder(name: String, parentFolder: VSDURL) : Future[VSDFolder] = {
    val channel = authChannel ~> unmarshal[VSDFolder]
    for {
      parentFolder <- getFolderInfo(parentFolder)
      res <- createFolder(name: String, parentFolder)
    } yield { res }
  }


  /**
   * deletes a folder with the given name and parent folder ID on the VSD
   *
   */
  def deleteFolder(url: VSDURL)  : Future[Try[Unit]] = {
    val channel = authChannel
    channel(Delete(url.selfUrl)).map { r =>
      if (r.status.isSuccess) Success(()) else Failure(new Exception(s"failed to delete directory ${url.selfUrl}" + r.entity.toString()))
    }
  }


  /**
   * Adds a VSD object to an existing folder
   */
  def addObjectToFolder(obj : VSDURL, folder: VSDFolder) : Future[VSDFolder] = {
    val channel = authChannel ~> unmarshal[VSDFolder]
    val folderModel =  folder.copy(containedObjects = Some(folder.containedObjects.getOrElse(Seq[VSDURL]()) :+ obj))
    channel(Put(s"$BASE_URL/folders",folderModel))
  }


  /**
   * removes a VSD object from an existing folder
   */
  def removeObjectFromFolder(obj : VSDURL, folder: VSDFolder) : Future[VSDFolder]= {
    val channel = authChannel ~> unmarshal[VSDFolder]
    val r =  folder.containedObjects.getOrElse(Seq[VSDURL]()).filterNot(l => l == obj)
    val folderModel =  folder.copy(containedObjects = if (r.isEmpty) None else Some(r))
    channel(Put(s"$BASE_URL/folders",folderModel))
  }


  /**
   * Downloads the content of the folder to the indicated File destination. If the destination folder already exists in the file system, the function will abort.
   * In case the VSD folder contains subfolders, this call will result in a recursion
   * On success,returns the list of downloaded VSDObjectIDs along with their corresponding File.
   *
   * The downloaded files are first stored in a temporary directory, and only on success of all files moved to the indicated destination.
   * */
  def downloadFolder(folder: VSDFolder, destination : File) : Future[Seq[(VSDCommonObjectInfo, File)]]= {
    val infoChannel = authChannel ~> unmarshal[VSDFolder]
    val objInfoChannel = authChannel ~> unmarshal[VSDCommonObjectInfo]

    if(destination.isDirectory()) return Future.failed(new Exception("Indicated folder already exists"))

    val tempDestination = File.createTempFile("VSDFolder_"+folder.id,"")
    tempDestination.delete() ; tempDestination.mkdir()

    // If any subdirectories, we recurse first
    val downloadedObjsInSubdirsF = Future.sequence(folder.childFolders.getOrElse(Seq[VSDURL]()).map{ sub =>
      for {
        info <- infoChannel(Get(sub.selfUrl))
        subFolderList <- downloadFolder(info, new File(tempDestination.getAbsolutePath + File.pathSeparator + info.name))
      } yield (subFolderList)
    }).map(_.flatten)

    // now download the objects contained in the file itself
    val downloadedObjsInFolderF = Future.sequence(folder.containedObjects.getOrElse(Seq[VSDURL]()).map { objURL =>
      for {
        info <- objInfoChannel(Get(objURL.selfUrl))
        dl <- downloadVSDObject(VSDURL(info.selfUrl), tempDestination, s"VSD_${info.id}.zip").map(_.get)
      } yield ((info, dl))
    })

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
  def getUserInfo(url : VSDURL) : Future[VSDUser]= {
    val channel = authChannel ~>  unmarshal[VSDUser]
    channel(Get(url.selfUrl))
  }

  def shutdown(): Unit = {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }


    /**
     *  add a link between 2 VSD objects
     **/
  def addLink(object1 : VSDURL, object2 : VSDURL) : Future[VSDLink] = {
      val channel = authChannel ~> unmarshal[VSDLink]
      val l = VSDLink(0, "dummy", object1, object2, "dummy")
      channel(Post(s"$BASE_URL/object-links", l))
   }



  /**
   * get link information
   **/
  def getLinkInfo(url : VSDURL) : Future[VSDLink] = {
    val channel = authChannel ~> unmarshal[VSDLink] ; channel(Get(url.selfUrl))
  }


  /**
   * deletes an existing link
   **/
  def deleteLink(url : VSDURL) : Future[Try[Unit]] = {
    val channel = authChannel
    channel(Delete(url.selfUrl)).map { r =>
      if (r.status.isSuccess) Success(()) else Failure(new Exception(s"failed to delete vsd link  ${url.selfUrl}" + r.entity.toString()))
    }
  }

  /**
    * Lists modalities supported by the VSD
    */
  def listModalities() : Future[Array[VSDModality]] = {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDModality]]
    paginationRecursion(s"$BASE_URL/modalities", 3, channel, 3)
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
  def listObjectTypes() : Future[Seq[(Int, String)]] = {
    println("Listing object types")
    val channel = authChannel ~>    unmarshal[VSDObjectOptions]
    channel(Options(s"$BASE_URL/objects")).map{ _.types.map(kv => (kv.key, kv.value))}
  }

  /**
   * Publishes (validates) an object
   *
   */
  def publishObject(url : VSDURL) : Future[VSDCommonObjectInfo] = {
    val channel = authChannel ~>  unmarshal[VSDCommonObjectInfo]
    for {
      info <-getVSDObjectInfo[VSDCommonObjectInfo](url)
      res <- channel(Put(s"$BASE_URL/objects/${info.id}/publish"))
    } yield res
  }

  /**
   * Sets group rights for a VSD object
   *
   * The rights are collection of the pre-defined objects :
   * [[VSDNoneRight]], [[VSDReadRight]], [[VSDEditRight]], [[VSDVisitRight]],[[VSDManageRight]],[[VSDDownloadRight]], [[VSDOwnerRight]]
   * */

  def setObjectGroupRights(obj : VSDURL, group: VSDURL, rights : Seq[VSDObjectRight]) : Future[VSDObjectGroupRight] = {
    val channel = authChannel ~>  unmarshal[VSDObjectGroupRight]
    val req = VSDObjectGroupRight(0, obj, group, rights.map(r => VSDURL(r.selfUrl)), "dummy")
    channel(Post(s"$BASE_URL/object-group-rights", req))
  }


  /**
   * Sets user rights for a VSD object
   *
   * The rights are collection of the pre-defined objects :
   * [[VSDNoneRight]], [[VSDReadRight]], [[VSDEditRight]], [[VSDVisitRight]],[[VSDManageRight]],[[VSDDownloadRight]], [[VSDOwnerRight]]
   * */

  def setObjectUserRights(obj : VSDURL, user: VSDURL, rights : Seq[VSDObjectRight]) : Future[VSDObjectUserRight] = {
    val channel = authChannel ~>  unmarshal[VSDObjectUserRight]
    val req = VSDObjectUserRight(0, obj, user, rights.map(r => VSDURL(r.selfUrl)), "dummy")
    channel(Post(s"$BASE_URL/object-user-rights", req))
  }


  /** *
    * Get a list of groups
     */
  def listGroups(): Future[Array[VSDGroup]]= {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDGroup]]
    paginationRecursion(s"$BASE_URL/groups", 3, channel, 3)
  }

  /** *
    * Returns the details of an object group right relation, given its url
    *
    */
  def getObjectGroupRight(url : VSDURL) : Future[VSDObjectGroupRight]= {
    val channel = authChannel ~> unmarshal[VSDObjectGroupRight]
    channel(Get(url.selfUrl))
  }


  /** *
    * Returns the details of an object user right relation, given its url
    *
    */
  def getObjectUserRight(url : VSDURL) : Future[VSDObjectUserRight]= {
    val channel = authChannel ~> unmarshal[VSDObjectUserRight]
    channel(Get(url.selfUrl))
  }


  /**
   * Returns a String representing the hierarchical path to this folder
   *
   */
  def getFolderPath(url : VSDURL) : Future[String] = {
    getFolderInfo(url).flatMap { info =>
        if(info.parentFolder.isEmpty)  Future {"/"+info.name}
        else getFolderPath(info.parentFolder.get).map(p => p + "/" + info.name)
    }
  }


  /**
   * Returns a VSDFolder given its hierarchical path (if it exists, None otherwise)
   */
  def getFolderFromPath(path : String) : Future[Option[VSDFolder]] = {

    val lastName = path.split('/').last
    val matchingF = listFolders().map { l => l.filter(_.name == lastName)}

    // for all folders matching the last name, get their full path and compare it to requested
    val matchingPathsF = matchingF.flatMap { matching =>  Future.sequence(matching.toIndexedSeq.map( i => getFolderPath(VSDURL(i.selfUrl)).map(pth => (i, pth))))}
    matchingPathsF.map { matchingPaths =>  matchingPaths.find(pair => pair._2 == path).map(_._1) }
  }


 }
object VSDConnect {


  private def connect(username: String, password: String, BASE_URL: String): Try[VSDConnect] = {
    import system.dispatcher
    val conn = new VSDConnect(username, password, BASE_URL)
    implicit val system = conn.system
    conn.authChannel(Get(s"$BASE_URL/objects/unpublished/")).map { r =>
      if (r.status.isSuccess) {
        Success(conn)
      } else {
        println(r)
        Failure(new Exception("Login unsuccessful"))
      }
    }.await
  }

  def apply(username : String, password : String) : Try[VSDConnect] = {
    val BASE_URL = "https://www.virtualskeleton.ch/api"
    connect(username, password , BASE_URL)
  }

  /**
    * Factory method that avoids writing credentials in source code. The indicated file must contain the login on the first line
    * and the password on the second
    *
    */
  def apply(credentialsFile: File): Try[VSDConnect] = {
    val lines = Source.fromFile(credentialsFile).getLines().toIndexedSeq
    val login= lines(0) ;  val pass= lines(1)
    apply(login, pass)
  }

  def demo(username : String, password : String) : Try[VSDConnect] = {
    val BASE_URL = "https://demo.virtualskeleton.ch/api"
    connect(username, password , BASE_URL)
  }

  def printStep(resp: HttpResponse): HttpResponse = {
    println("*** Response : " + resp.entity.asString)
    resp
  }
}