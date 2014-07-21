package org.statismo.stk.vsdconnect

import spray.json._
import DefaultJsonProtocol._


case class VSDObjectID(id: Int)
case class VSDFileID(id: Int)

case class VSDURL(selfUrl : String)
case class FileUploadResponse(file : VSDURL, relatedObject : VSDURL)    

case class VSDObjectInfo (sliceThickness: Option[Float], spaceBetweenSlices:Option[Float], kilovoltPeak: Option[Float], 
    modality:Option[VSDURL], id:Int, createdDate:String, description:Option[String], ontologyCount:Option[Int], `type`:Option[Int],
    downloadUrl : String, files : Seq[VSDURL], linkedObjects:Option[ Seq[VSDURL]], linkedObjectRelations:Option[ Seq[VSDURL]],
    ontologyItems:Option[ Seq[VSDURL]],  ontologyItemRelations:Option[ Seq[VSDURL]], selfUrl:String
)

case class VSDOntology(key : Int, value : String)
case class VSDOntologies(types : Array[VSDOntology])

object VSDJson {

  /**
   * This object contains all the case classes for requests / responses from the VSD
   * JSON (de)serializers if needed
   */
  
  	implicit val VSDURLProtocol = jsonFormat1(VSDURL.apply)
	implicit val VSDObjectIdProtocol = jsonFormat1(VSDObjectID.apply)
	implicit val VSDFileIdProtocol = jsonFormat1(VSDFileID.apply)
	implicit val FileUploadResponseFormat = jsonFormat2(FileUploadResponse.apply)
	implicit val teamFormat = jsonFormat16(VSDObjectInfo)
	implicit val VSDOntologyProtocol = jsonFormat2(VSDOntology) 
	implicit val VSDOntologiesProtocol = jsonFormat1(VSDOntologies)
	
	
//    implicit object FileUploadResponseFormat extends RootJsonFormat[FileUploadResponse] {
//      def write(c: FileUploadResponse) = JsObject(
//        "id" -> JsNumber(c.id.id),
//        "createdDate" -> JsString(c.createdDate),
//        "size" -> JsNumber(c.size),
//        "hashCode" -> JsString(c.vsdHashCode),
//        "anonymizedHashCode" -> JsString(c.anonymizedHashCode),
//        "originalFileName" -> JsString(c.originalFileName),
//        "downloadUrl" -> JsString(c.downloadUrl))
//
//      def read(value: JsValue) = {
//        value.asJsObject.getFields("id", "createdDate", "size", "hashCode", "anonymizedHashCode", "originalFileName", "downloadUrl") match {
//
//          case Seq(JsNumber(id), JsString(date), JsNumber(size), JsString(code), JsString(anonymizedHashCode), JsString(originalFileName), JsString(downloadUrl)) =>
//            FileUploadResponse(VSDFileID(id.toInt), date, size.toInt, code, anonymizedHashCode, originalFileName, downloadUrl)
//          case e => throw new DeserializationException("File upload response deserialization error " +e)
//        }
//      }
//    }
}