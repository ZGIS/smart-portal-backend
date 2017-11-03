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

package controllers.csw


import java.util.UUID

import models.gmd.MdMetadata
import utils.ClassnameLogger

import scala.xml.{Elem, Node, NodeSeq}
import scala.xml.transform.{RewriteRule, RuleTransformer}

sealed trait CswTransactionWithIndexUpdate {
  def transaction: String
  def fileIdentifier: String
  def requestTemplateXml: Node
  // mdMetadata / UUID
  def transform: NodeSeq
  def responsePositive(e: Elem): Boolean
}

/**
  * inserts the MDMetadata into the TransactionInsert XML
  *
  * @param xml
  */
private class AddMDMetadataToInsert(xml: Node) extends RewriteRule {
  override def transform(n: Node): Seq[Node] = n match {
    case e: Elem if e.label == "Insert" => {
      new Elem(e.prefix, "Insert", e.attributes, e.scope, e.minimizeEmpty, (xml).toSeq: _*)
    }
    case x => x
  }
}

case class CswInsertRequest(requestTemplateXml: Node, mdMetadata: MdMetadata, transaction: String = "INSERT") extends CswTransactionWithIndexUpdate with ClassnameLogger {
  override def fileIdentifier: String = mdMetadata.fileIdentifier

  override def transform: NodeSeq = {
    val rule = new RuleTransformer(new AddMDMetadataToInsert(mdMetadata.toXml()))
    rule.transform(requestTemplateXml)
  }

  override def responsePositive(e: Elem) : Boolean = {
    logger.debug(e.toString())
    val resultingFileIdentifier = (e \\ "InsertResult" \\ "BriefRecord" \\ "identifier").text
    resultingFileIdentifier.equals(fileIdentifier)
  }
}

/**
  * inserts the MDMetadata into the Transaction Update XML
  *
  * @param xml
  */
private class AddMDMetadataToUpdate(xml: Node) extends RewriteRule {
  override def transform(n: Node): Seq[Node] = n match {
    case e: Elem if e.label == "Update" => {
      new Elem(e.prefix, "Update", e.attributes, e.scope, e.minimizeEmpty, (xml).toSeq: _*)
    }
    case x => x
  }
}

case class CswUdateRequest(requestTemplateXml: Node, mdMetadata: MdMetadata, transaction: String = "UPDATE") extends CswTransactionWithIndexUpdate with ClassnameLogger {
  override def fileIdentifier: String = mdMetadata.fileIdentifier

  override def transform: NodeSeq = {
    val rule = new RuleTransformer(new AddMDMetadataToUpdate(mdMetadata.toXml()))
    rule.transform(requestTemplateXml)
  }

  override def responsePositive(e: Elem) : Boolean = {
    logger.debug(e.toString())
    val resultingFileIdentifier = (e \\ "UpdateResult" \\ "BriefRecord" \\ "identifier").text
    resultingFileIdentifier.equals(fileIdentifier)
  }

}

/**
  * horribly wasteful :-)
  *
  * @param requestTemplateXml
  * @param fileIdentifierUuid
  */
case class CswDeleteRequest(requestTemplateXml: Node, fileIdentifierUuid: UUID, transaction: String = "DELETE") extends CswTransactionWithIndexUpdate with ClassnameLogger {
  override def fileIdentifier: String = fileIdentifierUuid.toString

  override def transform: NodeSeq = {
    scala.xml.XML.loadString(requestTemplateXml.toString.replace("FILEIDENTIFIER", fileIdentifierUuid.toString))
  }

  override def responsePositive(e: Elem) : Boolean = {
    logger.debug(e.toString())
    val numDeleted = (e \\ "totalDeleted").text.toInt
    numDeleted >= 1
  }

}

