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

import org.locationtech.spatial4j.shape.Rectangle

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
sealed trait OwcFeatureType {
  val id: String
  val bbox: Option[Rectangle]
  val properties: OwcProperties
}

/**
  * implicit type is Feature
  *
  * @param id
  * @param bbox
  * @param properties
  * @param offerings // aka resources
  */
case class OwcEntry(
                     id: String,
                     bbox: Option[Rectangle],
                     properties: OwcProperties,
                     offerings: List[OwcOffering] // special here of course
                   ) extends OwcFeatureType

/**
  * the OwcDocument wraps it all up
  * implicit type is FeatureCollection
  * properties.links must contain a profile link element
  *
  * @param id
  * @param bbox
  * @param properties
  * @param features // aka the entries
  */
case class OwcDocument(
                        id: String,
                        bbox: Option[Rectangle],
                        properties: OwcProperties,
                        features: List[OwcEntry] // special here of course
                      ) extends OwcFeatureType



