package org.statismo.stk.vsdconnect

import org.specs2.matcher.ShouldMatchers
import org.scalatest.FunSpec
import akka.event.Logging
import akka.actor.ActorSystem
import java.io.File
import spray.can.Http.ConnectionException
import scala.util.Failure
import scala.util.Success
import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest._
import matchers.ShouldMatchers._
import java.util.concurrent._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span
import org.scalatest.time.Seconds
import org.scalatest.time.Minutes

class VSDTests extends FunSpec with ShouldMatchers with ScalaFutures {

  val vsd = new VSDConnect("demo@virtualskeleton.ch", "demo" /*, true*/ )

  implicit val system = vsd.system.dispatcher

  val tmpDir = new File(System.getProperty("java.io.tmpdir"))

  describe("VSD REST connection") {

    val uploadedFile = scala.concurrent.promise[VSDFileID]

    it("can upload a single file") {
      val path = getClass().getResource("/dicomdir/1.2.840.113704.1.111.3216.1302961430.63764.dcm").getPath
      val r = vsd.sendFile(new File(path), 5)

      whenReady(r, timeout(Span(1, Minutes))) { resp =>
        resp match {
          case s: Success[(VSDFileID, VSDObjectID)] => { uploadedFile.success(resp.get._1) }
          case f: Failure[_] => { uploadedFile.failure(new Exception("Fail due to upload test fail")); fail("*** Download failed " + f.exception.printStackTrace) }
        }
      }

      r onFailure {
        case s: ConnectionException => fail("Connection problem. This could be due to a self-signed certifcate. Please download the certificate from the VSD webserver and add it to the JVM's keychain")
      }
    }

    it("can download a single file (previously uploaded)") {

      val fileId = Await.result(uploadedFile.future, Duration(2, MINUTES))
      val dldFile = vsd.downloadFile(fileId, tmpDir, fileId.id.toString)
      whenReady(dldFile, timeout(Span(1, Minutes))) { f =>
        f match {
          case s: Success[File] => { assert(s.get.exists); s.get.delete }
          case f: Failure[_] => fail("*** Download failed " + f.exception)
        }
      }

    }

    val uploadedObject = scala.concurrent.promise[VSDObjectID]

    it("can upload a dicom directory and download resulting object") {

      val path = getClass().getResource("/dicomdir/").getPath
      val r = vsd.sendDICOMDirectory(new File(path))

      whenReady(r, timeout(Span(1, Minutes))) { s =>
        if (s.isLeft) {
          uploadedObject.failure(new Exception("Fail due to upload test fail"));
          fail(s"Sending dicom directory failed for ${s.left.map(_.size)} files")
        } else {
          s.right.map { ol =>
            val objid = ol.head
            uploadedObject.success(objid)
          }
        }
      }
      r onFailure {
        case s: ConnectionException => fail("Connection problem. This could be due to a self-signed certifcate. Please download the certificate from the VSD webserver and add it to the JVM's keychain")
      }
    }

    it("can download a vsd objet") {
      val objId = Await.result(uploadedObject.future, Duration(5, MINUTES))
      val obj = vsd.downloadVSDObject(objId, tmpDir, s"object${objId}")
      whenReady(obj, timeout(Span(1, Minutes))) { o =>
        o match {
          case s: Success[File] => { assert(s.get.exists); s.get.delete }
          case f: Failure[_] => fail("*** Download failed " + f.exception)
        }
      }
    }

    val objInfo = scala.concurrent.promise[VSDObjectInfo]

    it("can retrieve information on a VSD object") {

      val objId = Await.result(uploadedObject.future, Duration(5, MINUTES))

      val info = vsd.getVSDObjectInfo(objId)
      whenReady(info, timeout(Span(1, Minutes))) { i =>
        objInfo.success(i)
        assert(i.sliceThickness.get === 0.67f)
      }
      info onFailure {
        case e => objInfo.failure(new Exception("Failed to retrieve info to modifiy : " + e))
      }
    }

    it("it can update information of a VSD object") {

      val newDescription = "newer description"
      val i = Await.result(objInfo.future, Duration(1, MINUTES))
      val newInfo = i.copy(description = Some(newDescription))

      val f = for {
        f1 <- vsd.updateVSDObjectInfo(newInfo, 4)
        updatedInfo <- vsd.getVSDObjectInfo(VSDObjectID(i.id))
      } yield updatedInfo

      whenReady(f, timeout(Span(1, Minutes))) { s =>
        assert(s.sliceThickness.get === 0.67f)
        assert(s.description.get === newDescription)
      }
    }

    ignore("can retrieve the list of ontologies") {
      val ontologiesF = vsd.listOntologies
      whenReady(ontologiesF, timeout(Span(2, Minutes))) { o =>
        assert(o.types.contains((v: VSDOntology) => v.value.equals("FMA")))
      }
    }

  }

}