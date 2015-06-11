package org.statismo.stk.vsdconnect

import java.io.{File, FileOutputStream}
import java.nio.file.Files

import akka.actor.ActorSystem
import akka.event.Logging
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout.durationToTimeout
import org.statismo.stk.vsdconnect.VSDJson._
import spray.can.Http
import spray.client.pipelining
import spray.client.pipelining.{Post, WithTransformerConcatenation, addCredentials, sendReceive, _}
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.util.pimpFuture

import scala.Array.canBuildFrom
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
 * Simple upload of files based on Basic authentication
 */

class VSDConnect private (user: String, password: String, BASE_URL: String) {

  implicit val system = ActorSystem(s"VSDConnect-user-${user.replace("@", "-").replace(".", "-")}")
  import system.dispatcher

  val log = Logging(system, getClass)
  val authChannel =  addCredentials(BasicHttpCredentials(user, password)) ~> sendReceive

  /**
   * This function returns the generated VSD file id or an exception in case the send failed
   */
  def sendFile(f: File, nbRetrials: Int = 0): Future[Try[(VSDFileID, VSDObjectID)]] = {

    val pipe = authChannel ~> unmarshal[FileUploadResponse]
    val bArray = Files.readAllBytes(f.toPath)
    val req = Post(s"$BASE_URL/upload/", MultipartFormData(Seq(
      BodyPart(
        HttpEntity(ContentType(MediaTypes.`multipart/form-data`), bArray),
        HttpHeaders.`Content-Disposition`("form-data", Map("filename" -> (f.getName + ".dcm"))) :: Nil))))

    val t = pipe(req) map { r =>

      val fileId = r.file.selfUrl.split('/').last
      val objectId = r.relatedObject.selfUrl.split('/').last
      Success(VSDFileID(fileId.toInt), VSDObjectID(objectId.toInt))
    }
    t.recoverWith {
      case e =>
        if (nbRetrials > 0) {
          println(s"Retrying file ${f.getName}, nb Retries left ${nbRetrials}")
          sendFile(f, nbRetrials - 1)
        } else Future { Failure(e) } // the final recover after all retrials failed
    }
  }

  /**
   * This function sends all the related Dicom files in a class and returns the list of object ids
   * that are associated with each uploaded file, or the exception for the files that failed
   *
   */
  def sendDICOMDirectoryDetailed(subjDir: File): Future[List[(String, Try[(VSDFileID, VSDObjectID)])]] = {
    val listFiles = subjDir.listFiles
    val send = for (f <- listFiles) yield { sendFile(f, 2).map(t => (f.getAbsolutePath(), t)) }
    Future.sequence(send.toList)
  }

  /**
   * This method uploads a Dicom directory, and returns if succeeded the list of VSDObjectIds created,
   * or on failure, the list of files that failed to be uploaded
   * *
   */
  def sendDICOMDirectory(subjDir: File): Future[Either[List[String], List[VSDObjectID]]] = {

    val summary = for {
      detailed <- sendDICOMDirectoryDetailed(subjDir)

      failedFiles = detailed.filter { case (_, t) => t.isFailure }.map(_._1)
      succeeded = detailed.filter { case (_, t) => t.isSuccess }
      distinctObjectIds = detailed.filter { case (_, t) => t.isSuccess }.map(_._2.get._2).distinct
    } yield {
      if (failedFiles.isEmpty) Right(distinctObjectIds) else Left(failedFiles)
    }

    summary
  }

