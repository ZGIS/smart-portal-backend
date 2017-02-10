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
  * object for request contentType and POST data
  *
  * @param contentType
  * @param postData
  */
case class OwcPostRequestConfig(contentType: Option[String], postData: Option[String]) extends ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson: JsValue = Json.toJson(this)
}

/**
  * companion object for [[OwcPostRequestConfig]]
  */
object OwcPostRequestConfig extends ClassnameLogger {

  /**
    * OwcPostRequestConfig Json stuff
    */
  implicit val owcPostRequestConfigReads: Reads[OwcPostRequestConfig] = (
    (JsPath \ "type").readNullable[String] and
      (JsPath \ "request").readNullable[String]
    ) (OwcPostRequestConfig.apply _)

  implicit val owcPostRequestConfigWrites: Writes[OwcPostRequestConfig] = (
    (JsPath \ "type").writeNullable[String] and
      (JsPath \ "request").writeNullable[String]
    ) (unlift(OwcPostRequestConfig.unapply))

  implicit val owcPostRequestConfigFormat: Format[OwcPostRequestConfig] =
    Format(owcPostRequestConfigReads, owcPostRequestConfigWrites)

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
  def toJson: JsValue = Json.toJson(this)
}

/**
  * companion object for [[OwcRequestResult]]
  */
object OwcRequestResult extends ClassnameLogger {

  /**
    * OwcRequestResult Json stuff
    */
  implicit val owcRequestResultReads: Reads[OwcRequestResult] = (
    (JsPath \ "contentType").readNullable[String] and
      (JsPath \ "result").readNullable[String]
    ) (OwcRequestResult.apply _)

  implicit val owcRequestResultWrites: Writes[OwcRequestResult] = (
    (JsPath \ "contentType").writeNullable[String] and
      (JsPath \ "result").writeNullable[String]
    ) (unlift(OwcRequestResult.unapply))

  implicit val owcRequestResultFormat: Format[OwcRequestResult] =
    Format(owcRequestResultReads, owcRequestResultWrites)

}

/**
  * an owc offering can have multiple operations, e.g. typically GetCapabilities and a data retrieving operation,
  * which should correspond to the offering type code (e.g. WMS, WFS ..)
  *
  * @param code        operation code, e.g. GetCapabilities
  * @param method      GET, POST ...
  * @param contentType e.g. "application/xml", for expected return type (accept header?)
  * @param href        could be URL / URI type though
  * @param request     only need to hold data when method is POST
  * @param result      could hold inline result of the request, not sure if we need
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
  def toJson: JsValue = Json.toJson(this)
}

/**
  * companion object for [[OwcOperation]]
  */
object OwcOperation extends ClassnameLogger {

