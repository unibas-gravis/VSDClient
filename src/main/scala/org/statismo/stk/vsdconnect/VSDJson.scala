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

case class VSDKeyValEntry(key : Int, value: String)

case class VSDObjectOptions(types : Seq[VSDKeyValEntry])

sealed case class VSDObjectRight(id: Int, name: String, rightValue: Int, selfUrl:String)
object VSDNoneRight extends VSDObjectRight(1, "None", 0,"https://demo.virtualskeleton.ch/api/object_rights/1")
object VSDVisitRight extends VSDObjectRight(2, "Visit", 1,"https://demo.virtualskeleton.ch/api/object_rights/1")
object VSDReadRight extends VSDObjectRight(3, "Read", 2,"https://demo.virtualskeleton.ch/api/object_rights/1")
object VSDDownloadRight extends VSDObjectRight(4, "Download", 4,"https://demo.virtualskeleton.ch/api/object_rights/1")
object VSDEditRight extends VSDObjectRight(5, "Edit", 8,"https://demo.virtualskeleton.ch/api/object_rights/1")
object VSDManageRight extends VSDObjectRight(6, "Manage", 16,"https://demo.virtualskeleton.ch/api/object_rights/1")
object VSDOwnerRight extends VSDObjectRight(7, "Owner", 32,"https://demo.virtualskeleton.ch/api/object_rights/1")

case class VSDObjectGroupRight(id : Int, relatedObject:VSDURL, relatedGroup: VSDURL, relatedRights: Seq[VSDURL], selfUrl: String)

case class VSDObjectUserRight(id : Int, relatedObject:VSDURL, relatedUser: VSDURL, relatedRights: Seq[VSDURL], selfUrl: String)

case class VSDGroup(id :Int, name: String, chief:Option[VSDURL], selfUrl: String)


object VSDJson {

  implicit val VSDURLProtocol : RootJsonFormat[VSDURL] = jsonFormat1(VSDURL.apply)
  implicit val VSDObjectIdProtocol : RootJsonFormat[VSDObjectID] = jsonFormat1(VSDObjectID.apply)
  implicit val VSDFileIdProtocol : RootJsonFormat[VSDFileID] = jsonFormat1(VSDFileID.apply)
  implicit val FileUploadResponseFormat  : RootJsonFormat[FileUploadResponse] = jsonFormat2(FileUploadResponse.apply)
  implicit val VSDRawImageObjectInfoProtocol : RootJsonFormat[VSDRawImageObjectInfo] = jsonFormat20(VSDRawImageObjectInfo)
  implicit val VSDSegmentationObjectInfoProtocol : RootJsonFormat[VSDSegmentationObjectInfo]= jsonFormat18(VSDSegmentationObjectInfo)
  implicit val VSDCommonObjectInfoProtocol : RootJsonFormat[VSDCommonObjectInfo] = jsonFormat16(VSDCommonObjectInfo)
  implicit val VSDStatModelObjectInfoProtocol : RootJsonFormat[VSDStatisticalModelObjectInfo] = jsonFormat16(VSDStatisticalModelObjectInfo)
  implicit val VSDOntologyProtocol : RootJsonFormat[VSDOntology] = jsonFormat2(VSDOntology)
  implicit val VSDOntologiesProtocol : RootJsonFormat[VSDOntologies] = jsonFormat1(VSDOntologies)
  implicit val VSDOntologyItemProtocol: RootJsonFormat[VSDOntologyItem] = jsonFormat4(VSDOntologyItem)
  implicit val VSDPaginationProtocol : RootJsonFormat[VSDPagination]= jsonFormat2(VSDPagination)
  implicit val VSDOntologyItemsListPerTypeProtocol : RootJsonFormat[VSDPaginatedList[VSDOntologyItem]]= jsonFormat4(VSDPaginatedList[VSDOntologyItem])
  implicit val VSDObjectOntologyItemProtocol : RootJsonFormat[VSDObjectOntologyItem]= jsonFormat6(VSDObjectOntologyItem)
  implicit val VSDPaginatedListRawObjectsProtocol: RootJsonFormat[VSDPaginatedList[VSDRawImageObjectInfo]] = jsonFormat4(VSDPaginatedList[VSDRawImageObjectInfo])
  implicit val VSDPaginatedListSegmentationObjectsProtocol : RootJsonFormat[VSDPaginatedList[VSDSegmentationObjectInfo]]= jsonFormat4(VSDPaginatedList[VSDSegmentationObjectInfo])
  implicit val VSDPaginatedCommonObjectProtocol : RootJsonFormat[VSDPaginatedList[VSDCommonObjectInfo]]= jsonFormat4(VSDPaginatedList[VSDCommonObjectInfo])
  implicit val VSDPaginatedStatModelObjectProtocol : RootJsonFormat[VSDPaginatedList[VSDStatisticalModelObjectInfo]]= jsonFormat4(VSDPaginatedList[VSDStatisticalModelObjectInfo])
  implicit val VSDFolderProtocol : RootJsonFormat[VSDFolder] = jsonFormat9(VSDFolder)
  implicit val VSDPaginatedFolderProtocol : RootJsonFormat[VSDPaginatedList[VSDFolder]] = jsonFormat4(VSDPaginatedList[VSDFolder])
  implicit val VSDUserProtocol : RootJsonFormat[VSDUser] = jsonFormat3(VSDUser)
  implicit val VSDLinkProtocol: RootJsonFormat[VSDLink] = jsonFormat5(VSDLink)
  implicit val VSDModalityProtocol : RootJsonFormat[VSDModality]= jsonFormat4(VSDModality)
  implicit val VSDPaginatedModalityProtocol : RootJsonFormat[VSDPaginatedList[VSDModality]]= jsonFormat4(VSDPaginatedList[VSDModality])
  implicit val VSDSegmentationMethodProtocol : RootJsonFormat[VSDSegmentationMethod]= jsonFormat3(VSDSegmentationMethod)
  implicit val VSDPaginatedSegMethodProtocol: RootJsonFormat[VSDPaginatedList[VSDSegmentationMethod]] = jsonFormat4(VSDPaginatedList[VSDSegmentationMethod])
  implicit val VSDKeyValEntryProtocol: RootJsonFormat[VSDKeyValEntry] = jsonFormat2(VSDKeyValEntry)
  implicit val VSDObjectOptionsProtocol: RootJsonFormat[VSDObjectOptions] = jsonFormat1(VSDObjectOptions)
  implicit val VSDObjectRightProtocol: RootJsonFormat[VSDObjectRight] = jsonFormat4(VSDObjectRight)
  implicit val VSDPaginatedRightsProtocol : RootJsonFormat[VSDPaginatedList[VSDObjectRight]]= jsonFormat4(VSDPaginatedList[VSDObjectRight])
  implicit val VSDGroupProtocol: RootJsonFormat[VSDGroup] = jsonFormat4(VSDGroup.apply)
  implicit val VSDObjectGroupRightProtocol: RootJsonFormat[VSDObjectGroupRight] = jsonFormat5(VSDObjectGroupRight.apply)
  implicit val VSDObjectUserRightProtocol: RootJsonFormat[VSDObjectUserRight] = jsonFormat5(VSDObjectUserRight.apply)
  implicit val VSDPaginatedGroupProtocol: RootJsonFormat[VSDPaginatedList[VSDGroup]] = jsonFormat4(VSDPaginatedList[VSDGroup])

}