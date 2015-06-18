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
import org.scalatest.time.{Span, Seconds, Minutes}

class VSDTests extends FunSpec with ShouldMatchers with ScalaFutures {

  val vsd = VSDConnect.demo("demo@virtualskeleton.ch", "demo").get

  implicit val system = vsd.system.dispatcher

  val tmpDir = new File(System.getProperty("java.io.tmpdir"))

  import VSDJson._

  describe("VSD REST connection") {

    val uploadedFile = scala.concurrent.promise[VSDFileID]

    it("can upload a single file") {
      println("sending file")
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
      println("downloading file")
      val dldFile = vsd.downloadFile(fileId, tmpDir, fileId.id.toString)
      whenReady(dldFile, timeout(Span(1, Minutes))) { f =>
        f match {
          case s: Success[File] => { assert(s.get.exists); s.get.delete }
          case f: Failure[_] => fail("*** Download failed " + f.exception)
        }
      }
    }

    val uploadedObject = scala.concurrent.promise[VSDObjectID]

    it("can upload a dicom directory ") {

      val path = getClass().getResource("/dicomdir/").getPath
      println("sending directory")
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

    it("can download a vsd object (previously uploaded)") {
      val objId = Await.result(uploadedObject.future, Duration(5, MINUTES))
      println("downloading vsd object")
      val obj = vsd.downloadVSDObject(objId, tmpDir, s"object${objId}")
      whenReady(obj, timeout(Span(1, Minutes))) { o =>
        o match {
          case s: Success[File] => { assert(s.get.exists); s.get.delete }
          case f: Failure[_] => fail("*** Download failed " + f.exception)
        }
      }
    }

    val objInfo = scala.concurrent.promise[VSDRawImageObjectInfo]

    it("can retrieve information on a VSD Raw Image object") {
      val objId = Await.result(uploadedObject.future, Duration(5, MINUTES))
      println("retrieving object information")
      val info = vsd.getVSDObjectInfo[VSDRawImageObjectInfo](objId)
      whenReady(info, timeout(Span(1, Minutes))) { i =>
        objInfo.success(i)
        assert(i.sliceThickness.get === 0.67f)
      }
      info onFailure {
        case e => objInfo.failure(new Exception("Failed to retrieve info to modifiy : " + e))
      }
    }

    it("can update information of a VSD object") {

      val newDescription = "Generated by unit test of VSDConnect REST client"
      val i = Await.result(objInfo.future, Duration(1, MINUTES))
      println("updating object information")
      val newInfo = i.copy(description = Some(newDescription))

      val f = for {
        f1 <- vsd.updateVSDObjectInfo(newInfo, 4)
        updatedInfo <- vsd.getVSDObjectInfo[VSDRawImageObjectInfo](VSDObjectID(i.id))
      } yield updatedInfo

      whenReady(f, timeout(Span(1, Minutes))) { s =>
        assert(s.sliceThickness.get === 0.67f)
        assert(s.sliceThickness.get === 0.67f)
        assert(s.description.get === newDescription)
      }
    }

    val ontologyKey = scala.concurrent.Promise[Int]

    it("can retrieve the list of ontologies") {
      println("listing ontologies")
      val ontologiesF = vsd.listOntologies
      whenReady(ontologiesF, timeout(Span(2, Minutes))) { o =>
        val l = o.types.find((v: VSDOntology) => v.value.equals("FMA"))
        assert(l.isDefined)
        l.map(t => ontologyKey.success(t.key))
        if (l.isEmpty) ontologyKey.failure(new Exception("Failed to retrieve the key of the FMA ontology"))
      }
    }

    val ontologyItem = scala.concurrent.promise[VSDOntologyItem]
    val ontologyItem2 = scala.concurrent.promise[VSDOntologyItem]

    it("can retrieve the list of ontology items for one given ontology") {
      val FMAtypeKey = Await.result(ontologyKey.future, Duration(1, MINUTES))
      println("retrieving ontology items")
      val listOntItems = vsd.listOntologyItemsForType(FMAtypeKey, 3)
      whenReady(listOntItems, timeout(Span(2, Minutes))) { l =>
        assert(l.length > 0)
        ontologyItem.success(l.head)
        ontologyItem2.success(l(1))
      }
      listOntItems onFailure { case e => ontologyItem.failure(new Exception("Failed to retrieve a valid ontology item id:  " + e)) }
    }

    val newObjInfo = scala.concurrent.promise[VSDObjectInfo]
    val objOntoItemRelationId = scala.concurrent.promise[Int]

    it("can update the information of one object to be of a given ontology item") {
      val ontoItem = Await.result(ontologyItem.future, Duration(1, MINUTES))
      val oldObinf = Await.result(objInfo.future, Duration(1, MINUTES))
      println("updating object information to be of a certain ontology")
      val newObjOntoItemF = vsd.createObjectOntologyItemRelation(oldObinf, VSDURL(ontoItem.selfUrl)) 
      whenReady(newObjOntoItemF, timeout(Span(2, Minutes))) { newObjOntoItem =>
        assert(newObjOntoItem.position === oldObinf.ontologyItemRelations.map(_.size).getOrElse(0))
        objOntoItemRelationId.success(newObjOntoItem.id)

        val f = vsd.getVSDObjectInfo[VSDRawImageObjectInfo](VSDObjectID(oldObinf.id))
        f onSuccess { case i => newObjInfo.success(i) }
        f onFailure { case e => newObjInfo.failure(new Exception("Failed to fetch obj Info after adding ontology item " + e)) }
      }
      newObjOntoItemF onFailure {
        case e =>
          newObjInfo.failure(new Exception("Failed to create first ontology relation " + e))
          objOntoItemRelationId.failure(new Exception("Failed to create first ontology relation " + e))
      }
    }

    val readyToCleanObject = scala.concurrent.promise[Boolean]
    val readyToCleanFolder = scala.concurrent.promise[Boolean]

    it("can update an object ontology relation") {
      val ontoItem = Await.result(ontologyItem2.future, Duration(1, MINUTES))
      val objOntoItemRelId = Await.result(objOntoItemRelationId.future, Duration(1, MINUTES))

      val newInfo = Await.result(newObjInfo.future, Duration(1, MINUTES))
      println("updating object ontology relation")
      val secondObjItemF = vsd.updateObjectOntologyItemRelation(objOntoItemRelId, newInfo, VSDURL(ontoItem.selfUrl))

      whenReady(secondObjItemF, timeout(Span(2, Minutes))) { newObjOntoItem =>
        assert(newObjOntoItem.ontologyItem.selfUrl === ontoItem.selfUrl)
      }
    }

    it("uploaded object appears in list of unpublished objects") {
      val info = Await.result(objInfo.future, Duration(1, MINUTES))
      println("checking that uploaded object appears in list")
      val unpublishedF = vsd.listUnpublishedObjects(3)       
      val t = unpublishedF.map(l => l.map(_.id).contains (info.id))
      whenReady(t, timeout(Span(1, Minutes))) { v =>  assert(v)   }
    }


    it("can list published objects") {
      val l = vsd.listPublishedObjects()
      whenReady(l, timeout(Span(1, Minutes))) { v =>   assert(v.size > 0) }
    }


    val myDatafolder = scala.concurrent.promise[VSDFolder]

    it("can list available folders") {
      val dirs = vsd.listFolders()
      whenReady(dirs, timeout(Span(1, Minutes))) { r =>
        assert(r.size > 0)
        r.find(i => i.level==1 && i.name == "MyProjects").map { myDatafolder.success(_) }
      }
    }

    val myCreatedfolder = scala.concurrent.promise[VSDFolder]

    it("can create a folder") {
      println("creating folder")
      val parentFolder = Await.result(myDatafolder.future, Duration(1, MINUTES))
      val folderInfo = vsd.createFolder("unitTestFolder", parentFolder)
      whenReady(folderInfo, timeout(Span(1, Minutes))) { i =>
        assert(i.name === "unitTestFolder")
        myCreatedfolder.success(i)
      }
    }

    it("can retrieve correct info given a folder id") {
      val createdInfo = Await.result(myCreatedfolder.future, Duration(1, MINUTES))
      val queriedInfo = vsd.getFolderInfo(VSDFolderID(createdInfo.id))
      whenReady(queriedInfo, timeout(Span(1, Minutes))) { i =>
        assert(i.selfUrl === createdInfo.selfUrl && i.name ==="unitTestFolder")
      }
    }

    val addedToFolder = scala.concurrent.promise[VSDFolder]

    it("can add an object to a folder") {
      val createdInfo = Await.result(myCreatedfolder.future, Duration(1, MINUTES))
      val info = Await.result(objInfo.future, Duration(1, MINUTES))

      val u = vsd.addObjectToFolder(info, createdInfo)
      whenReady(u, timeout(Span(1, Minutes))) { f =>
        assert(f.containedObjects.get.contains(VSDURL(info.selfUrl)))
        addedToFolder.success(f)
      }
    }

    it("can download the content of a folder") {
      val folderInf = Await.result(addedToFolder.future, Duration(1, MINUTES))
      val info = Await.result(objInfo.future, Duration(1, MINUTES))

      val dest = File.createTempFile("UnitTestVSDFolder_" + folderInf.id, "")
      val r = vsd.downloadFolder(folderInf, dest)

      whenReady(r, timeout(Span(4, Minutes))) { f =>
        assert(f(0)._1.id === info.id)
        val fil = new File(f(0)._2.getAbsolutePath)
        assert(fil.exists())
      }
    }


    it("can remove an object from a folder") {
      val addedInfo = Await.result(addedToFolder.future, Duration(1, MINUTES))
      val info = Await.result(objInfo.future, Duration(1, MINUTES))

      val u = vsd.removeObjectFromFolder(info, addedInfo)
      whenReady(u, timeout(Span(1, Minutes))) { f =>
        assert(f.containedObjects.getOrElse(Seq[VSDURL]()).find(_ == VSDURL(info.selfUrl)) isEmpty)
      }
    }


    it("can retrieve user info") {
      val userInfo = vsd.getUserInfo(VSDUserID(1))
      whenReady(userInfo, timeout(Span(1, Minutes))) { i => assert(i.username == "system") }
    }

    val object2P = scala.concurrent.promise[VSDObjectID]

    it("can upload a nifti segmentation") {
      val obj1Info = Await.result(objInfo.future, Duration(1, MINUTES))
      val path = getClass().getResource("/volume.nii").getPath
      val idObj2F = vsd.sendFile(new File(path), 20).map { t => t.get._2}
      whenReady(idObj2F, timeout(Span(2, Minutes))) { idObj2 =>
        assert(idObj2.id > obj1Info.id)
        object2P.success(idObj2)
      }
    }

    val linkP = scala.concurrent.promise[VSDLink]

    it("can link 2 objects") {
      // first upload a second object
      val obj1Info = Await.result(objInfo.future, Duration(1, MINUTES))
      val obj2Id = Await.result(object2P.future, Duration(3, MINUTES))

      val fut = for {
        obj2Info <- vsd.getVSDObjectInfo[VSDSegmentationObjectInfo](obj2Id)
        link <- vsd.addLink(VSDURL(obj2Info.selfUrl), VSDURL(obj1Info.selfUrl))
        lookedUpLink <- vsd.getLinkInfo(VSDLinkID(link.id))
      } yield {(lookedUpLink,obj2Info)}

      whenReady(fut, timeout(Span(4, Minutes))) { case (link, obj2) =>
        assert(link.object1.selfUrl == obj2.selfUrl  && link.object2.selfUrl == obj1Info.selfUrl)
        readyToCleanObject.complete(Success(true))
        linkP.success(link)
      }
    }

    it("can list the modalities on the VSD") {
      val modalitiesF = vsd.listModalities()
      whenReady(modalitiesF, timeout(Span(1, Minutes))) { modalities =>
         assert(modalities.find(_.name == "CT").isDefined)
      }
    }

    it("can list the segmentation methods supported by the VSD") {
      val segMethsF = vsd.listSegmentationMethods()
      whenReady(segMethsF, timeout(Span(1, Minutes))) { segMethods =>
        assert(segMethods.find(_.name == "Manual").isDefined)
      }
    }

    it("can list the object types supported by the VSD") {
      val r = vsd.listObjectTypes()
      whenReady(r, timeout(Span(1, Minutes))) { u => println("received  " + u)}
    }

    it("can delete a link") {
      val link = Await.result(linkP.future, Duration(5, MINUTES))
      val del = vsd.deleteLink(VSDLinkID(link.id))
      whenReady(del, timeout(Span(1, Minutes))) { u => assert(u.isSuccess) }
    }

    it("can delete unpublished VSD objects") {
      val objId = Await.result(uploadedObject.future, Duration(5, MINUTES))
      val obj2Id = Await.result(object2P.future, Duration(5, MINUTES))
      // Wait until all other tests finished
      Await.result(readyToCleanObject.future, Duration(6, MINUTES))

      val d = for {
        a <- vsd.deleteUnpublishedVSDObject(objId)
        b <- vsd.deleteUnpublishedVSDObject(obj2Id)
      } yield b

      whenReady(d, timeout(Span(1, Minutes))) { r =>
        assert(r.isSuccess)
        readyToCleanFolder.success(true)
      }
    }

    it("can delete an empty folder") {
      val createdInfo = Await.result(myCreatedfolder.future, Duration(1, MINUTES))
      //  Await.result(readyToCleanFolder.future, Duration(6, MINUTES))

      val deletion = vsd.deleteFolder(VSDFolderID(createdInfo.id))
      whenReady(deletion, timeout(Span(1, Minutes))) { r => assert(r.isSuccess) }
    }


  }

}