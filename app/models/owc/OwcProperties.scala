/*
 * Copyright (c) 2011-2017 Interfaculty Department of Geoinformatics, University of
 * Salzburg (Z_GIS) & Institute of Geological and Nuclear Sciences Limited (GNS Science)
 * in the SMART Aquifer Characterisation (SAC) programme funded by the New Zealand
 * Ministry of Business, Innovation and Employment (MBIE)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models.owc

import java.time.ZonedDateTime
import java.util.UUID

import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.ClassnameLogger

/**
  * Model of OWS Context Documents and provide GeoJson encoding thereof (and maybe AtomXML)
  * An OWC document is an extended FeatureCollection, where the features (aka entries) hold a variety of metadata
  * about the things they provide the context for (i.e. other data sets, services, metadata records)
  * OWC documents do not duplicate a CSW MD_Metadata record, but a collection of referenced resources;
  *
  * http://www.opengeospatial.org/standards/owc
  *
  * Classically, the WMC documents (Web Map Context documents) were a list of WMS layers for a web map viewer
  * with a certain context, i.e. title, Bounding Box and a few visualisation properties like scale/zoom,
  * OWC has superseded that concept into a generic collection of resources:
  *
  * We use OWC primarily in the form of collections of case studies, preferably with at least two offerings per entry:
  * 1) a web visualisable form, e.g.WMS, WFS, SOS ...
  * 2) a CSW addressable MD_Metadata record according to the resource
  *
  * The OWC JSON Encoding is a profile of GeoJSON FeatureCollection, the XML encoding is a profile of Atom/GeoRSS Feed
  */

/**
  * Author field
  *
  * @param uuid
  * @param name
  * @param email
  * @param uri
  */
case class OwcAuthor(uuid: UUID, name: String, email: Option[String], uri: Option[String]) extends ClassnameLogger {



  /**
    *
    * @return
    */
  def toJson: JsValue = Json.toJson(this)
}

/**
  * companion object for [[OwcAuthor]]
  */
object OwcAuthor extends ClassnameLogger {

  implicit val owcAuthorReads: Reads[OwcAuthor] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "email").readNullable[String] and
      (JsPath \ "uri").readNullable[String]
    ) (OwcAuthorJs.apply _)

  implicit val owcAuthorWrites: Writes[OwcAuthor] = (
    (JsPath \ "name").write[String] and
      (JsPath \ "email").writeNullable[String] and
      (JsPath \ "uri").writeNullable[String]
    ) (unlift(OwcAuthorJs.unapply))

  implicit val owcAuthorFormat: Format[OwcAuthor] =
    Format(owcAuthorReads, owcAuthorWrites)
}

/**
  * OwcAuthor Json stuff
  */
object OwcAuthorJs extends ClassnameLogger {

  /**
    *
    * @param name
    * @param email
    * @param uri
    * @return
    */
  def apply(name: String, email: Option[String], uri: Option[String]): OwcAuthor = {
    // Todo, we might find a way to find an existing UUID from DB if entry exists
    val uuid = UUID.randomUUID()
    OwcAuthor(uuid, name, email, uri)
  }

  /**
    *
    * @param arg
    * @return
    */
  def unapply(arg: OwcAuthor): Option[(String, Option[String], Option[String])] = {
    Some(arg.name, arg.email, arg.uri)
  }
}


/**
  * reusable pattern of tagging things in the entry lists for declaration in subsequent processes,
  * e.g. accordeon groups in legends panel(we have to implement that in the mapviewer though)
  *
  * @param uuid
  * @param scheme e.g. for mapviewer: view-groups
  * @param term   identifier of a view group: nz-overview
  * @param label  human readable name of the term: New Zealand Overview, National Scale models..
  */
case class OwcCategory(uuid: UUID, scheme: String, term: String, label: Option[String]) extends ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson: JsValue = Json.toJson(this)
}

/**
  * companion object for [[OwcCategory]]
  */
object OwcCategory extends ClassnameLogger {

  implicit val owcCategoryReads: Reads[OwcCategory] = (
    (JsPath \ "scheme").read[String] and
      (JsPath \ "term").read[String] and
      (JsPath \ "label").readNullable[String]
    ) (OwcCategoryJs.apply _)

  implicit val owcCategoryWrites: Writes[OwcCategory] = (
    (JsPath \ "scheme").write[String] and
      (JsPath \ "term").write[String] and
      (JsPath \ "label").writeNullable[String]
    ) (unlift(OwcCategoryJs.unapply))

  implicit val owcCategoryFormat: Format[OwcCategory] =
    Format(owcCategoryReads, owcCategoryWrites)

}

