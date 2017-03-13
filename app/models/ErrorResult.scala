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

package models

import play.api.libs.json.{Format, JsPath, Reads, Writes}
import play.api.libs.functional.syntax.{unlift, _}

/**
  * This class should be returned as JSON in case of Controller returning anything that is not OK() as HTTP-Response
  * like InternalServerError() etc.
  */
case class ErrorResult(message: String, details: Option[String]) { }

/** companion for {{ErrorResult}} case class
  * Implements a JSON reader and writer
  * */
object ErrorResult {
  implicit val reads: Reads[ErrorResult] = (
    (JsPath \ "message").read[String] and
      (JsPath \ "details").readNullable[String]
    ) (ErrorResult.apply _)

  implicit val writes: Writes[ErrorResult] = (
    (JsPath \ "message").write[String] and
      (JsPath \ "details").writeNullable[String]
    ) (unlift(ErrorResult.unapply))

  val format: Format[ErrorResult] = Format(reads, writes)

}

