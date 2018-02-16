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
import play.api.libs.json._
import utils.ClassnameLogger

import scala.xml.NodeSeq

/**
  * Minimal wrapper around SOS capabilities Service Identification Section
  */
final case class SosServiceMetadata(
                            abstrakt: Option[String],
                            fees: Option[String],
                            accessConstraints: Option[String],
                            providerName: Option[String],
                            providerSite: Option[String],
                            serviceContactName: Option[String],
                            serviceContactEmail: Option[String]
                          ) {
  def toJson(): JsValue = {
    Json.toJson(this)
  }
}

object SosServiceMetadata extends ClassnameLogger {
  implicit val reads: Reads[SosServiceMetadata] = (
    (JsPath \ "abstrakt").readNullable[String] and
      (JsPath \ "fees").readNullable[String] and
      (JsPath \ "accessConstraints").readNullable[String] and
      (JsPath \ "providerName").readNullable[String] and
      (JsPath \ "providerSite").readNullable[String] and
      (JsPath \ "serviceContactName").readNullable[String] and
      (JsPath \ "serviceContactEmail").readNullable[String]
    ) (SosServiceMetadata.apply _)

  implicit val writes: Writes[SosServiceMetadata] = (
    (JsPath \ "abstrakt").writeNullable[String] and
      (JsPath \ "fees").writeNullable[String] and
      (JsPath \ "accessConstraints").writeNullable[String] and
      (JsPath \ "providerName").writeNullable[String] and
      (JsPath \ "providerSite").writeNullable[String] and
      (JsPath \ "serviceContactName").writeNullable[String] and
      (JsPath \ "serviceContactEmail").writeNullable[String]
    ) (unlift(SosServiceMetadata.unapply))

  def fromJson(json: JsValue): Option[SosServiceMetadata] = {
    Json.fromJson[SosServiceMetadata](json) match {
      case JsSuccess(r: SosServiceMetadata, path: JsPath) => Some(r)
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

  def fromXml(nodeSeq: NodeSeq): Option[SosServiceMetadata] = {
    try {
      nodeSeq.head.label match {
        case "Capabilities" =>
          Some(SosServiceMetadata(
            abstrakt = Some((nodeSeq \ "ServiceIdentification" \ "Abstract").text),
            fees = Some((nodeSeq \ "ServiceIdentification" \ "Fees").text),
            accessConstraints = Some((nodeSeq \ "ServiceIdentification" \ "AccessConstraints").text),
            providerName = Some((nodeSeq \ "ServiceProvider" \ "ProviderName").text),
            providerSite = Some((nodeSeq \ "ServiceProvider" \ "ProviderSite" \ "@{http://www.w3.org/1999/xlink}href").text),
            serviceContactName = Some((nodeSeq \ "ServiceProvider" \ "ServiceContact" \ "IndividualName").text),
            serviceContactEmail = Some((nodeSeq \ "ServiceProvider" \ "ServiceContact" \ "ContactInfo" \ "Address" \"ElectronicMailAddress").text)
          ))
        case _ =>
          throw new IllegalArgumentException(f"Expected ServiceIdentification / ServiceProvider but found  ${nodeSeq.head.label}")
      }
    }
    catch {
      //FIXME SR replace by specific exceptions
      case e: Exception => logger.warn(f"Exception on parsing ServiceIdentification / ServiceProvider: ${e.getMessage}", e)
        None
    }
  }
}

