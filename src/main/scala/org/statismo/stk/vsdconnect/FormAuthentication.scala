package org.statismo.stk.vsdconnect

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

/**
 * This connects to the VSD and does a Microsoft forms authentication
 */

object FormAuthentication extends App {

  def getAuthChannel(user: String, pass: String, system: ActorSystem) = {
    implicit val s = system
    import s.dispatcher

    val login = s"username=${URLEncoder.encode(user)}&password=${URLEncoder.encode(pass)}"
    val pipeline = sendReceive
    val authenticatedChannel = for {
      verifKeys <- pipeline(spray.client.pipelining.Get("https://demo.virtualskeleton.ch/Account/LogOn")).map(e => {
        val formKey = e.entity.asString.split("""__RequestVerificationToken" type="hidden" value="""")(1).split('"')(0)
        val cookieKey = e.headers.find(h => h.name == "Set-Cookie").map(s => s.value.split(";")(0)).get
        (formKey, cookieKey)
      })
      contentType = ContentType(MediaTypes.`application/x-www-form-urlencoded`)
      request = Post("https://demo.virtualskeleton.ch/Account/LogOn", HttpEntity(contentType, login + "&__RequestVerificationToken=" + URLEncoder.encode(verifKeys._1))) ~> addHeader("Cookie", verifKeys._2)
      aspKey <- pipeline(request).map(r => r.headers.find(_.name == "Set-Cookie").map(_.value.split(";")(0)))
    } yield (addHeader("Cookie", verifKeys._2 + ";" + aspKey.get) ~> pipeline)
    authenticatedChannel
  }

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("simple-spray-client")
  import system.dispatcher // execution context for futures below

  val log = Logging(system, getClass)
  val pipeline = sendReceive

  val authenticatedChannel = getAuthChannel("demo@virtualskeleton.ch", "demo", system)

  authenticatedChannel onSuccess {
    case channel => {
      val req = channel(Get("https://demo.virtualskeleton.ch/MyVSD/MyData"))
      req onComplete {
        case Success(s) => println("Success " + s.entity.asString)
        case Failure(f) => println("failed " + f)
      }

      val req2 = channel(Get("https://demo.virtualskeleton.ch/api/objects/24"))
      req2 onComplete {
        case Success(s) => println(s"Status ${s.status}" + s.entity.asString)
        case Failure(f) => println("failed " + f)
      }

    }
  }
  authenticatedChannel onFailure { case e => println("failed : " + e) }

  def shutdown(): Unit = {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }
}