  /**
   * This method downloads a file from the VSD given its downloadURL
   */
  def downloadFile(url: VSDURL, downloadDir: File, fileName: String): Future[Try[File]] = {
    require(downloadDir.isDirectory)
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

  /**
   * This method downloads a file from the VSD given its file ID
   */
  def downloadFile(id: VSDFileID, downloadDir: File, fileName: String): Future[Try[File]] = {
    downloadFile(VSDURL(s"$BASE_URL/files/${id.id}/download"), downloadDir, fileName)
  }

  def getVSDObjectInfo(id: VSDObjectID) : Future[VSDObjectInfo] = {
    val pipe = authChannel ~> unmarshal[VSDObjectInfo]
    pipe(Get(s"$BASE_URL/objects/${id.id}"))
  }

  def updateVSDObjectInfo(info: VSDObjectInfo, nbRetrials: Int = 0): Future[Try[HttpResponse]] = {

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

  def listUnpublishedObjects(nbRetrialsPerPage: Int = 3): Future[Array[VSDObjectInfo]] = {
    val channel = authChannel ~> unmarshal[VSDPaginatedList[VSDObjectInfo]]
    paginationRecursion(s"$BASE_URL/objects/unpublished/", nbRetrialsPerPage,channel, nbRetrialsPerPage)
  }

  def getOntologyItemInfo(url: VSDURL): Future[VSDOntologyItem] = {
    val channel = authChannel ~> unmarshal[VSDOntologyItem]
    channel(Get(url.selfUrl))
  }

  def createObjectOntologyItemRelation(objectInfo: VSDObjectInfo, ontologyItemURL: VSDURL): Future[VSDObjectOntologyItem] = {
    val channel = authChannel ~> unmarshal[VSDObjectOntologyItem]
    getOntologyItemInfo(ontologyItemURL).flatMap { ontologyItemInfo =>
      val newRelation = VSDObjectOntologyItem(1, 0, ontologyItemInfo.`type`, VSDURL(objectInfo.selfUrl), ontologyItemURL, "")
      channel(Post(s"$BASE_URL/object-ontologies/${ontologyItemInfo.`type`}", newRelation))
    }
  }

  def updateObjectOntologyItemRelation(id: Int, objectInfo: VSDObjectInfo, ontologyItemURL: VSDURL): Future[VSDObjectOntologyItem] = {
    val channel = authChannel ~> unmarshal[VSDObjectOntologyItem]
    val position = objectInfo.ontologyItemRelations.map(_.size).getOrElse(0)
    getOntologyItemInfo(ontologyItemURL).flatMap { ontologyItemInfo =>
      val newRelation = VSDObjectOntologyItem(id, position, ontologyItemInfo.`type`, VSDURL(objectInfo.selfUrl), ontologyItemURL, "")
      channel(Put(s"$BASE_URL/object-ontologies/${ontologyItemInfo.`type`}/${id}", newRelation))
    }
  }

  def deleteUnpublishedVSDObject(id: VSDObjectID): Future[Try[HttpResponse]] = {
    val channel = authChannel
    channel(Delete(s"$BASE_URL/objects/${id.id}")).map { r =>
      if (r.status.isSuccess) Success(r) else Failure(new Exception(s"failed to delete unpublished vsd object id ${id}" + r.entity.toString()))
    }
  }

  /**
   * Download of object is always shipped in one zip file
   */
  def downloadVSDObject(id: VSDObjectID, downloadDir: File, fileName: String): Future[Try[File]] = {
    downloadFile(VSDURL(s"$BASE_URL/objects/${id.id}/download"), downloadDir, fileName)
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
  def getFolderInfo(id: VSDFolderID): Future[VSDFolder] = {
    val channel = authChannel ~> unmarshal[VSDFolder]
    channel(Get(s"$BASE_URL/folders/${id.id}"))
  }


  /**
   * creates a folder with the given name and a parent folder on the VSD
   *
   */
  def createFolder(name: String, parentFolder: VSDFolder) : Future[VSDFolder] = {
    val channel = authChannel ~> unmarshal[VSDFolder]
    val folderModel =  VSDFolder(1, name, parentFolder.level + 1, Some(VSDURL(parentFolder.selfUrl)), None, None, None, None, "dummy")
    channel(Post(s"$BASE_URL/folders",folderModel))
  }

  /**
   * creates a folder with the given name and parent folder ID on the VSD
   *
   */
  def createFolder(name: String, parentFolderId: VSDFolderID)  : Future[VSDFolder] = {
    val channel = authChannel ~> unmarshal[VSDFolder]
    for {
      parentFolder <- getFolderInfo(parentFolderId)
      res <- createFolder(name: String, parentFolder)
    } yield { res }
  }


  /**
   * deletes a folder with the given name and parent folder ID on the VSD
   *
   */
  def deleteFolder(id: VSDFolderID)  : Future[Try[HttpResponse]] = {
    val channel = authChannel
    channel(Delete(s"$BASE_URL/folders/${id.id}")).map { r =>
      if (r.status.isSuccess) Success(r) else Failure(new Exception(s"failed to delete directory with id ${id}" + r.entity.toString()))
    }
  }


  /**
   * Adds a VSD object to an existing folder
   */
  def addObjectToFolder(obj : VSDObjectInfo, folder: VSDFolder) : Future[VSDFolder] = {
    val channel = authChannel ~> VSDConnect.printStep ~> unmarshal[VSDFolder]
    val folderModel =  folder.copy(containedObjects = folder.containedObjects.map(l => l :+ VSDURL(obj.selfUrl)))
    channel(Put(s"$BASE_URL/folders",folderModel))
  }

  /**
   * removes a VSD object from an existing folder
   */
  def removeObjectFromFolder(obj : VSDObjectInfo, folder: VSDFolder) = {
    val channel = authChannel ~> VSDConnect.printStep ~> unmarshal[VSDFolder]
    val folderModel =  folder.copy(containedObjects = folder.containedObjects.filterNot(l => l == VSDURL(obj.selfUrl)))
    channel(Put(s"$BASE_URL/folders",folderModel))
  }


  def shutdown(): Unit = {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
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

  def demo(username : String, password : String) : Try[VSDConnect] = {
    val BASE_URL = "https://demo.virtualskeleton.ch/api"
    connect(username, password , BASE_URL)
  }


  def printStep(resp: HttpResponse): HttpResponse = {
    println("*** Response : " + resp.entity.asString)
    resp
  }
}