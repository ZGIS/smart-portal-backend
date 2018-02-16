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

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZoneOffset, ZonedDateTime}
import java.util.UUID

import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json._
import utils.ClassnameLogger

import scala.xml.{Elem, Node}
import scala.xml.transform.{RewriteRule, RuleTransformer}

/**
  * inserts the CIResponsibleParty into the MDMetadata XML (where needed)
  *
  * @param xml
  */
private class AddCIResponsiblePartyToMDMetadata(xml: Node) extends RewriteRule {
  override def transform(n: Node): Seq[Node] = n match {
    case e: Elem if (e.label == "contact" && e.prefix == "gmd")
      || (e.label == "pointOfContact" && e.prefix == "gmd") => {
      new Elem(e.prefix, e.label, e.attributes, e.scope, e.minimizeEmpty, (xml).toSeq: _*)
    }
    case x => x
  }
}

/**
  * inserts the distributionInfo into the MDMetadata XML (where needed)
  *
  * @param xml
  */
private class AddMDDistributionToMDMetadata(xml: Node) extends RewriteRule {
  override def transform(n: Node): Seq[Node] = n match {
    case e: Elem if (e.label == "distributionInfo" && e.prefix == "gmd") => {
      new Elem(e.prefix, e.label, e.attributes, e.scope, e.minimizeEmpty, (xml).toSeq: _*)
    }
    case x => x
  }
}

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

trait JsonableCompanion[A] extends ClassnameLogger {
  val reads: Reads[A]
  val writes: Writes[A]
  val format: Format[A] = Format(reads, writes)