/**
  * OwcCategory Json stuff
  */
object OwcCategoryJs extends ClassnameLogger {

  /**
    *
    * @param scheme
    * @param term
    * @param label
    * @return
    */
  def apply(scheme: String, term: String, label: Option[String]): OwcCategory = {
    // Todo, we might find a way to find an existing UUID from DB if entry exists
    val uuid = UUID.randomUUID()
    OwcCategory(uuid, scheme, term, label)
  }

  /**
    *
    * @param arg
    * @return
    */
  def unapply(arg: OwcCategory): Option[(String, String, Option[String])] = {
    Some(arg.scheme, arg.term, arg.label)
  }
}

/**
  *
  * @param uuid
  * @param rel one of typically "self", "profile", "icon", "via"
  * @param mimeType
  * @param href
  * @param title
  */
case class OwcLink(uuid: UUID, rel: String, mimeType: Option[String], href: String, title: Option[String]) extends ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson: JsValue = Json.toJson(this)
}

/**
  * companion object for [[OwcLink]]
  */
object OwcLink extends ClassnameLogger {

  implicit val owcLinkReads: Reads[OwcLink] = (
    (JsPath \ "rel").read[String] and
      (JsPath \ "type").readNullable[String] and
      (JsPath \ "href").read[String] and
      (JsPath \ "title").readNullable[String]
    ) (OwcLinkJs.apply _)

  implicit val owcLinkWrites: Writes[OwcLink] = (
    (JsPath \ "rel").write[String] and
      (JsPath \ "type").writeNullable[String] and
      (JsPath \ "href").write[String] and
      (JsPath \ "title").writeNullable[String]
    ) (unlift(OwcLinkJs.unapply))

  implicit val owcLinkFormat: Format[OwcLink] =
    Format(owcLinkReads, owcLinkWrites)

}

/**
  * OwcLink Json stuff
  */
object OwcLinkJs  extends ClassnameLogger {

  /**
    *
    * @param rel
    * @param mimeType
    * @param href
    * @param title
    * @return
    */
  def apply(rel: String, mimeType: Option[String], href: String, title: Option[String]): OwcLink = {
    // Todo, we might find a way to find an existing UUID from DB if entry exists
    val uuid = UUID.randomUUID()
    OwcLink(uuid, rel, mimeType, href, title)
  }

  /**
    *
    * @param arg
    * @return
    */
  def unapply(arg: OwcLink): Option[(String, Option[String], String, Option[String])] = {
    Some(arg.rel, arg.mimeType, arg.href, arg.title)
  }
}


/**
  *
  * @param uuid
  * @param language
  * @param title
  * @param subtitle // aka abstract / abstrakt, not sure why they called it subtitle in geojson spec of OWC
  * @param updated
  * @param generator
  * @param rights
  * @param authors
  * @param contributors
  * @param categories
  * @param links
  */
case class OwcProperties(
                          uuid: UUID,
                          language: String,
                          title: String,
                          subtitle: Option[String],
                          updated: Option[ZonedDateTime],
                          generator: Option[String],
                          rights: Option[String],
                          authors: List[OwcAuthor],
                          contributors: List[OwcAuthor],
                          creator: Option[String],
                          publisher: Option[String],
                          categories: List[OwcCategory],
                          links: List[OwcLink]) extends ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson: JsValue = Json.toJson(this)
}

/**
  * companion object for [[OwcProperties]]
  */
object OwcProperties extends ClassnameLogger {

  implicit val owcPropertiesReads: Reads[OwcProperties] = (
    (JsPath \ "lang").readNullable[String] and
      (JsPath \ "title").read[String] and
      (JsPath \ "subtitle").readNullable[String] and
      (JsPath \ "updated").readNullable[ZonedDateTime] and
      (JsPath \ "generator").readNullable[String] and
      (JsPath \ "rights").readNullable[String] and
      (JsPath \ "authors").read[List[OwcAuthor]] and
      (JsPath \ "contributors").read[List[OwcAuthor]] and
      (JsPath \ "creator").readNullable[String] and
      (JsPath \ "publisher").readNullable[String] and
      (JsPath \ "categories").read[List[OwcCategory]] and
      (JsPath \ "links").read[List[OwcLink]]
    ) (OwcPropertiesJs.apply _)

