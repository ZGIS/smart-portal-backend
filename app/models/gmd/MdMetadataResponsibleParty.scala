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
trait MdMetadataResponsiblePartyTrait extends ValidValuesReadsAdditions {
  /* empty */
}

/**
  * Created by steffen on 21.12.16.
  */
case class MdMetadataResponsibleParty(individualName: String,
                                      telephone: String,
                                      email: String,
                                      pointOfContact: String,
                                      orgName: String,
                                      orgWebLinkage: String
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
  override def toXml(): Node = <gmd:CI_ResponsibleParty>
    <gmd:individualName><gco:CharacterString>{this.individualName}</gco:CharacterString></gmd:individualName>
    <gmd:organisationName><gco:CharacterString>{this.orgName}</gco:CharacterString></gmd:organisationName>
    <gmd:positionName><gco:CharacterString></gco:CharacterString></gmd:positionName>
    <gmd:contactInfo><gmd:CI_Contact>
      <gmd:phone><gmd:CI_Telephone>
        <gmd:voice><gco:CharacterString>{this.telephone}</gco:CharacterString></gmd:voice>
        <gmd:facsimile><gco:CharacterString></gco:CharacterString></gmd:facsimile>
      </gmd:CI_Telephone></gmd:phone>
      <gmd:address><gmd:CI_Address>
        <gmd:deliveryPoint><gco:CharacterString></gco:CharacterString></gmd:deliveryPoint>
        <gmd:city><gco:CharacterString></gco:CharacterString></gmd:city>
        <gmd:administrativeArea><gco:CharacterString/></gmd:administrativeArea>
        <gmd:postalCode><gco:CharacterString></gco:CharacterString></gmd:postalCode>
        <gmd:country><gco:CharacterString>New Zealand</gco:CharacterString></gmd:country>
        <gmd:electronicMailAddress><gco:CharacterString>{this.email}</gco:CharacterString></gmd:electronicMailAddress>
      </gmd:CI_Address></gmd:address>
    </gmd:CI_Contact>
    </gmd:contactInfo>
    <gmd:role>
      <gmd:CI_RoleCode codeListValue={this.pointOfContact}
                       codeList="http://asdd.ga.gov.au/asdd/profileinfo/gmxCodelists.xml#CI_RoleCode">
        {this.pointOfContact}
      </gmd:CI_RoleCode>
    </gmd:role>
  </gmd:CI_ResponsibleParty>
}

object MdMetadataResponsibleParty extends MdMetadataResponsiblePartyTrait with
  JsonableCompanion[MdMetadataResponsibleParty] {
  /**
    * metadataService will be injected. for this to work you need to add
    * `bind(classOf[MdMetadataResponsiblePartyTrait]).toInstance(MdMetadataResponsibleParty)`
    * to your implementation of `com.google.inject.AbstractModule.configure()`
    */
  @Inject() override var metadataService: MetadataService = null

  override implicit val reads: Reads[MdMetadataResponsibleParty] = (
    (JsPath \ "individualName").read[String] and
      (JsPath \ "telephone").read[String] and
      (JsPath \ "email").read[String](Reads.email) and
      (JsPath \ "pointOfContact").read[String](validValue("pointOfContact")) and
      (JsPath \ "orgName").read[String] and
      (JsPath \ "orgWebLinkage").read[String]
    ) (MdMetadataResponsibleParty.apply _)

  override implicit val writes: Writes[MdMetadataResponsibleParty] = (
    (JsPath \ "individualName").write[String] and
      (JsPath \ "telephone").write[String] and
      (JsPath \ "email").write[String] and
      (JsPath \ "pointOfContact").write[String] and
      (JsPath \ "orgName").write[String] and
      (JsPath \ "orgWebLinkage").write[String]
    ) (unlift(MdMetadataResponsibleParty.unapply))

  /**
    * parse object from Json
    *
    * @param json
    * @return Option if parsing error
    */
  override def fromJson(json: JsValue): Option[MdMetadataResponsibleParty] = {
    Json.fromJson[MdMetadataResponsibleParty](json) match {
      case JsSuccess(r: MdMetadataResponsibleParty, path: JsPath) => Some(r)
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