  implicit val owcOperationReads: Reads[OwcOperation] = (
    (JsPath \ "code").read[String] and
      (JsPath \ "method").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "href").read[String] and
      (JsPath \ "request").readNullable[OwcPostRequestConfig] and
      (JsPath \ "result").readNullable[OwcRequestResult]
    ) (OwcOperationJs.apply _)

  implicit val owcOperationWrites: Writes[OwcOperation] = (
    (JsPath \ "code").write[String] and
      (JsPath \ "method").write[String] and
      (JsPath \ "type").write[String] and
      (JsPath \ "href").write[String] and
      (JsPath \ "request").writeNullable[OwcPostRequestConfig] and
      (JsPath \ "result").writeNullable[OwcRequestResult]
    ) (unlift(OwcOperationJs.unapply))

  implicit val owcOperationFormat: Format[OwcOperation] =
    Format(owcOperationReads, owcOperationWrites)

  /**
    *
    * @param jsonString
    * @return
    */
  def parseJson(jsonString: String): Option[OwcOperation] = parseJson(Json.parse(jsonString))

  /**
    *
    * @param json
    * @return
    */
  def parseJson(json: JsValue): Option[OwcOperation] = {
    val resultFromJson: JsResult[OwcOperation] = Json.fromJson[OwcOperation](json)
    resultFromJson match {
      case JsSuccess(r: OwcOperation, path: JsPath) => Some(r)
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
  * OwcOperation Json stuff
  */
object OwcOperationJs {

  /**
    *
    * @param code
    * @param method
    * @param contentType
    * @param href
    * @param request
    * @param result
    * @return
    */
  def apply(code: String,
            method: String,
            contentType: String,
            href: String,
            request: Option[OwcPostRequestConfig],
            result: Option[OwcRequestResult]): OwcOperation = {
    // Todo, we might find a way to find an existing UUID from DB if entry exists
    val uuid = UUID.randomUUID()
    OwcOperation(uuid, code, method, contentType, href, request, result)
  }

  /**
    *
    * @param arg
    * @return
    */
  def unapply(arg: OwcOperation): Option[(String,
    String,
    String,
    String,
    Option[OwcPostRequestConfig],
    Option[OwcRequestResult])] = {
    Some(arg.code,
      arg.method,
      arg.contentType,
      arg.href,
      arg.request,
      arg.result)
  }
}


/**
  * trait OwcOffering
  */
sealed trait OwcOffering {
  val uuid: UUID
  val code: String
  val operations: List[OwcOperation]
  val content: List[String]

  def toJson: JsValue
}

object OwcOffering extends ClassnameLogger {

  implicit val owcOfferingReads: Reads[OwcOffering] = (
  (JsPath \ "code").read[String] and
  (JsPath \ "operations").read[List[OwcOperation]] and
  (JsPath \ "contents").read[List[String]]
  ) (OwcOfferingJs.apply _)

  implicit val owcOfferingWrites: Writes[OwcOffering] = (
  (JsPath \ "code").write[String] and
  (JsPath \ "operations").write[List[OwcOperation]] and
  (JsPath \ "contents").write[List[String]]
  ) (unlift(OwcOfferingJs.unapply))

  implicit val owcOfferingFormat: Format[OwcOffering] =
  Format(owcOfferingReads, owcOfferingWrites)

}


/**
  * OwcOffering Json stuff
  */
object OwcOfferingJs extends ClassnameLogger {

  private lazy val OwcReqPattern = "^http://www.opengis.net/spec/owc-(geojson|atom)/1.0/req/(wms|wmts|wfs|wcs|csw|wps|gml|kml|geotiff|sos|netcdf|http-link)$".r

  def buildOfferingFrom(offeringType: String, operations: List[OwcOperation], content: List[String]): OwcOffering = {
    // Todo, we might find a way to find an existing UUID from DB if entry exists
    val uuid = UUID.randomUUID()

    val geoJsonCode = "http://www.opengis.net/spec/owc-geojson/1.0/req/" + offeringType

    offeringType match {
      case "wms" => WmsOffering(uuid, geoJsonCode, operations, content)
      case "wmts" => WmtsOffering(uuid, geoJsonCode, operations, content)
      case "wfs" => WfsOffering(uuid, geoJsonCode, operations, content)
      case "wcs" => WcsOffering(uuid, geoJsonCode, operations, content)
      case "csw" => CswOffering(uuid, geoJsonCode, operations, content)
      case "wps" => WpsOffering(uuid, geoJsonCode, operations, content)
      case "gml" => GmlOffering(uuid, geoJsonCode, operations, content)
      case "kml" => KmlOffering(uuid, geoJsonCode, operations, content)
      case "geotiff" => GeoTiffOffering(uuid, geoJsonCode, operations, content)
      case "sos" => SosOffering(uuid, geoJsonCode, operations, content)
      case "netcdf" => NetCdfOffering(uuid, geoJsonCode, operations, content)
      case "http-link" => HttpLinkOffering(uuid, geoJsonCode, operations, content)
    }
  }

  /**
    *
    * @param code
    * @param operations
    * @param content
    * @return
    */
  def apply(code: String, operations: List[OwcOperation], content: List[String]): OwcOffering = {

    code match {
      case OwcReqPattern(owcEncoding, offeringType) => {
        logger.debug(s"$owcEncoding, $offeringType")
        buildOfferingFrom(offeringType, operations, content)
      }
      case _ => throw new IllegalArgumentException(s"offering code cannot be used to build offering type: $code")
    }
  }

  /**
    *
    * @param arg
    * @return
    */
  def unapply(arg: OwcOffering): Option[(String, List[OwcOperation], List[String])] = {
    Some(arg.code, arg.operations, arg.content)
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
  def toJson: JsValue = Json.toJson(this)
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
  def toJson: JsValue = Json.toJson(this)
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
  def toJson: JsValue = Json.toJson(this)
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
  def toJson: JsValue = Json.toJson(this)
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
  def toJson: JsValue = Json.toJson(this)
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
  def toJson: JsValue = Json.toJson(this)
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
  def toJson: JsValue = Json.toJson(this)
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
  def toJson: JsValue = Json.toJson(this)
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
  def toJson: JsValue = Json.toJson(this)
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
  def toJson: JsValue = Json.toJson(this)
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
  def toJson: JsValue = Json.toJson(this)
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
  def toJson: JsValue = Json.toJson(this)
}

/**
  * not in the spec, but we need them so I made up an extension
  *
  * @param uuid
  * @param code
  * @param operations
  * @param content
  */
case class X3dOffering(
                             uuid: UUID,
                             code: String = "http://www.opengis.net/spec/owc-geojson/1.0/req/x3d",
                             operations: List[OwcOperation],
                             content: List[String]
                           ) extends OwcOffering with ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson: JsValue = Json.toJson(this)
}


