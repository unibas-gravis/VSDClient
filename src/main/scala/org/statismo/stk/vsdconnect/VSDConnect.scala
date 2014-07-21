package org.statismo.stk.vsdconnect

import java.io.File
import java.nio.file.Files
import scala.Array.canBuildFrom
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import akka.actor.ActorSystem
import akka.event.Logging
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout.durationToTimeout
import spray.can.Http
import spray.can.Http.ConnectionException
import spray.client.pipelining.Post
import spray.client.pipelining.WithTransformerConcatenation
import spray.client.pipelining.addCredentials
import spray.client.pipelining.sendReceive
import spray.client.pipelining.sendReceive$default$3
import spray.http.BasicHttpCredentials
import spray.http.BodyPart
import spray.http.ContentType
import spray.http.HttpEntity
import spray.http.HttpHeaders
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.MediaTypes
import spray.http.MultipartFormData
import spray.util.pimpFuture
import scala.util.{ Success, Failure }
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.event.Logging
import akka.io.IO
import spray.json.{ JsonFormat, DefaultJsonProtocol }
import spray.can.Http
import spray.httpx.SprayJsonSupport
import spray.client.pipelining._
import spray.util._
import spray.http.BasicHttpCredentials
import java.net.URLEncoder
import spray.http.HttpHeaders.Cookie
import spray.http.HttpRequest
import spray.http.HttpEntity
import spray.http.ContentTypes
import spray.http.ContentType
import spray.http.HttpRequest
import spray.http.MediaTypes
import spray.http.HttpHeader
import VSDJson._
import SprayJsonSupport._
import java.io.FileOutputStream
import spray.http.HttpResponse
import scala.concurrent.Await

/**
 * Simple upload of files based on Basic authentication
 */

class VSDConnect(user: String, password: String, formAuthenticationFlag: Boolean = false) {

  implicit val system = ActorSystem(s"VSDConnect-user-${user.replace("@", "-").replace(".", "-")}")
  import system.dispatcher

  val UPLOAD_URL = "https://demo.virtualskeleton.ch/api/upload/"
  val FILEBASE_URL = "https://demo.virtualskeleton.ch/api/files/"

  val log = Logging(system, getClass)

  val authChannel = if (!formAuthenticationFlag)
    addCredentials(BasicHttpCredentials(user, password)) ~> sendReceive
  else {
    println("Using form authentication")
    Await.result( FormAuthentication.getAuthChannel(user, password,system), Duration(2, MINUTES))
  }
  
  /**
   * This function returns the generated VSD file id or an exception in case the send failed
   */
  def sendFile(f: File, nbRetrials: Int = 0): Future[Try[(VSDFileID, VSDObjectID)]] = {

    println("uploading " + f.getName)
    val pipe = authChannel ~> unmarshal[FileUploadResponse]
    val bArray = Files.readAllBytes(f.toPath)
    val req = Post(UPLOAD_URL, MultipartFormData(Seq(
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
    println("Downloading file from " + url.selfUrl)
    authChannel(Get(url.selfUrl)).map { r =>
      if (r.status.isSuccess) {
        println("response length " + r.entity.data.length)

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
    downloadFile(VSDURL(s"https://demo.virtualskeleton.ch/api/files/${id.id}/download"), downloadDir, fileName)
  }

  def getVSDObjectInfo(id: VSDObjectID) = {
    val pipe = authChannel ~> unmarshal[VSDObjectInfo]
    pipe(Get(s"https://demo.virtualskeleton.ch/api/objects/${id.id}"))
  }

  def updateVSDObjectInfo(info: VSDObjectInfo, nbRetrials: Int = 0): Future[Try[HttpResponse]] = {

    val resp = authChannel(Put(s"https://demo.virtualskeleton.ch/api/objects/${info.id}", info)).map { s => Success(s) }
    resp.recoverWith {
      case e =>
        if (nbRetrials > 0) {
          println(s"Retrying object info update for ${info.id}, nb Retries left ${nbRetrials}")
          updateVSDObjectInfo(info, nbRetrials - 1)
        } else Future { Failure(e) } // the final recover after all retrials failed
    }
  }

  def listOntologies(): Future[VSDOntologies] = {
    val channel = authChannel ~> unmarshal[VSDOntologies]
    channel(Options("https://demo.virtualskeleton.ch/api/ontologies"))
  }

  /**
   * Download of object is always shipped in one zip file
   */
  def downloadVSDObject(id: VSDObjectID, downloadDir: File, fileName: String): Future[Try[File]] = {
    downloadFile(VSDURL(s"https://demo.virtualskeleton.ch/api/objects/${id.id}/download"), downloadDir, fileName)
  }

  def shutdown(): Unit = {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }

}

object VSDConnect {
  def printStep(resp: HttpResponse): HttpResponse = {
    println("*** Response : " + resp.entity.asString)
    resp
  }
}