  implicit val owcPropertiesWrites: Writes[OwcProperties] = (
    (JsPath \ "lang").write[String] and
      (JsPath \ "title").write[String] and
      (JsPath \ "subtitle").writeNullable[String] and
      (JsPath \ "updated").writeNullable[ZonedDateTime] and
      (JsPath \ "generator").writeNullable[String] and
      (JsPath \ "rights").writeNullable[String] and
      (JsPath \ "authors").write[List[OwcAuthor]] and
      (JsPath \ "contributors").write[List[OwcAuthor]] and
      (JsPath \ "creator").writeNullable[String] and
      (JsPath \ "publisher").writeNullable[String] and
      (JsPath \ "categories").write[List[OwcCategory]] and
      (JsPath \ "links").write[List[OwcLink]]
    ) (unlift(OwcPropertiesJs.unapply))

  implicit val owcPropertiesFormat: Format[OwcProperties] =
    Format(owcPropertiesReads, owcPropertiesWrites)

  /**
    *
    * @param jsonString
    * @return
    */
  def parseJson(jsonString: String): Option[OwcProperties] = parseJson(Json.parse(jsonString))

  /**
    *
    * @param json
    * @return
    */
  def parseJson(json: JsValue): Option[OwcProperties] = {
    val resultFromJson: JsResult[OwcProperties] = Json.fromJson[OwcProperties](json)
    resultFromJson match {
      case JsSuccess(r: OwcProperties, path: JsPath) => Some(r)
      case e: JsError => {
        val lines = e.errors.map { tupleAction =>
          val jsPath = tupleAction._1
          val valErrors = tupleAction._2.map( valErr => valErr.message ).toList.mkString(" ; ")
          jsPath.toJsonString + " >> " +  valErrors
        }

        logger.error(s"JsError info  ${lines.mkString(" | ")}")
        None
      }
    }
  }
}

/**
  * OwcProperties Json stuff
  */
object OwcPropertiesJs extends ClassnameLogger {

  /**
    *
    * @param language
    * @param title
    * @param subtitle
    * @param updated
    * @param generator
    * @param rights
    * @param authors
    * @param contributors
    * @param creator
    * @param publisher
    * @param categories
    * @param links
    * @return
    */
  def apply(language: Option[String],
            title: String,
            subtitle: Option[String],
            updated: Option[ZonedDateTime],
            generator: Option[String],
            rights: Option[String],
            authors: List[OwcAuthor],
            contributors: List[OwcAuthor],
            creator: Option[String],
            publisher: Option[String],
            categories: List[OwcCategory],
            links: List[OwcLink]): OwcProperties = {
    // Todo, we might find a way to find an existing UUID from DB if entry exists
    val uuid = UUID.randomUUID()
    OwcProperties(uuid,
      language.getOrElse("en"),
      title,
      subtitle,
      updated,
      generator,
      rights,
      authors,
      contributors,
      creator,
      publisher,
      categories,
      links)
  }

  /**
    *
    * @param arg
    * @return
    */
  def unapply(arg: OwcProperties): Option[(String,
    String,
    Option[String],
    Option[ZonedDateTime],
    Option[String],
    Option[String],
    List[OwcAuthor],
    List[OwcAuthor],
    Option[String],
    Option[String],
    List[OwcCategory],
    List[OwcLink])] = {
    Some(arg.language,
      arg.title,
      arg.subtitle,
      arg.updated,
      arg.generator,
      arg.rights,
      arg.authors,
      arg.contributors,
      arg.creator,
      arg.publisher,
      arg.categories,
      arg.links)
  }
}

/**
  * Represents an uploaded file in OwnCollection
  * @param owcProperties this contains the file name
  * @param owcOperation  this contains the URL where it was uploaded to
  */
case class UploadedFileProperties(val owcProperties: OwcProperties,
                                  val owcOperation: OwcOperation) {
  implicit val reads: Reads[UploadedFileProperties] = (
    (JsPath \ "properties").read[OwcProperties] and
      (JsPath \ "operation").read[OwcOperation]
    ) (UploadedFilePropertiesJs.apply _)

  implicit val writes: Writes[UploadedFileProperties] = (
    (JsPath \ "properties").write[OwcProperties] and
      (JsPath \ "operation").write[OwcOperation]
    ) (unlift(UploadedFilePropertiesJs.unapply))

  def toJson: JsValue = Json.toJson(this);
}

/**
  * UploadedFileProperties Json stuff
  */
object UploadedFilePropertiesJs extends ClassnameLogger {

  def apply(owcProperties: OwcProperties,
            owcOperation: OwcOperation): UploadedFileProperties = {
    UploadedFileProperties(owcProperties, owcOperation)
  }

  def unapply(arg: UploadedFileProperties): Option[(OwcProperties, OwcOperation)] = {
    Some(arg.owcProperties, arg.owcOperation)
  }
}

