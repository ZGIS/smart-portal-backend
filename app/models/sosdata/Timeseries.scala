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

package models.sosdata

import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json.{JsPath, _}
import utils.ClassnameLogger


/**
  * Timeseries to be used with SOS/Timeseries Viewer on Frontend.
  */
case class Timeseries(
                       sosUrl: String,
                       offering: String,
                       procedure: String,
                       observedProperty: String,
                       featureOfInterest: String,
                       fromDate: String,
                       toDate: String,
                       uom: Option[String],
                       timeseriesName: String,
                       data: Option[TimeseriesData]
                     ) {

  def toJson(): JsValue = {
    Json.toJson(this)
  }
}

/**
  * Timeseries configuration used by plotly
  *
  * @param x
  * @param y
  * @param mode
  * @param name
  * @param chartType
  * @param hoverinfo
  */
case class TimeseriesData(
                           x: Seq[String],
                           y: Seq[String],
                           mode: String = "lines",
                           name: String,
                           chartType: String = "scatter",
                           hoverinfo: String = "x+y"
                         ) {
  def toJson(): JsValue = {
    Json.toJson(this)
  }
}

object Timeseries extends ClassnameLogger {
  implicit val reads: Reads[Timeseries] = (
    (JsPath \ "sosUrl").read[String] and
      (JsPath \ "offering").read[String] and
      (JsPath \ "procedure").read[String] and
      (JsPath \ "observedProperty").read[String] and
      (JsPath \ "featureOfInterest").read[String] and
      (JsPath \ "fromDate").read[String] and
      (JsPath \ "toDate").read[String] and
      (JsPath \ "uom").readNullable[String] and
      (JsPath \ "timeseriesName").read[String] and
      (JsPath \ "data").readNullable[TimeseriesData]
    ) (Timeseries.apply _)

  implicit val writes: Writes[Timeseries] = (
    (JsPath \ "sosUrl").write[String] and
      (JsPath \ "offering").write[String] and
      (JsPath \ "procedure").write[String] and
      (JsPath \ "observedProperty").write[String] and
      (JsPath \ "featureOfInterest").write[String] and
      (JsPath \ "fromDate").write[String] and
      (JsPath \ "toDate").write[String] and
      (JsPath \ "uom").writeNullable[String] and
      (JsPath \ "timeseriesName").write[String] and
      (JsPath \ "data").writeNullable[TimeseriesData]
    ) (unlift(Timeseries.unapply))

  def fromJson(json: JsValue): Option[Timeseries] = {
    Json.fromJson[Timeseries](json) match {
      case JsSuccess(r: Timeseries, path: JsPath) => Some(r)
      case e: JsError => {
        val lines = e.errors.map { tupleAction =>
          val jsPath = tupleAction._1
          val valErrors = tupleAction._2.map(valErr => valErr.message).toList.mkString(" ; ")
          jsPath.toJsonString + " >> " + valErrors
        }

        logger.error(s"JsError info  ${lines.mkString(" | ")}")
        None
      }
    }
  }
}

object TimeseriesData extends ClassnameLogger {
  implicit val reads: Reads[TimeseriesData] = (
    (JsPath \ "x").read[Seq[String]] and
      (JsPath \ "y").read[Seq[String]] and
      (JsPath \ "mode").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "hoverinfo").read[String]
    ) (TimeseriesData.apply _)

  implicit val writes: Writes[TimeseriesData] = (
    (JsPath \ "x").write[Seq[String]] and
      (JsPath \ "y").write[Seq[String]] and
      (JsPath \ "mode").write[String] and
      (JsPath \ "name").write[String] and
      (JsPath \ "type").write[String] and
      (JsPath \ "hoverinfo").write[String]
    ) (unlift(TimeseriesData.unapply))

  def fromJson(json: JsValue): Option[TimeseriesData] = {
    Json.fromJson[TimeseriesData](json) match {
      case JsSuccess(r: TimeseriesData, path: JsPath) => Some(r)
      case e: JsError => {
        val lines = e.errors.map { tupleAction =>
          val jsPath = tupleAction._1
          val valErrors = tupleAction._2.map(valErr => valErr.message).toList.mkString(" ; ")
          jsPath.toJsonString + " >> " + valErrors
        }

        logger.error(s"JsError info  ${lines.mkString(" | ")}")
        None
      }
    }
  }
}