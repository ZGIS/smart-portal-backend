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
  * Minimal wrapper around SOS capabilities
  */
case class SosCapabilities(
                          title: String,
                          sosUrl: String,
                          featuresOfInterest: Seq[String],
                          offerings: Seq[String],
                          observedProperties: Seq[String],
                          procedures: Seq[String]
                          ) {
  def toJson(): JsValue = {
    Json.toJson(this)
  }
}

object SosCapabilities extends ClassnameLogger {
  implicit val reads: Reads[SosCapabilities] = (
    (JsPath \ "title").read[String] and
      (JsPath \ "sosUrl").read[String] and
      (JsPath \ "featuresOfInterest").read[Seq[String]] and
      (JsPath \ "offerings").read[Seq[String]] and
      (JsPath \ "observedProperties").read[Seq[String]] and
      (JsPath \ "procedures").read[Seq[String]]
    ) (SosCapabilities.apply _)

  implicit val writes: Writes[SosCapabilities] = (
    (JsPath \ "title").write[String] and
      (JsPath \ "sosUrl").write[String] and
      (JsPath \ "featuresOfInterest").write[Seq[String]] and
      (JsPath \ "offerings").write[Seq[String]] and
      (JsPath \ "observedProperties").write[Seq[String]] and
      (JsPath \ "procedures").write[Seq[String]]
    ) (unlift(SosCapabilities.unapply))

  def fromJson(json: JsValue): Option[SosCapabilities] = {
    Json.fromJson[SosCapabilities](json) match {
      case JsSuccess(r: SosCapabilities, path: JsPath) => Some(r)
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

  def fromXml(nodeSeq: NodeSeq, sosUrl: String): Option[SosCapabilities] = {
    try {
      nodeSeq.head.label match {
        case "Capabilities" =>
          Some(SosCapabilities(
            (nodeSeq \ "ServiceIdentification" \ "Title").text,
            sosUrl,
            parseValuesFromOperationsMetadata(
              (nodeSeq \\ "OperationsMetadata" \ "Operation") filter(n => (n \ "@name" toString) == "GetObservation"),
              "featureOfInterest"),
            parseValuesFromOperationsMetadata(
              (nodeSeq \\ "OperationsMetadata" \ "Operation") filter(n => (n \ "@name" toString) == "GetObservation"),
              "offering"),
            parseValuesFromOperationsMetadata(
              (nodeSeq \\ "OperationsMetadata" \ "Operation") filter(n => (n \ "@name" toString) == "GetObservation"),
              "observedProperty"),
            parseValuesFromOperationsMetadata(
              (nodeSeq \\ "OperationsMetadata" \ "Operation") filter(n => (n \ "@name" toString) == "GetObservation"),
              "procedure")
          ))
        case _ =>
          throw new IllegalArgumentException(f"Expected MDMetadataNode but found  ${nodeSeq.head.label}")
      }
    }
    catch {
      //FIXME SR replace by specific exceptions
      case e: Exception => logger.warn(f"Exception on parsing MD_Metadata: ${e.getMessage}", e)
        None
    }  }

  private def parseValuesFromOperationsMetadata(nodeseq: NodeSeq, parameter: String): Seq[String] = {
    val param = (nodeseq \ "Parameter") filter(node => (node \ "@name" toString) == parameter)
    (param \ "AllowedValues" \\ "Value").map(_.text)
  }
}
