/*
* Copyright 2015 University of Basel, Graphics and Vision Research Group
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package ch.unibas.cs.gravis.vsdconnect

import spray.json._
import DefaultJsonProtocol._

/**
 * VSDURL is the first clsss pointer to objects on the VSD. It can point to Files, Objects, Folders, Links, Ontologies, etc ...
 * Most of the client's API requests rely on a VSDURL to identify the objects to manipulate.
 *
 * @param selfUrl actual URL of the resource
 */
case class VSDURL(selfUrl: String)

/**
 * Result class of a File upload operation as returned by the VSD
 * @param file url of the successfully uploaded file
 * @param relatedObject url of the created object associated to this file. For example,several upload Dicom files will have the same related VSD object
 */
case class FileUploadResponse(file: VSDURL, relatedObject: VSDURL)


trait VSDObjectInfo {
  val id: Int
  val createdDate: String
  val name : String
  val description: Option[String]
  val ontologyCount: Option[Int]
  val `type`: Option[Int]
  val downloadUrl: String
  val license: Option[VSDURL]
  val files: VSDPaginatedList[VSDURL]
  val linkedObjects: Option[VSDPaginatedList[VSDURL]]
  val linkedObjectRelations: Option[VSDPaginatedList[VSDURL]]
  val ontologyItems: Option[VSDPaginatedList[VSDURL]]
  val ontologyItemRelations: Option[VSDPaginatedList[VSDURL]]
  val objectPreviews: Option[Seq[VSDURL]]
  val objectGroupRights: Option[Seq[VSDURL]]
  val objectUserRights: Option[Seq[VSDURL]]
  val selfUrl: String
}

/** *
  * Common VSD Object information that is present for all supported object types
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  *
  */
case class VSDCommonObjectInfo(id: Int, createdDate: String, name:String, description: Option[String], ontologyCount: Option[Int], `type`: Option[Int],
                               downloadUrl: String, license: Option[VSDURL], files: VSDPaginatedList[VSDURL], linkedObjects: Option[VSDPaginatedList[VSDURL]], linkedObjectRelations: Option[VSDPaginatedList[VSDURL]],
                               ontologyItems: Option[VSDPaginatedList[VSDURL]], ontologyItemRelations: Option[VSDPaginatedList[VSDURL]], objectPreviews: Option[Seq[VSDURL]], objectGroupRights: Option[Seq[VSDURL]],
                               objectUserRights: Option[Seq[VSDURL]], selfUrl: String) extends VSDObjectInfo

/** *
  * Information for VSD Objects of type RAW (i.e. raw intensity images). Additionally to the common object information, this adds optional fields such as slice thickness, inter-spacing, signal strength, modality, ..
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  *
  */
case class VSDRawImageObjectInfo(sliceThickness: Option[Float], spaceBetweenSlices: Option[Float], kilovoltPeak: Option[Float],
                                 modality: Option[VSDURL], id: Int, createdDate: String, name:String, description: Option[String], ontologyCount: Option[Int], `type`: Option[Int],
                                 downloadUrl: String, license: Option[VSDURL], files: VSDPaginatedList[VSDURL], linkedObjects: Option[VSDPaginatedList[VSDURL]], linkedObjectRelations: Option[VSDPaginatedList[VSDURL]],
                                 ontologyItems: Option[VSDPaginatedList[VSDURL]], ontologyItemRelations: Option[VSDPaginatedList[VSDURL]], objectPreviews: Option[Seq[VSDURL]], objectGroupRights: Option[Seq[VSDURL]],
                                 objectUserRights: Option[Seq[VSDURL]], selfUrl: String) extends VSDObjectInfo

/** *
  * Information for VSD Objects of type Segmentation (i.e. binary images). Additionally to the common object information, this adds optional fields such as segmentation method, and a description ..
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  *
  */
