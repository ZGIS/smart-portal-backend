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

package models.gmd

import java.time.LocalDate
import java.util.UUID

import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.ClassnameLogger

import scala.xml.Node

/**
  *
  * @param ciDate
  * @param ciType
  */
case class MdMetadataCitation(val ciDate: LocalDate,
                              val ciType: String) {
  def toXml(): Node = ???

  /**
    * creates JsValue from this class
    * @return JsValue
    */
  def toJson(): JsValue = {
    Json.toJson(this)
  }
}

object MdMetadataCitation extends ClassnameLogger with JsonableCompagnion[MdMetadataCitation] {
  implicit val reads: Reads[MdMetadataCitation] = (
    (JsPath \ "ciDate").read[LocalDate] and
      (JsPath \ "ciType").read[String]
    ) (MdMetadataCitation.apply _)

  implicit val writes: Writes[MdMetadataCitation] = (
    (JsPath \ "ciDate").write[LocalDate] and
      (JsPath \ "ciType").write[String]
    ) (unlift(MdMetadataCitation.unapply))

  implicit val format: Format[MdMetadataCitation] =
    Format(reads, writes)

  /**
    * parse object from Json
    *
    * @param json
    * @return Option if parsing error
    */
  override def fromJson(json: JsValue): Option[MdMetadataCitation] = {
    Json.fromJson[MdMetadataCitation](json) match {
      case JsSuccess(r: MdMetadataCitation, path: JsPath) => Some(r)
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
