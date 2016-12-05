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

package models

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}

import play.api.db.Database
import utils.ClassnameLogger


/**
  * Model of OWS Context Documents and provide GeoJson encoding thereof (and maybe AtomXML)
  * An OWC document is an extended FeatureCollection, where the features (aka entries) hold a variety of metadata
  * about the things they provide the context for (i.e. other data sets, services, metadata records)
  * OWC documents do not duplicate a CSW MD_Metadata record, but a collection of referenced resources;
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
trait OwcFeatureType {
  val id: String
  val bbox: String
  val properties: OwcDefaultProperties
}

case class Author(name: String, email: String, uri: Option[String])

/**
  * reusable pattern of tagging things in the entry lists for declaration in subsequent processes,
  * e.g. accordeon groups in legends panel(we have to implement that in the mapviewer though)
  *
  * @param scheme e.g. for mapviewer: view-groups
  * @param term   identifier of a view group: nz-overview
  * @param label  human readable name of the term: New Zealand Overview, National Scale models..
  */
case class Category(scheme: String, term: String, label: String)

/**
  *
  * @param rel one of typically "self", "profile", "icon"
  * @param mimeType
  * @param href
  * @param title
  */
case class Link(rel: String, mimeType: String, href: String, title: Option[String])

/**
  *
  * @param lang
  * @param title
  * @param subtitle
  * @param updated
  * @param generator
  * @param rights
  * @param authors
  * @param contributors
  * @param categories
  * @param links
  */
case class OwcDefaultProperties(
                                 lang: Option[String],
                                 title: String,
                                 subtitle: Option[String],
                                 updated: ZonedDateTime,
                                 generator: Option[String],
                                 rights: String,
                                 authors: List[Author],
                                 contributors: List[Author],
                                 categories: List[Category],
                                 links: List[Link])


trait OwcOffering

case class WmsOffering() extends OwcOffering

case class WfsOffering() extends OwcOffering

case class WcsOffering() extends OwcOffering

case class SosOffering() extends OwcOffering

case class CswOffering() extends OwcOffering

case class PlainHttpOffering() extends OwcOffering

/**
  *
  * @param id
  * @param bbox
  * @param properties
  * @param entries
  */
case class OwcEntry(
                     id: String,
                     bbox: String,
                     properties: OwcDefaultProperties,
                     entries: List[OwcOffering] // special here of course
                   ) extends OwcFeatureType

/**
  * the OwcDocument wraps it all up
  *
  * @param id
  * @param bbox
  * @param properties
  * @param features
  */
case class OwcDocument(
                        id: String,
                        bbox: String,
                        properties: OwcDefaultProperties,
                        features: List[OwcEntry] // special here of course
                      ) extends OwcFeatureType