case class VSDSegmentationObjectInfo(segmentationMethodDescription: Option[String], segmentationMethod: Option[VSDURL], id: Int, createdDate: String, name:String, description: Option[String], ontologyCount: Option[Int], `type`: Option[Int],
                                     downloadUrl: String, license: Option[VSDURL], files: VSDPaginatedList[VSDURL], linkedObjects: Option[VSDPaginatedList[VSDURL]], linkedObjectRelations: Option[VSDPaginatedList[VSDURL]],
                                     ontologyItems: Option[VSDPaginatedList[VSDURL]], ontologyItemRelations: Option[VSDPaginatedList[VSDURL]], objectPreviews: Option[Seq[VSDURL]], objectGroupRights: Option[Seq[VSDURL]],
                                     objectUserRights: Option[Seq[VSDURL]], selfUrl: String) extends VSDObjectInfo


/** *
  * Information for VSD Objects of type Statistical Model (i.e. h5 files)
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  *
  */
case class VSDStatisticalModelObjectInfo(id: Int, createdDate: String, name:String, description: Option[String], ontologyCount: Option[Int], `type`: Option[Int],
                                         downloadUrl: String, license: Option[VSDURL], files: VSDPaginatedList[VSDURL], linkedObjects: Option[VSDPaginatedList[VSDURL]], linkedObjectRelations: Option[VSDPaginatedList[VSDURL]],
                                         ontologyItems: Option[VSDPaginatedList[VSDURL]], ontologyItemRelations: Option[VSDPaginatedList[VSDURL]], objectPreviews: Option[Seq[VSDURL]], objectGroupRights: Option[Seq[VSDURL]],
                                         objectUserRights: Option[Seq[VSDURL]], selfUrl: String) extends VSDObjectInfo

/**
 * Class identifying a user on the VSD
 * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
 *
 */
case class VSDUser(id: Int, username: String, selfUrl: String)

/** *
  * Internal class to handle paginated responses.
  *
  * (required to be public due to Marshalling implicits)
  */
case class VSDPagination(rpp: Int, page: Int)

/** *
  * Class of VSD Ontologies. An ontology on the VSD, is a set of statndardized denomination of anatomical organs. An example of such an ontology is the
  * [[https://en.wikipedia.org/wiki/Foundational_Model_of_Anatomy Foundational Model of Anatomy (FMA)]]
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  *
  */
case class VSDOntology(key: Int, value: String)

/** *
  * Class of lists of [[VSDOntology]] returned upon request
  *
  */
case class VSDOntologies(types: Array[VSDOntology])


/** *
  * Class of an item belonging to an ontology supported by the VSD. This is typically the denomination of a particular organ of interest in the given ontology.
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  *
  */
case class VSDOntologyItem(id: Int, term: String, `type`: Int, selfUrl: String)


/** *
  * Relation between an ontology item and a VSD object. This is used to say that the VSD object is a depiction of a particular organ
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  *
  */
case class VSDObjectOntologyItem(id: Int, position: Int, `type`: Int, `object`: VSDURL, ontologyItem: VSDURL, selfUrl: String)

/** *
  * Internal class to handle paginated responses.
  *
  * (required to be public due to Marshalling implicits)
  */
case class VSDPaginatedList[A](totalCount: Int, pagination: VSDPagination, items: Array[A], nextPageUrl: Option[String])


/** *
  * Class of Folders on the VSD
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
case class VSDFolder(id: Int, name: String, level: Int, parentFolder: Option[VSDURL], childFolders: Option[Seq[VSDURL]], containedObjects: Option[Seq[VSDURL]], folderGroupRights: Option[Seq[VSDURL]], folderUserRights: Option[Seq[VSDURL]], selfUrl: String)

/** *
  * Class of Links on the VSD. a link indicates a relation between 2 objects eg. object 2 is the segmentation of the raw object 1
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
case class VSDLink(id: Int, description: String, object1: VSDURL, object2: VSDURL, selfUrl: String)

/** *
  * Class of Image modalities supported by the VSD (CT, OT,). A detailed list of modality names (not necessarily all supported by the VSD) can be found [[https://wiki.nci.nih.gov/display/CIP/Key+to+two-letter+Modality+Acronyms+in+DICOM here]]
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
case class VSDModality(id: Int, name: String, description: String, selfUrl: String)

/** *
  * Class of Segmentation methods supported by the VSD. These are for example: Manual, Automatic. Semi-Automatic
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
case class VSDSegmentationMethod(id: Int, name: String, selfUrl: String)

/** *
  * Class of Key-Value entries
  */
