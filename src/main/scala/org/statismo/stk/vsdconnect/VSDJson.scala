package org.statismo.stk.vsdconnect

import spray.json._
import DefaultJsonProtocol._

case class VSDObjectID(id: Int)

case class VSDFileID(id: Int)

case class VSDFolderID(id: Int)

case class VSDURL(selfUrl: String)

case class VSDUserID(id: Int)

case class VSDLinkID(id: Int)

case class FileUploadResponse(file: VSDURL, relatedObject: VSDURL)


trait VSDObjectInfo {
  val id: Int
  val createdDate: String
  val description: Option[String]
  val ontologyCount: Option[Int]
  val `type`: Option[Int]
  val downloadUrl: String
  val license: Option[VSDURL]
  val files: Seq[VSDURL]
  val linkedObjects: Option[Seq[VSDURL]]
  val linkedObjectRelations: Option[Seq[VSDURL]]
  val ontologyItems: Option[Seq[VSDURL]]
  val ontologyItemRelations: Option[Seq[VSDURL]]
  val objectPreviews: Option[Seq[VSDURL]]
  val objectGroupRights: Option[Seq[VSDURL]]
  val objectUserRights: Option[Seq[VSDURL]]
  val selfUrl: String
}


case class VSDCommonObjectInfo(id: Int, createdDate: String, description: Option[String], ontologyCount: Option[Int], `type`: Option[Int],
                               downloadUrl: String, license: Option[VSDURL], files: Seq[VSDURL], linkedObjects: Option[Seq[VSDURL]], linkedObjectRelations: Option[Seq[VSDURL]],
                               ontologyItems: Option[Seq[VSDURL]], ontologyItemRelations: Option[Seq[VSDURL]], objectPreviews: Option[Seq[VSDURL]], objectGroupRights: Option[Seq[VSDURL]],
                               objectUserRights: Option[Seq[VSDURL]], selfUrl: String) extends VSDObjectInfo

case class VSDRawImageObjectInfo(sliceThickness: Option[Float], spaceBetweenSlices: Option[Float], kilovoltPeak: Option[Float],
                                 modality: Option[VSDURL], id: Int, createdDate: String, description: Option[String], ontologyCount: Option[Int], `type`: Option[Int],
                                 downloadUrl: String, license: Option[VSDURL], files: Seq[VSDURL], linkedObjects: Option[Seq[VSDURL]], linkedObjectRelations: Option[Seq[VSDURL]],
                                 ontologyItems: Option[Seq[VSDURL]], ontologyItemRelations: Option[Seq[VSDURL]], objectPreviews: Option[Seq[VSDURL]], objectGroupRights: Option[Seq[VSDURL]],
                                 objectUserRights: Option[Seq[VSDURL]], selfUrl: String) extends VSDObjectInfo

case class VSDSegmentationObjectInfo(segmentationMethodDescription: Option[String], segmentationMethod: Option[VSDURL], id: Int, createdDate: String, description: Option[String], ontologyCount: Option[Int], `type`: Option[Int],
                                     downloadUrl: String, license: Option[VSDURL], files: Seq[VSDURL], linkedObjects: Option[Seq[VSDURL]], linkedObjectRelations: Option[Seq[VSDURL]],
                                     ontologyItems: Option[Seq[VSDURL]], ontologyItemRelations: Option[Seq[VSDURL]], objectPreviews: Option[Seq[VSDURL]], objectGroupRights: Option[Seq[VSDURL]],
                                     objectUserRights: Option[Seq[VSDURL]], selfUrl: String) extends VSDObjectInfo

case class VSDStatisticalModelObjectInfo(id: Int, createdDate: String, description: Option[String], ontologyCount: Option[Int], `type`: Option[Int],
                                         downloadUrl: String, license: Option[VSDURL], files: Seq[VSDURL], linkedObjects: Option[Seq[VSDURL]], linkedObjectRelations: Option[Seq[VSDURL]],
                                         ontologyItems: Option[Seq[VSDURL]], ontologyItemRelations: Option[Seq[VSDURL]], objectPreviews: Option[Seq[VSDURL]], objectGroupRights: Option[Seq[VSDURL]],
                                         objectUserRights: Option[Seq[VSDURL]], selfUrl: String) extends VSDObjectInfo


