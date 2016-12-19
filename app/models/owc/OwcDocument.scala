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

import org.locationtech.spatial4j.shape.Rectangle
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
                   ) extends OwcFeatureType with ClassnameLogger {

/**
  *
  * @return
  */
def toJson: JsValue = Json.toJson(this)
}

/**
  * companion object for [[OwcEntry]]
  */
object OwcEntry extends ClassnameLogger {

  implicit val rectangleWrites: Writes[Rectangle] = new RectangleWriter()
  // implicit val rectangleReads: Reads[Rectangle] = new RectangleReader()
  implicit val rectangleReads: Reads[Option[Rectangle]] = new JtsPolygonReader()

  implicit val owcEntryReads: Reads[OwcEntry] = (
    (JsPath \ "type").read[String] and
    (JsPath \ "id").read[String] and
      (JsPath \ "geometry").readNullable[Option[Rectangle]] and
      (JsPath \ "properties").read[OwcProperties] and
      (JsPath \ "properties" \ "offerings").read[List[OwcOffering]]
    ) (OwcEntryJs.apply _)

  implicit val owcEntryWrites: Writes[OwcEntry] = (
    (JsPath \ "type").write[String] and
    (JsPath \ "id").write[String] and
      (JsPath \ "geometry").writeNullable[Rectangle] and
      (JsPath \ "properties").write[OwcProperties] and
      (JsPath \ "properties" \ "offerings").write[List[OwcOffering]]
    ) (unlift(OwcEntryJs.unapply))

  implicit val owcEntryFormat: Format[OwcEntry] =
    Format(owcEntryReads, owcEntryWrites)

  /**
    *
    * @param jsonString
    * @return
    */
  def parseJson(jsonString: String) : Option[OwcEntry] = parseJson(Json.parse(jsonString))

  /**
    *
    * @param json
    * @return
    */
  def parseJson(json: JsValue) : Option[OwcEntry] = {
    val resultFromJson: JsResult[OwcEntry] = Json.fromJson[OwcEntry](json)
    resultFromJson match {
      case JsSuccess(r: OwcEntry, path: JsPath) => Some(r)
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
  * OwcEntry Json stuff
  */
object OwcEntryJs extends ClassnameLogger {

  /**
    *
    * @param featureType
    * @param id
    * @param bbox
    * @param properties
    * @param offerings
    * @return
    */
  def apply(featureType: String, id: String,
            bbox: Option[Option[Rectangle]],
            properties: OwcProperties,
            offerings: List[OwcOffering]): OwcEntry = {

    if (!featureType.equalsIgnoreCase("feature")) {
      throw new IllegalArgumentException("featureType <type> must be <feature>")
    }
    OwcEntry(id,
      bbox.getOrElse(None),
      properties,
      offerings)
  }

  /**
    *
    * @param arg
    * @return
    */
  def unapply(arg: OwcEntry): Option[(
    String,
    String,
      Option[Rectangle],
      OwcProperties,
      List[OwcOffering])] = {
    Some("Feature", arg.id, arg.bbox, arg.properties, arg.offerings)
  }
}

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
                      ) extends OwcFeatureType with ClassnameLogger  {

  /**
    *
    * @return
    */
  def toJson: JsValue = Json.toJson(this)
}

/**
  * Companion Object to [[OwcDocument]]
  */
object OwcDocument extends ClassnameLogger {

  implicit val rectangleWrites: Writes[Rectangle] = new RectangleWriter()
  // implicit val rectangleReads: Reads[Rectangle] = new RectangleReader()
  implicit val rectangleReads: Reads[Option[Rectangle]] = new JtsPolygonReader()

  /**
    * OwcDocument Json stuff
    */
  implicit val owcDocumentReads: Reads[OwcDocument] = (
    (JsPath \ "type").read[String] and
    (JsPath \ "id").read[String] and
      (JsPath \ "geometry").readNullable[Option[Rectangle]] and
      (JsPath \ "properties").read[OwcProperties] and
      (JsPath \ "features").read[List[OwcEntry]]
    ) (OwcDocumentJs.apply _)

  implicit val owcDocumentWrites: Writes[OwcDocument] = (
    (JsPath \ "type").write[String] and
    (JsPath \ "id").write[String] and
      (JsPath \ "geometry").writeNullable[Rectangle] and
      (JsPath \ "properties").write[OwcProperties] and
      (JsPath \ "features").write[List[OwcEntry]]
    ) (unlift(OwcDocumentJs.unapply))

  implicit val owcDocumentFormat: Format[OwcDocument] =
    Format(owcDocumentReads, owcDocumentWrites)

  /**
    *
    * @param jsonString
    * @return
    */
  def parseJson(jsonString: String) : Option[OwcDocument] = parseJson(Json.parse(jsonString))

  /**
    *
    * @param json
    * @return
    */
  def parseJson(json: JsValue) : Option[OwcDocument] = {
    val resultFromJson: JsResult[OwcDocument] = Json.fromJson[OwcDocument](json)
    resultFromJson match {
      case JsSuccess(r: OwcDocument, path: JsPath) => Some(r)
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
  * OwcDocument Json stuff
  */
object OwcDocumentJs extends ClassnameLogger {

  /**
    *
    * @param featureType
    * @param id
    * @param bbox
    * @param properties
    * @param features
    * @return
    */
  def apply(featureType: String,
            id: String,
            bbox: Option[Option[Rectangle]],
            properties: OwcProperties,
            features: List[OwcEntry]): OwcDocument = {

    if (!featureType.equalsIgnoreCase("FeatureCollection")) {
      throw new IllegalArgumentException("featureType <type> must be <FeatureCollection>")
    }
    OwcDocument(id,
      bbox.getOrElse(None),
      properties,
      features)
  }

  /**
    *
    * @param arg
    * @return
    */
  def unapply(arg: OwcDocument): Option[(
    String,
      String,
      Option[Rectangle],
      OwcProperties,
      List[OwcEntry])] = {
    Some("FeatureCollection", arg.id, arg.bbox, arg.properties, arg.features)
  }
}
