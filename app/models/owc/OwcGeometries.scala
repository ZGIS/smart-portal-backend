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

import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.context.jts.JtsSpatialContext
import org.locationtech.spatial4j.io.ShapeIO
import org.locationtech.spatial4j.shape.{Rectangle, Shape}
import play.api.data.validation.ValidationError
import play.api.libs.json._
import utils.ClassnameLogger

import scala.util.{Failure, Success, Try}

/**
  * spatial4j Rectangle / Geometries Json stuff
  */
object OwcGeometries extends ClassnameLogger {
}

/**
  * RectangleReader
  */
class RectangleReader extends Reads[Rectangle] with ClassnameLogger {

  private lazy val ctx = SpatialContext.GEO
  private lazy val jtsCtx = JtsSpatialContext.GEO
  private lazy val jtsGeoJsonReader = jtsCtx.getFormats().getReader(ShapeIO.GeoJSON)

  /**
    *
    * @param geoJsonString
    * @return
    */
  def jsToRect(geoJsonString: String): Option[Rectangle] = {
    Try(jtsGeoJsonReader.read(geoJsonString).asInstanceOf[Rectangle]) match {
      case Success(rect: Rectangle) => Some(rect)
      case Failure(ex) => {
        logger.warn(s"Problem reading geoJsonString to rectangle: ${ex.getMessage}")
        None
      }
    }
  }

  /**
    *
    * @param geoJsonJsValue
    * @return
    */
  def jsToRect(geoJsonJsValue: JsValue): Option[Rectangle] = {
    val geoJsonString = Json.stringify(geoJsonJsValue)
    jsToRect(geoJsonString)
  }

  /**
    *
    * @param json
    * @return
    */
  def reads(json: JsValue): JsResult[Rectangle] = {

    logger.warn(s"trying js to rect ${Json.stringify(json)}")
    val rectOpt = jsToRect(json)

    rectOpt match {
      case Some(s) => {
        JsSuccess(s)
      }
      case _ => {
        logger.error("JsError ValidationError error.expected.geometry")
        JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.rectangle"))))
      }
    }
  }
}

/**
  * RectangleWriter
  */
class RectangleWriter extends Writes[Rectangle] with ClassnameLogger {

  private lazy val ctx = SpatialContext.GEO
  private lazy val geoJsonWriter = ctx.getFormats().getWriter(ShapeIO.GeoJSON)

  def rectToJsValue(rect: Rectangle): JsValue = {
    Try(Json.parse(geoJsonWriter.toString(rect))) match {
      case Success(jsValue) => jsValue
      case Failure(ex) => {
        logger.warn(s"Problem writing rectangle to Json: ${ex.getMessage}")
        Json.parse(geoJsonWriter.toString(ctx.getWorldBounds))
      }
    }
  }

  def writes(rect: Rectangle): JsValue = {
    rectToJsValue(rect)
  }

}


/**
  * JTS Polygon Geometries Json stuff
  */

/**
  * RectangleReader
  */
class JtsPolygonReader extends Reads[Option[Rectangle]] with ClassnameLogger {

  private lazy val ctx = SpatialContext.GEO
  private lazy val jtsCtx = JtsSpatialContext.GEO
  private lazy val geoJsonReader = ctx.getFormats().getReader(ShapeIO.GeoJSON)
  private lazy val jtsGeoJsonReader = jtsCtx.getFormats().getReader(ShapeIO.GeoJSON)

  /**
    *
    * @param geoJsonString
    * @return
    */
  def jsToRect(geoJsonString: String): Option[Rectangle] = {

    val parse = Try {
      val geom = jtsGeoJsonReader.read(geoJsonString)
      logger.trace(s"jts geom ${geom.getBoundingBox}")
      geom
    }

    parse match {
      case Success(shape: Shape) => Some(shape.getBoundingBox)
      case Failure(ex) => {
        logger.error(s"Problem reading geoJsonString to rectangle: ${ex.getMessage}")
        None
      }
    }
  }

  /**
    *
    * @param geoJsonJsValue
    * @return
    */
  def jsToRect(geoJsonJsValue: JsValue): Option[Rectangle] = {
    val geoJsonString = Json.stringify(geoJsonJsValue)
    jsToRect(geoJsonString)
  }

  /**
    *
    * @param json
    * @return
    */
  def reads(json: JsValue): JsResult[Option[Rectangle]] = {
    val rectOpt = jsToRect(json)
    rectOpt match {
      case Some(s) => {
        JsSuccess(Some(s))
      }
      case None => {
        JsSuccess(None)
      }
      case _ => {
        logger.error("JsError ValidationError error.expected.geometry")
        JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.geometry"))))
      }
    }
  }
}