case class VSDUser(id: Int, username: String, selfUrl: String)

case class VSDPagination(rpp: Int, page: Int)

case class VSDOntology(key: Int, value: String)

case class VSDOntologies(types: Array[VSDOntology])

case class VSDOntologyItem(id: Int, term: String, `type`: Int, selfUrl: String)

case class VSDObjectOntologyItem(id: Int, position: Int, `type`: Int, `object`: VSDURL, ontologyItem: VSDURL, selfUrl: String)

case class VSDPaginatedList[A](totalCount: Int, pagination: VSDPagination, items: Array[A], nextPageUrl: Option[String])

case class VSDFolder(id: Int, name: String, level: Int, parentFolder: Option[VSDURL], childFolders: Option[Seq[VSDURL]], containedObjects: Option[Seq[VSDURL]], folderGroupRights: Option[Seq[VSDURL]], folderUserRights: Option[Seq[VSDURL]], selfUrl: String)

case class VSDLink(id: Int, description: String, object1: VSDURL, object2: VSDURL, selfUrl: String)

case class VSDModality(id: Int, name: String, description: String, selfUrl: String)

case class VSDSegmentationMethod(id: Int, name: String, selfUrl: String)

object VSDJson {

  implicit val VSDURLProtocol = jsonFormat1(VSDURL.apply)
  implicit val VSDObjectIdProtocol = jsonFormat1(VSDObjectID.apply)
  implicit val VSDFileIdProtocol = jsonFormat1(VSDFileID.apply)
  implicit val FileUploadResponseFormat = jsonFormat2(FileUploadResponse.apply)
  implicit val VSDRawImageObjectInfoProtocol = jsonFormat20(VSDRawImageObjectInfo)
  implicit val VSDSegmentationObjectInfoProtocol = jsonFormat18(VSDSegmentationObjectInfo)
  implicit val VSDCommonObjectInfoProtocol = jsonFormat16(VSDCommonObjectInfo)
  implicit val VSDStatModelObjectInfoProtocol = jsonFormat16(VSDStatisticalModelObjectInfo)
  implicit val VSDOntologyProtocol = jsonFormat2(VSDOntology)
  implicit val VSDOntologiesProtocol = jsonFormat1(VSDOntologies)
  implicit val VSDOntologyItemProtocol = jsonFormat4(VSDOntologyItem)
  implicit val VSDPaginationProtocol = jsonFormat2(VSDPagination)
  implicit val VSDOntologyItemsListPerTypeProtocol = jsonFormat4(VSDPaginatedList[VSDOntologyItem])
  implicit val VSDObjectOntologyItemProtocol = jsonFormat6(VSDObjectOntologyItem)
  implicit val VSDPaginatedListRawObjectsProtocol = jsonFormat4(VSDPaginatedList[VSDRawImageObjectInfo])
  implicit val VSDPaginatedListSegmentationObjectsProtocol = jsonFormat4(VSDPaginatedList[VSDSegmentationObjectInfo])
  implicit val VSDPaginatedCommonObjectProtocol = jsonFormat4(VSDPaginatedList[VSDCommonObjectInfo])
  implicit val VSDPaginatedStatModelObjectProtocol = jsonFormat4(VSDPaginatedList[VSDStatisticalModelObjectInfo])
  implicit val VSDFolderProtocol = jsonFormat9(VSDFolder)
  implicit val VSDPaginatedFolderProtocol = jsonFormat4(VSDPaginatedList[VSDFolder])
  implicit val VSDUserProtocol = jsonFormat3(VSDUser)
  implicit val VSDLinkProtocol = jsonFormat5(VSDLink)
  implicit val VSDModalityProtocol = jsonFormat4(VSDModality)
  implicit val VSDPaginatedModalityProtocol = jsonFormat4(VSDPaginatedList[VSDModality])
  implicit val VSDSegmentationMethodProtocol = jsonFormat3(VSDSegmentationMethod)
  implicit val VSDPaginatedSegMethodProtocol = jsonFormat4(VSDPaginatedList[VSDSegmentationMethod])
}