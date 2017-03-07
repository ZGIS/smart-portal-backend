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

import javax.inject.Inject

import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json._
import services.{MetadataService, ValidValuesReadsAdditions}

import scala.xml.Node

/**
  * this trait is only needed for dependency injection of [[MetadataService]] into the companion object
  */
trait MdMetadataDistributionTrait extends ValidValuesReadsAdditions {
  /* empty */
}

/**
  * Created by steffen on 21.12.16.
  */
case class MdMetadataDistribution(useLimitation: String,
                                  formatName: String,
                                  formatVersion: String,
                                  onlineResourceLinkage: String
                                 ) extends Jsonable with Xmlable {
  /**
    * creates JsValue from this class
    *
    * @return JsValue
    */
  override def toJson(): JsValue = {
    Json.toJson(this)
  }


  /**
    * creates JsValue from this class
    *
    * @return JsValue
    */
  override def toXml(): Node = <gmd:MD_Distribution>
    <gmd:distributionFormat><gmd:MD_Format>
      <gmd:name><gco:CharacterString>{this.formatName}</gco:CharacterString></gmd:name>
      <gmd:version><gco:CharacterString>{this.formatVersion}</gco:CharacterString></gmd:version>
    </gmd:MD_Format></gmd:distributionFormat>
    <gmd:transferOptions>
      <gmd:MD_DigitalTransferOptions>
        <gmd:onLine>
          <gmd:CI_OnlineResource>
            <gmd:linkage>
              <gmd:URL>{this.onlineResourceLinkage}</gmd:URL>
            </gmd:linkage>
          </gmd:CI_OnlineResource>
        </gmd:onLine>
      </gmd:MD_DigitalTransferOptions>
    </gmd:transferOptions>
  </gmd:MD_Distribution>

}

object MdMetadataDistribution extends MdMetadataDistributionTrait with
  JsonableCompanion[MdMetadataDistribution] {
  /**
    * metadataService will be injected. for this to work you need to add
    * `bind(classOf[MdMetadataDistributionTrait]).toInstance(MdMetadataDistribution)`
    * to your implementation of `com.google.inject.AbstractModule.configure()`
    */
  @Inject() override var metadataService: MetadataService = null

  override implicit val reads: Reads[MdMetadataDistribution] = (
    (JsPath \ "useLimitation").read[String](validValue("useLimitation")) and
      (JsPath \ "formatName").read[String] and
      (JsPath \ "formatVersion").read[String](validValue("formatVersion")) and
      (JsPath \ "onlineResourceLinkage").read[String]
    ) (MdMetadataDistribution.apply _)

  override implicit val writes: Writes[MdMetadataDistribution] = (
    (JsPath \ "useLimitation").write[String] and
      (JsPath \ "formatName").write[String] and
      (JsPath \ "formatVersion").write[String] and
      (JsPath \ "onlineResourceLinkage").write[String]
    ) (unlift(MdMetadataDistribution.unapply))

  /**
    * parseOm2Measurements object from Json
    *
    * @param json
    * @return Option if parsing error
    */
  override def fromJson(json: JsValue): Option[MdMetadataDistribution] = {
    Json.fromJson[MdMetadataDistribution](json) match {
      case JsSuccess(r: MdMetadataDistribution, path: JsPath) => Some(r)
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
