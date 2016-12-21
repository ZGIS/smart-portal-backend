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
import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json._
import utils.ClassnameLogger
import javax.inject.Inject

import services.MetadataService

import scala.xml.Node


trait Xmlable {
  /**
    * creates JsValue from this class
    *
    * @return JsValue
    */
  def toXml(): Node
}

trait Jsonable {
  /**
    * creates JsValue from this class
    *
    * @return JsValue
    */
  def toJson(): JsValue
}

trait JsonableCompagnion[A] extends ClassnameLogger {
  /**
    * parse object from Json
    * @param json
    * @return Option if parsing error
    */
  def fromJson(json: JsValue): Option[A]
}

/**
  *
  * @param fileIdentifier
  * @param title
  * @param abstrakt
  * @param keywords
  * @param topicCategoryCode
  * @param hierarchyLevelName
  * @param scale
  * @param lineageStatement
  */
case class MdMetadata(val fileIdentifier: String,
                      val title: String,
                      val abstrakt: String,
                      val keywords: List[String],
                      val topicCategoryCode: String,
                      val hierarchyLevelName: String,
                      val scale: String,
                      val citation: MdMetadataCitation,
                      val lineageStatement: String
                     ) extends Jsonable with Xmlable {

  def toXml(): Node = ???

  def toJson(): JsValue = {
    Json.toJson(this)
  }
}

object MdMetadata extends ClassnameLogger with JsonableCompagnion[MdMetadata] {

  implicit val reads: Reads[MdMetadata] = (
    (JsPath \ "fileIdentifier").read[String](Reads.filterNot[String](ValidationError("String empty"))(_.trim().isEmpty))
      or Reads.pure(UUID.randomUUID().toString)
      and
      (JsPath \ "title").read[String] and
      (JsPath \ "abstrakt").read[String] and
      (JsPath \ "keywords").read[List[String]] and
      (JsPath \ "topicCategoryCode").read[String] and
      (JsPath \ "hierarchyLevelName").read[String] and
      (JsPath \ "scale").read[String] and
      (JsPath \ "citation").read[MdMetadataCitation] and
      (JsPath \ "lineageStatement").read[String]
    ) (MdMetadata.apply _)

  implicit val writes: Writes[MdMetadata] = (
    (JsPath \ "fileIdentifier").write[String] and
      (JsPath \ "title").write[String] and
      (JsPath \ "abstrakt").write[String] and
      (JsPath \ "keywords").write[List[String]] and
      (JsPath \ "topicCategoryCode").write[String] and
      (JsPath \ "hierarchyLevelName").write[String] and
      (JsPath \ "scale").write[String] and
      (JsPath \ "citation").write[MdMetadataCitation] and
      (JsPath \ "lineageStatement").write[String]
    ) (unlift(MdMetadata.unapply))

  implicit val format: Format[MdMetadata] =
    Format(reads, writes)

  def fromJson(json: JsValue): Option[MdMetadata] = {
    Json.fromJson[MdMetadata](json) match {
      case JsSuccess(r: MdMetadata, path: JsPath) => Some(r)
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

  def fromXml(node: Node): MdMetadata = ???
}
