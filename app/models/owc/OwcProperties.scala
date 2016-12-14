/*
 * Copyright (C) 2011-2017 Interfaculty Department of Geoinformatics, University of
 * Salzburg (Z_GIS) & Institute of Geological and Nuclear Sciences Limited (GNS Science)
 * in the SMART Aquifer Characterisation (SAC) programme funded by the New Zealand
 * Ministry of Business, Innovation and Employment (MBIE)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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

import play.api.libs.json.{Json, _}
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
  def toJson = Json.toJson(this)
}

/**
  * companion object for [[OwcAuthor]]
  */
object OwcAuthor extends ClassnameLogger {
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
  def toJson = Json.toJson(this)
}

/**
  * companion object for [[OwcCategory]]
  */
object OwcCategory extends ClassnameLogger {
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
  def toJson = Json.toJson(this)
}

/**
  * companion object for [[OwcLink]]
  */
object OwcLink extends ClassnameLogger {
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
  def toJson = Json.toJson(this)
}

/**
  * companion object for [[OwcProperties]]
  */
object OwcProperties extends ClassnameLogger {

  /**
    *
    * @param jsonString
    * @return
    */
  def parseJson(jsonString: String) : Option[OwcProperties] = parseJson(Json.parse(jsonString))

  /**
    *
    * @param json
    * @return
    */
  def parseJson(json: JsValue) : Option[OwcProperties] = {
    val resultFromJson: JsResult[OwcProperties] = Json.fromJson[OwcProperties](json)
    resultFromJson match {
      case JsSuccess(r: OwcProperties, path: JsPath) => Some(r)
      case e: JsError => None
    }
  }
}