  /**
    * parseOm2Measurements object from Json
    *
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
  * @param smartCategory
  * @param topicCategoryCode
  * @param hierarchyLevelName
  * @param scale
  * @param extent
  * @param citation
  * @param lineageStatement
  * @param responsibleParty
  * @param distribution
  */
final case class MdMetadata(fileIdentifier: String,
                      title: String,
                      abstrakt: String,
                      keywords: List[String],
                      smartCategory: List[String],
                      topicCategoryCode: String,
                      hierarchyLevelName: String,
                      scale: String,
                      extent: MdMetadataExtent,
                      citation: MdMetadataCitation,
                      lineageStatement: String,
                      responsibleParty: MdMetadataResponsibleParty,
                      distribution: MdMetadataDistribution
                     ) extends Jsonable with Xmlable with ClassnameLogger {

  def toXml(): Node = {
    val mdMetadataTemplate =
      <gmd:MD_Metadata xmlns:gmd="http://www.isotc211.org/2005/gmd" xmlns:gco="http://www.isotc211.org/2005/gco"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.isotc211.org/2005/gco http://www.isotc211.org/2005/gco/gco.xsd
                                     http://www.isotc211.org/2005/gmd http://www.isotc211.org/2005/gmd/gmd.xsd">
        <gmd:fileIdentifier><gco:CharacterString>{this.fileIdentifier}</gco:CharacterString></gmd:fileIdentifier>
        <gmd:language><gco:CharacterString>eng</gco:CharacterString></gmd:language>
        <gmd:characterSet><gmd:MD_CharacterSetCode codeListValue="utf8" codeList="http://asdd.ga.gov.au/asdd/profileinfo/gmxCodelists.xml#MD_CharacterSetCode">utf8</gmd:MD_CharacterSetCode></gmd:characterSet>
        <gmd:hierarchyLevel><gmd:MD_ScopeCode codeListValue={this.hierarchyLevelName} codeList="http://asdd.ga.gov.au/asdd/profileinfo/GAScopeCodeList.xml#MD_ScopeCode">{this.hierarchyLevelName}</gmd:MD_ScopeCode></gmd:hierarchyLevel>
        <gmd:hierarchyLevelName><gco:CharacterString>{this.hierarchyLevelName}</gco:CharacterString></gmd:hierarchyLevelName>

        <gmd:contact><!-- FILLED WITH TRANSFORMATOR - SAME AS gmd:pointOfContact --></gmd:contact>
        <gmd:dateStamp><gco:DateTime>{ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssVV"))}</gco:DateTime></gmd:dateStamp>
        <gmd:metadataStandardName><gco:CharacterString>ANZLIC Metadata Profile: An Australian/New Zealand Profile of AS/NZS ISO 19115:2005, Geographic information - Metadata</gco:CharacterString></gmd:metadataStandardName>
        <gmd:metadataStandardVersion><gco:CharacterString>1.1</gco:CharacterString></gmd:metadataStandardVersion>
        <gmd:identificationInfo>
          <gmd:MD_DataIdentification>
            <gmd:citation><gmd:CI_Citation>
                <gmd:title><gco:CharacterString>{this.title}</gco:CharacterString></gmd:title>
                <gmd:date><gmd:CI_Date>
                    <gmd:date><gco:Date>{ZonedDateTime.of(citation.ciDate.atStartOfDay(),ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}</gco:Date></gmd:date>
                    <gmd:dateType><gmd:CI_DateTypeCode codeListValue={this.citation.ciDateType}
                                             codeList="http://asdd.ga.gov.au/asdd/profileinfo/gmxCodelists.xml#CI_DateTypeCode">
                      {this.citation.ciDateType}
                    </gmd:CI_DateTypeCode></gmd:dateType>
                </gmd:CI_Date></gmd:date>
            </gmd:CI_Citation></gmd:citation>
            <gmd:abstract><gco:CharacterString>{this.abstrakt}</gco:CharacterString></gmd:abstract>
            <!-- gmd:purpose??? -->
            <!-- gmd:status??? -->
            <gmd:pointOfContact><!-- FILLED WITH TRANSFORMATOR - SAME AS gmd:contact --></gmd:pointOfContact>
            <!-- gmd:resourceMaintainance??? -->
            <!-- gmd:resourceFormat??? -->
            <gmd:descriptiveKeywords>
              <gmd:MD_Keywords>
                <!-- TODO FILL THIS WITH TRANSFORMATOR -->
                {this.keywords.map(k=> <gmd:keyword><gco:CharacterString>{k}</gco:CharacterString></gmd:keyword>)}
                <gmd:type>
                  <!-- TODO WHAT TYPE HERE? -->
                  <gmd:MD_KeywordTypeCode codeListValue="theme"
                                          codeList="http://asdd.ga.gov.au/asdd/profileinfo/gmxCodelists.xml#MD_KeywordTypeCode">
                    theme
                  </gmd:MD_KeywordTypeCode>
                </gmd:type>
              </gmd:MD_Keywords>
            </gmd:descriptiveKeywords>
            <gmd:descriptiveKeywords>
              <gmd:MD_Keywords>
                <!-- TODO FILL THIS WITH TRANSFORMATOR -->
                {this.smartCategory.map(k=> <gmd:keyword><gco:CharacterString>{k}</gco:CharacterString></gmd:keyword>)}
                <gmd:type>
                  <gmd:MD_KeywordTypeCode codeListValue="SMART"
                                          codeList="http://smart">
                    SMART
                  </gmd:MD_KeywordTypeCode>
                </gmd:type>
              </gmd:MD_Keywords>
            </gmd:descriptiveKeywords>
            <gmd:resourceConstraints>
              <gmd:MD_SecurityConstraints><gmd:classification>
                  <gmd:MD_ClassificationCode codeListValue="unclassified"
                                             codeList="http://asdd.ga.gov.au/asdd/profileinfo/gmxCodelists.xml#MD_ClassificationCode">
                    unclassified
                  </gmd:MD_ClassificationCode>
              </gmd:classification></gmd:MD_SecurityConstraints>
            </gmd:resourceConstraints>
            <gmd:resourceConstraints>
              <gmd:MD_LegalConstraints>
                <gmd:useLimitation><gco:CharacterString><!-- TODO WHICH VALUE HERE?! --></gco:CharacterString></gmd:useLimitation>
                <gmd:useConstraints>
                  <gmd:MD_RestrictionCode codeListValue="copyright"
                                          codeList="http://asdd.ga.gov.au/asdd/profileinfo/gmxCodelists.xml#MD_RestrictionCode">
                    copyright
                  </gmd:MD_RestrictionCode>
                </gmd:useConstraints>
              </gmd:MD_LegalConstraints>
            </gmd:resourceConstraints>
            <gmd:resourceConstraints>
              <gmd:MD_LegalConstraints>
                <gmd:useLimitation><gco:CharacterString>{this.distribution.useLimitation}</gco:CharacterString></gmd:useLimitation>
                <gmd:useConstraints>
                  <gmd:MD_RestrictionCode codeListValue="license"
                                          codeList="http://asdd.ga.gov.au/asdd/profileinfo/gmxCodelists.xml#MD_RestrictionCode">
                    license
                  </gmd:MD_RestrictionCode>
                </gmd:useConstraints>
              </gmd:MD_LegalConstraints>
            </gmd:resourceConstraints>
            <!-- gmd:spatialRepresentationType ??? -->
            <gmd:spatialResolution>
              <gmd:MD_Resolution>
                <gmd:equivalentScale>
                  <gmd:MD_RepresentativeFraction><gmd:denominator><gco:Integer>{this.scale}</gco:Integer></gmd:denominator></gmd:MD_RepresentativeFraction>
                </gmd:equivalentScale>
              </gmd:MD_Resolution>
            </gmd:spatialResolution>
            <gmd:language>
              <gco:CharacterString>eng</gco:CharacterString>
            </gmd:language>
            <gmd:characterSet>
              <gmd:MD_CharacterSetCode codeListValue="utf8"
                                       codeList="http://asdd.ga.gov.au/asdd/profileinfo/gmxCodelists.xml#MD_CharacterSetCode">
                utf8
              </gmd:MD_CharacterSetCode>
            </gmd:characterSet>
            <gmd:topicCategory><gmd:MD_TopicCategoryCode>{this.topicCategoryCode}</gmd:MD_TopicCategoryCode></gmd:topicCategory>
            <gmd:extent>
              <gmd:EX_Extent>
                <gmd:geographicElement>
                  <gmd:EX_GeographicBoundingBox>
                    <gmd:westBoundLongitude><gco:Decimal>{this.extent.mapExtentCoordinates(0)}</gco:Decimal></gmd:westBoundLongitude>
                    <gmd:eastBoundLongitude><gco:Decimal>{this.extent.mapExtentCoordinates(2)}</gco:Decimal></gmd:eastBoundLongitude>
                    <gmd:southBoundLatitude><gco:Decimal>{this.extent.mapExtentCoordinates(1)}</gco:Decimal></gmd:southBoundLatitude>
                    <gmd:northBoundLatitude><gco:Decimal>{this.extent.mapExtentCoordinates(3)}</gco:Decimal></gmd:northBoundLatitude>
                  </gmd:EX_GeographicBoundingBox>
                </gmd:geographicElement>
              </gmd:EX_Extent>
            </gmd:extent>
          </gmd:MD_DataIdentification>
        </gmd:identificationInfo>
        <gmd:distributionInfo><!-- THIS IS FILLED BY TRANSFORMATOR FROM MdMetadataDistribution --></gmd:distributionInfo>
        <gmd:dataQualityInfo>
          <gmd:DQ_DataQuality>
            <gmd:scope>
              <gmd:DQ_Scope>
                <gmd:level><gmd:MD_ScopeCode codeListValue={this.hierarchyLevelName} codeList="http://asdd.ga.gov.au/asdd/profileinfo/gmxCodelists.xml#MD_ScopeCode">{this.hierarchyLevelName}</gmd:MD_ScopeCode></gmd:level>
                <gmd:levelDescription><gmd:MD_ScopeDescription><gmd:other><gco:CharacterString>{this.hierarchyLevelName}</gco:CharacterString></gmd:other></gmd:MD_ScopeDescription></gmd:levelDescription>
              </gmd:DQ_Scope>
            </gmd:scope>
            <gmd:lineage><gmd:LI_Lineage><gmd:statement><gco:CharacterString>{this.lineageStatement}</gco:CharacterString></gmd:statement></gmd:LI_Lineage></gmd:lineage>
          </gmd:DQ_DataQuality>
        </gmd:dataQualityInfo>
      </gmd:MD_Metadata>

    val rule = new RuleTransformer(new AddCIResponsiblePartyToMDMetadata(this.responsibleParty.toXml()),
      new AddMDDistributionToMDMetadata(this.distribution.toXml()))
    val finalXml = rule.transform(mdMetadataTemplate)
    finalXml.asInstanceOf[Node]
  }

  def toJson(): JsValue = {
    Json.toJson(this)
  }
}

object MdMetadata extends ClassnameLogger with JsonableCompanion[MdMetadata] {

  override implicit val reads: Reads[MdMetadata] = (
    (JsPath \ "fileIdentifier").read[String](Reads.filterNot[String](ValidationError("String empty"))(_.trim().isEmpty))
      or Reads.pure(UUID.randomUUID().toString)
      and
      (JsPath \ "title").read[String] and
      (JsPath \ "abstrakt").read[String] and
      (JsPath \ "keywords").read[List[String]] and
      (JsPath \ "smartCategory").read[List[String]] and
      (JsPath \ "topicCategoryCode").read[String] and
      (JsPath \ "hierarchyLevelName").read[String] and
      (JsPath \ "scale").read[String] and
      (JsPath \ "extent").read[MdMetadataExtent] and
      (JsPath \ "citation").read[MdMetadataCitation] and
      (JsPath \ "lineageStatement").read[String] and
      (JsPath \ "responsibleParty").read[MdMetadataResponsibleParty] and
      (JsPath \ "distribution").read[MdMetadataDistribution]
    ) (MdMetadata.apply _)

  override implicit val writes: Writes[MdMetadata] = (
    (JsPath \ "fileIdentifier").write[String] and
      (JsPath \ "title").write[String] and
      (JsPath \ "abstrakt").write[String] and
      (JsPath \ "keywords").write[List[String]] and
      (JsPath \ "smartCategory").write[List[String]] and
      (JsPath \ "topicCategoryCode").write[String] and
      (JsPath \ "hierarchyLevelName").write[String] and
      (JsPath \ "scale").write[String] and
      (JsPath \ "extent").write[MdMetadataExtent] and
      (JsPath \ "citation").write[MdMetadataCitation] and
      (JsPath \ "lineageStatement").write[String] and
      (JsPath \ "responsibleParty").write[MdMetadataResponsibleParty] and
      (JsPath \ "distribution").write[MdMetadataDistribution]
    ) (unlift(MdMetadata.unapply))

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
