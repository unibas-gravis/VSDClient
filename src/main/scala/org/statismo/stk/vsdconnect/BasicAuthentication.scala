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

object BasicAuth extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("simple-spray-client")
  import system.dispatcher // execution context for futures below
  val log = Logging(system, getClass)
  val authenticatedchannel = addCredentials(BasicHttpCredentials("demo@virtualskeleton.ch", "demo")) ~> sendReceive

  val req = authenticatedchannel(Get("https://demo.virtualskeleton.ch/MyVSD/MyData"))

  req onComplete {
    case Success(s) => if(s.status.intValue != 200 )
      println("failed "+ s.entity.asString)
    else
      println(s"Success " + s.entity.asString)
    case Failure(f) => println("failed " + f)
  }

  def shutdown(): Unit = {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }
}