case class VSDKeyValEntry(key : Int, value: String)

/** *
  * Class summing object types supported by the VSD. These can be RawImage,Segmentation, StatisticalModel, ..
  */
case class VSDObjectOptions(types : Seq[VSDKeyValEntry])


/** *
  * Class of rights than can be attributed to VSD objects and Folders
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
sealed case class VSDObjectRight(id: Int, name: String, rightValue: Int, selfUrl:String)
/** *
  * None right than can be attributed to VSD objects and Folders
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
object VSDNoneRight extends VSDObjectRight(1, "None", 0,"https://demo.virtualskeleton.ch/api/object_rights/1")
/** *
  * Visit right than can be attributed to VSD objects and Folders
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
object VSDVisitRight extends VSDObjectRight(2, "Visit", 1,"https://demo.virtualskeleton.ch/api/object_rights/1")
/** *
  * Read right than can be attributed to VSD objects and Folders
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
object VSDReadRight extends VSDObjectRight(3, "Read", 2,"https://demo.virtualskeleton.ch/api/object_rights/1")
/** *
  * Download right than can be attributed to VSD objects and Folders
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
object VSDDownloadRight extends VSDObjectRight(4, "Download", 4,"https://demo.virtualskeleton.ch/api/object_rights/1")
/** *
  * Edit right than can be attributed to VSD objects and Folders
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
object VSDEditRight extends VSDObjectRight(5, "Edit", 8,"https://demo.virtualskeleton.ch/api/object_rights/1")
/** *
  * Manage right than can be attributed to VSD objects and Folders
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
object VSDManageRight extends VSDObjectRight(6, "Manage", 16,"https://demo.virtualskeleton.ch/api/object_rights/1")
/** *
  * Owner right than can be attributed to VSD objects and Folders
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
object VSDOwnerRight extends VSDObjectRight(7, "Owner", 32,"https://demo.virtualskeleton.ch/api/object_rights/1")

/** *
  * Class indicating rights attributed to a group on a VSD object
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
case class VSDObjectGroupRight(id : Int, relatedObject:VSDURL, relatedGroup: VSDURL, relatedRights: Seq[VSDURL], selfUrl: String)
/** *
  * Class indicating rights attributed to a users on a VSD object
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
case class VSDObjectUserRight(id : Int, relatedObject:VSDURL, relatedUser: VSDURL, relatedRights: Seq[VSDURL], selfUrl: String)


/** *
  * Class of groups (user groups) on the VSD
  * The sub-fields names should be self-explanatory. In case of doubt, please check the [[https://www.virtualskeleton.ch/api/Help VSD's API doc]]
  */
case class VSDGroup(id :Int, name: String, chief:Option[VSDURL], selfUrl: String)

/** *
  * Defines variables required de/serializing VSD related classes from/to Json
  *
  */
object VSDJson {

  implicit val VSDURLProtocol : RootJsonFormat[VSDURL] = jsonFormat1(VSDURL.apply)
  implicit val FileUploadResponseFormat  : RootJsonFormat[FileUploadResponse] = jsonFormat2(FileUploadResponse.apply)
  implicit val VSDRawImageObjectInfoProtocol : RootJsonFormat[VSDRawImageObjectInfo] = rootFormat(lazyFormat(jsonFormat21(VSDRawImageObjectInfo)))
  implicit val VSDSegmentationObjectInfoProtocol : RootJsonFormat[VSDSegmentationObjectInfo]= rootFormat(lazyFormat(jsonFormat19(VSDSegmentationObjectInfo)))
  implicit val VSDCommonObjectInfoProtocol : RootJsonFormat[VSDCommonObjectInfo] = rootFormat(lazyFormat(jsonFormat17(VSDCommonObjectInfo)))
  implicit val VSDStatModelObjectInfoProtocol : RootJsonFormat[VSDStatisticalModelObjectInfo] = rootFormat(lazyFormat(jsonFormat17(VSDStatisticalModelObjectInfo)))
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
  implicit val VSDPaginatedURLProtocol: RootJsonFormat[VSDPaginatedList[VSDURL]] = jsonFormat4(VSDPaginatedList[VSDURL])
}