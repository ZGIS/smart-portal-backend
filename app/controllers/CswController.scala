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

package controllers

import java.util
import javax.inject.{Inject, Provider}

import play.api.cache.CacheApi
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.{Action, AnyContent, Controller}
import play.api.{Application, Configuration}
import services.MetadataService
import utils.{ClassnameLogger, PasswordHashing}

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, Node}


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

/**
  *
  */
class CswController @Inject()(val configuration: Configuration,
                              val cache: CacheApi,
                              val passwordHashing: PasswordHashing,
                              wsClient: WSClient,
                              implicit val context: ExecutionContext,
                              appProvider: Provider[Application],
                              metadataService: MetadataService
                             )
  extends Controller with ClassnameLogger with Security {


  lazy val app = appProvider.get()
  lazy val cswtInsertResource = app.resource("csw/transaction.insert.xml").get
  lazy val cswtInsertXml = scala.xml.XML.load(cswtInsertResource)
  lazy val mdMetadata = scala.xml.XML.load(app.resource("csw/MD_Metadata.test.xml").get)

  val CSW_URL: String = configuration.getString("smart.csw.url").getOrElse("http://localhost:8000")
  val CSW_OPERATIONS_METADATA_URL: String = s"${CSW_URL}/?service=CSW&version=2.0.2&request=GetCapabilities&sections=OperationsMetadata"

  logger.error("controller starting")


  /**
    * returns valid values for different topics used in metadata editor of webgui
    *
    * @param topic
    * @return
    */
  def getValidValuesFor(topic: String): Action[AnyContent] = Action { request =>
    logger.debug(s"returning valid values for '${topic}'")
    val validValuesOption = metadataService.getValidValuesFor(topic)
    if (validValuesOption.isEmpty) {
      //TODO SR should we have a generic error return object that we return as JSON?
      BadRequest(s"There are not valid values for '${topic}'")
    }
    else {
      Ok(Json.toJson(validValuesOption.get))
    }
  }

  /** calls CSW:Transaction -> Insert
    * FIXME should implement HasToken(parse.json) instead of Action.async, to only allow authenticated users
    */
  def insert: Action[AnyContent] = Action.async { request =>
    logger.debug(request.body.asJson.toString)

    //TODO parse JSON to MDMetadataSet and convert that to XML

    val futureResponse: Future[WSResponse] = for {
      getCapaResponse <- wsClient.url(CSW_OPERATIONS_METADATA_URL).get()
      insertResponse <- {
        //check if getCapaResponse indicates, that the CSW can perform Transactions
        val operation = (getCapaResponse.xml \\ "OperationsMetadata" \\ "Operation").filter(node => {
          logger.debug(s"Attribute 'name'=${node.attribute("name").get.text.toString}")
          node.attribute("name").get.text.equals("Transaction")
        })
        logger.debug(s"operation = ${operation.toString()}")
        if (operation.isEmpty) {
          throw new UnsupportedOperationException("CSW does not support Transaction.")
        }

        //insert MDMEtadata in insert template
        val rule = new RuleTransformer(new AddMDMetadataToInsert(mdMetadata))
        val finalXML = rule.transform(cswtInsertXml)
        logger.debug(s"finalXml: ${finalXML.toString()}")
        wsClient.url(CSW_URL).post(finalXML.toString())
      }
    } yield insertResponse

    futureResponse.recover {
      case e: Exception =>
        logger.error("Insert CSW threw exception", e)
        InternalServerError(e.getMessage)
    }

    futureResponse.map(response => {
      logger.error(response.xml.toString())
      response.xml match {
        case e: Elem if e.label == "TransactionResponse" => {
          val fileIdentifier = (e \\ "InsertResult" \\ "BriefRecord" \\ "identifier").text
          Ok(Json.obj("type" -> "success", "fileIdentifier" -> fileIdentifier,
            "message" -> s"Inserted as ${fileIdentifier}."))
        }
        case e: Elem if e.label == "ExceptionReport" => {
          val message = (e \\ "Exception" \\ "ExceptionText").text
          //TODO SR make this InternalServerError
          Ok(Json.obj("type" -> "danger", "message" -> message))
        }
        case _ => InternalServerError(s"Unexpected Response: ${response}")
      }
    })
  }
}
