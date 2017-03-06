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

package models.tvp

import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.ClassnameLogger

/**
  *
  * @param datetime
  * @param foiId
  * @param measUnit
  * @param measValue
  * @param geom
  * @param obsProp
  * @param procedure
  */
case class TimeValuePair(
                          datetime: String,
                          foiId: String,
                          measUnit: String,
                          measValue: String,
                          geom: Option[String],
                          obsProp: String,
                          procedure: String
                        ) extends ClassnameLogger {

  /**
    *
    * @return
    */
  def toJson: JsValue = Json.toJson(this)
}

/**
  *
  */
object TimeValuePair extends ClassnameLogger {

  implicit val tvpWrites: Writes[TimeValuePair] = (
    (JsPath \ "datetime").write[String] and
      (JsPath \ "foiId").write[String] and
      (JsPath \ "measUnit").write[String] and
      (JsPath \ "measValue").write[String] and
      (JsPath \ "geom").writeNullable[String] and
      (JsPath \ "obsProp").write[String] and
      (JsPath \ "procedure").write[String]
    ) (unlift(TimeValuePair.unapply))

}