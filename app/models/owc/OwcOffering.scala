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

import java.util.UUID

import org.locationtech.spatial4j.shape.Rectangle
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
  * trait OwcOffering
  */
sealed trait OwcOffering {
  val uuid: UUID
  val code: String
  val operations: List[OwcOperation]
  val content: List[String]

  def toJson
}

/**
  * object for request contentType and POST data
  *
  * @param contentType
  * @param postData
  */
case class OwcPostRequestConfig(contentType: Option[String], postData: Option[String])extends ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

/**
  * companion object for [[OwcPostRequestConfig]]
  */
object OwcPostRequestConfig extends ClassnameLogger {
}

/**
  * object for result contentType and POST data
  *
  * @param contentType
  * @param resultData
  */
case class OwcRequestResult(contentType: Option[String], resultData: Option[String]) extends ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

/**
  * companion object for [[OwcRequestResult]]
  */
object OwcRequestResult extends ClassnameLogger {
}

/**
  * an owc offering can have multiple operations, e.g. typically GetCapabilities and a data retrieving operation,
  * which should correspond to the offering type code (e.g. WMS, WFS ..)
  *
  * @param code operation code, e.g. GetCapabilities
  * @param method GET, POST ...
  * @param contentType e.g. "application/xml", for expected return type (accept header?)
  * @param href could be URL / URI type though
  * @param request only need to hold data when method is POST
  * @param result could hold inline result of the request, not sure if we need
  */
case class OwcOperation(uuid: UUID,
                        code: String,
                        method: String,
                        contentType: String,
                        href: String, request:
                        Option[OwcPostRequestConfig],
                        result: Option[OwcRequestResult]) extends ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

/**
  * companion object for [[OwcOperation]]
  */
object OwcOperation extends ClassnameLogger {
  /**
    *
    * @param jsonString
    * @return
    */
  def parseJson(jsonString: String) : Option[OwcOperation] = parseJson(Json.parse(jsonString))

  /**
    *
    * @param json
    * @return
    */
  def parseJson(json: JsValue) : Option[OwcOperation] = {
    val resultFromJson: JsResult[OwcOperation] = Json.fromJson[OwcOperation](json)
    resultFromJson match {
      case JsSuccess(r: OwcOperation, path: JsPath) => Some(r)
      case e: JsError => None
    }
  }
}

/**
  *
  * @param uuid
  * @param code
  * @param operations
  * @param content
  */
case class WmsOffering(
                        uuid: UUID,
                        code: String = "http://www.opengis.net/spec/owc-geojson/1.0/req/wms",
                        operations: List[OwcOperation],
                        content: List[String]
                      ) extends OwcOffering with ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

/**
  *
  * @param uuid
  * @param code
  * @param operations
  * @param content
  */
case class WmtsOffering(
                         uuid: UUID,
                         code: String = "http://www.opengis.net/spec/owc-geojson/1.0/req/wmts",
                         operations: List[OwcOperation],
                         content: List[String]
                       ) extends OwcOffering with ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

/**
  *
  * @param uuid
  * @param code
  * @param operations
  * @param content
  */
case class WfsOffering(
                        uuid: UUID,
                        code: String = "http://www.opengis.net/spec/owc-geojson/1.0/req/wfs",
                        operations: List[OwcOperation],
                        content: List[String]
                      ) extends OwcOffering with ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

/**
  *
  * @param uuid
  * @param code
  * @param operations
  * @param content
  */
case class WcsOffering(
                        uuid: UUID,
                        code: String = "http://www.opengis.net/spec/owc-geojson/1.0/req/wcs",
                        operations: List[OwcOperation],
                        content: List[String]
                      ) extends OwcOffering with ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

/**
  *
  * @param uuid
  * @param code
  * @param operations
  * @param content
  */
case class CswOffering(
                        uuid: UUID,
                        code: String = "http://www.opengis.net/spec/owc-geojson/1.0/req/csw",
                        operations: List[OwcOperation],
                        content: List[String]
                      ) extends OwcOffering with ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

/**
  *
  * @param uuid
  * @param code
  * @param operations
  * @param content
  */
case class WpsOffering(
                        uuid: UUID,
                        code: String = "http://www.opengis.net/spec/owc-geojson/1.0/req/wps",
                        operations: List[OwcOperation],
                        content: List[String]
                      ) extends OwcOffering with ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

/**
  *
  * @param uuid
  * @param code
  * @param operations
  * @param content
  */
case class GmlOffering(
                        uuid: UUID,
                        code: String = "http://www.opengis.net/spec/owc-geojson/1.0/req/gml",
                        operations: List[OwcOperation],
                        content: List[String]
                      ) extends OwcOffering with ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

/**
  *
  * @param uuid
  * @param code
  * @param operations
  * @param content
  */
case class KmlOffering(
                        uuid: UUID,
                        code: String = "http://www.opengis.net/spec/owc-geojson/1.0/req/kml",
                        operations: List[OwcOperation],
                        content: List[String]
                      ) extends OwcOffering with ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

/**
  *
  * @param uuid
  * @param code
  * @param operations
  * @param content
  */
case class GeoTiffOffering(
                            uuid: UUID,
                            code: String = "http://www.opengis.net/spec/owc-geojson/1.0/req/geotiff",
                            operations: List[OwcOperation],
                            content: List[String]
                          ) extends OwcOffering with ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

// the following two are not in the spec, but we need them so I made up an extension

/**
  * not in the spec, but we need them so I made up an extension
  *
  * @param uuid
  * @param code
  * @param operations
  * @param content
  */
case class SosOffering(
                        uuid: UUID,
                        code: String = "http://www.opengis.net/spec/owc-geojson/1.0/req/sos",
                        operations: List[OwcOperation],
                        content: List[String]
                      ) extends OwcOffering with ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

/**
  * not in the spec, but we need them so I made up an extension
  *
  * @param uuid
  * @param code
  * @param operations
  * @param content
  */
case class NetCdfOffering(
                           uuid: UUID,
                           code: String = "http://www.opengis.net/spec/owc-geojson/1.0/req/netcdf",
                           operations: List[OwcOperation],
                           content: List[String]
                         ) extends OwcOffering with ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}

/**
  * not in the spec, but we need them so I made up an extension
  *
  * @param uuid
  * @param code
  * @param operations
  * @param content
  */
case class HttpLinkOffering(
                             uuid: UUID,
                             code: String = "http://www.opengis.net/spec/owc-geojson/1.0/req/http-link",
                             operations: List[OwcOperation],
                             content: List[String]
                           ) extends OwcOffering with ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson = Json.toJson(this)
}


