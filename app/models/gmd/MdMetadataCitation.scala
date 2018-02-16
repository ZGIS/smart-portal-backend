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
import javax.inject.Inject

import play.api.libs.functional.syntax._
import play.api.libs.json._
import services.{MetadataService, ValidValuesReadsAdditions}
import utils.ClassnameLogger

import scala.xml.Node

/**
  * this trait is only needed for dependency injection of [[MetadataService]] into the companion object
  */
trait MdMetadataCitationTrait extends ValidValuesReadsAdditions {
  /* empty */
}

/**
  *
  * @param ciDate
  * @param ciDateType
  */
final case class MdMetadataCitation(ciDate: LocalDate,
                              ciDateType: String) extends Jsonable with Xmlable {
  def toXml(): Node = ???

  def toJson(): JsValue = {
    Json.toJson(this)
  }
}

object MdMetadataCitation extends ClassnameLogger with MdMetadataCitationTrait with
  JsonableCompanion[MdMetadataCitation] {
  /**
    * metadataService will be injected. for this to work you need to add
    * `bind(classOf[MdMetadataCitationTrait]).toInstance(MdMetadataCitation)`
    * to your implementation of `com.google.inject.AbstractModule.configure()`
    */
  @Inject() var metadataService: MetadataService = null

  implicit val reads: Reads[MdMetadataCitation] = (
    (JsPath \ "ciDate").read[LocalDate] and
      (JsPath \ "ciDateType").read[String](validValue("ciDateType"))
    ) (MdMetadataCitation.apply _)

  implicit val writes: Writes[MdMetadataCitation] = (
    (JsPath \ "ciDate").write[LocalDate] and
      (JsPath \ "ciDateType").write[String]
    ) (unlift(MdMetadataCitation.unapply))

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
