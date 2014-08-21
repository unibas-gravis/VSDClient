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

case class VSDPagination(rpp : Int, page:Int)
case class VSDOntology(key : Int, value : String)
case class VSDOntologies(types : Array[VSDOntology])
case class VSDOntologyItem(id : Int, term:String, `type`: Int, selfUrl : String)
case class VSDObjectOntologyItem(id : Int, position: Int, `type` : Int, `object` : VSDURL, ontologyItem: VSDURL, selfUrl :String)
case class VSDOntologyItemsListPerType(totalCount : Int, pagination : VSDPagination, items: Array[VSDOntologyItem],  nextPageUrl: Option[String]) 
case class VSDPaginatedListObjects(totalCount : Int, pagination : VSDPagination, items: Array[VSDObjectInfo],  nextPageUrl: Option[String]) 



object VSDJson {

  /**
   * This object contains all the case classes for requests / responses from the VSD
   * JSON (de)serializers if needed
   */
  
  	implicit val VSDURLProtocol = jsonFormat1(VSDURL.apply)
	implicit val VSDObjectIdProtocol = jsonFormat1(VSDObjectID.apply)
	implicit val VSDFileIdProtocol = jsonFormat1(VSDFileID.apply)
	implicit val FileUploadResponseFormat = jsonFormat2(FileUploadResponse.apply)
	implicit val VSDObjectInfoProtocol = jsonFormat16(VSDObjectInfo)
	implicit val VSDOntologyProtocol = jsonFormat2(VSDOntology) 
	implicit val VSDOntologiesProtocol = jsonFormat1(VSDOntologies)
	implicit val VSDOntologyItemProtocol = jsonFormat4(VSDOntologyItem)
	implicit val VSDPaginationProtocol = jsonFormat2(VSDPagination)
	implicit val VSDOntologyItemsListPerTypeProtocol = jsonFormat4(VSDOntologyItemsListPerType)
	implicit val VSDObjectOntologyItemProtocol = jsonFormat6(VSDObjectOntologyItem)
	implicit val VSDPaginatedListObjectsProtocol = jsonFormat4(VSDPaginatedListObjects)
	
	
}