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

import javax.inject.{Inject, Provider}

import models.ErrorResult
import models.gmd.MdMetadata
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.{Action, AnyContent, Controller}
import play.api.{Application, Configuration}
import services.{MetadataService, OwcCollectionsService, UserService}
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
  * Contoller that serves all CSW related requests
  *
  * @param configuration
  * @param cache
  * @param passwordHashing
  * @param wsClient
  * @param context
  * @param appProvider
  * @param metadataService
  * @param collectionsService
  */
class CswController @Inject()(val configuration: Configuration,
                              val userService: UserService,
                              val passwordHashing: PasswordHashing,
                              wsClient: WSClient,
                              implicit val context: ExecutionContext,
                              appProvider: Provider[Application],
                              metadataService: MetadataService,
                              collectionsService: OwcCollectionsService
                             )
  extends Controller with ClassnameLogger with Security {

  lazy val app = appProvider.get()
  lazy val cswtInsertResource = app.resource("csw/transaction.insert.xml").get
  lazy val cswtInsertXml = scala.xml.XML.load(cswtInsertResource)

  val CSW_URL: String = configuration.getString("smart.csw.url").getOrElse("http://localhost:8000")
  val CSW_OPERATIONS_METADATA_URL: String = s"${CSW_URL}/?service=CSW&version=2.0.2&request=GetCapabilities&sections=OperationsMetadata"

  //TODO SR maybe we should configure these URLs somewhere else? Or even better generate some access lib from the ingester?
  val INGESTER_URL: String = configuration.getString("smart.csw-ingester.url").getOrElse("http://localhost:9001")
  val INGESTER_UPDATE_INDEX_URL: String = s"$INGESTER_URL/cswi-api/v1/buildIndex/smart"

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
      val error = ErrorResult(s"There are no valid values for '${topic}'", None)
      BadRequest(Json.toJson(error))
    }
    else {
      Ok(Json.toJson(validValuesOption.get))
    }
  }

  /** calls CSW:Transaction -> Insert
    */
  def insert: Action[JsValue] = HasTokenAsync(parse.json) {
    token =>
      authUser =>
        implicit request =>

          logger.debug(request.toString)
          //TODO parse JSON to MDMetadataSet and convert that to XML
          // val mdMetadata = MdMetadata.fromJson(((request.body.as[JsObject]) \ "metadata").get)
          (request.body.as[JsObject] \ "metadata").validate[MdMetadata].fold(
            errors => {
              logger.error(JsError.toJson(errors).toString())
              val error = ErrorResult("Could not validate request.", Some(JsError.toJson(errors).toString()))
              Future {
                BadRequest(Json.toJson(error)).as(JSON)
              }
            },
            mdMetadata => {
              logger.trace(Json.prettyPrint(mdMetadata.toJson()))
              //FIXME SR I find that pretty hard to read. Is there a better way of chaining WS calls?
              // see https://www.playframework.com/documentation/2.5.x/ScalaWS#chaining-ws-calls
              val futureResponse: Future[(WSResponse, WSResponse)] = for {
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
                  logger.debug(s"MD_MetadataXML: ${mdMetadata.toXml().toString()}")
                  val rule = new RuleTransformer(new AddMDMetadataToInsert(mdMetadata.toXml()))
                  val finalXML = rule.transform(cswtInsertXml)
                  logger.debug(s"finalXml: ${finalXML.toString()}")
                  wsClient.url(CSW_URL).post(finalXML.toString())
                }
                updateIngesterIndexResponse <- {
                  val response = wsClient.url(INGESTER_UPDATE_INDEX_URL).get()
                  response.onSuccess {
                    case response if response.status == 200 => {
                      logger.info(
                        s"Successfully called $INGESTER_UPDATE_INDEX_URL (${response.status} - ${response.statusText})");
                      logger.info(s"response: ${response.body}");
                    }
                    case response => {
                      logger.warn(
                        s"Call to '$INGESTER_UPDATE_INDEX_URL' returned error: ${response.status} - ${response.statusText}");
                      logger.warn(s"response: ${response.body}");
                    }
                  }
                  response
                }
              } yield (insertResponse, updateIngesterIndexResponse)

              futureResponse.recover {
                case e: Exception =>
                  logger.error("Insert CSW threw exception", e)
                  val error = ErrorResult(s"Exception (${e.getClass.getCanonicalName}) on CSW insert: ${e.getMessage}",
                    None)
                  InternalServerError(Json.toJson(error)).as(JSON)
              }

              futureResponse.map(response => {
                logger.debug(response._1.xml.toString())
                response._1.xml match {
                  case e: Elem if e.label == "TransactionResponse" => {
                    val fileIdentifier = (e \\ "InsertResult" \\ "BriefRecord" \\ "identifier").text
                    logger.debug(s"Adding ${mdMetadata.fileIdentifier} to default collection of $authUser.")
                    val added = collectionsService.addMdResourceToUserDefaultCollection(CSW_URL, mdMetadata, authUser)
                    val userMetaEntryOk = userService.insertUserMetaRecordEntry(CSW_URL, mdMetadata.fileIdentifier, authUser)
                    if (added && userMetaEntryOk.isDefined) {
                      Ok(Json.obj("type" -> "success", "fileIdentifier" -> fileIdentifier,
                        "message" -> s"Inserted as ${fileIdentifier} and reference entry added to your data collection."))
                    } else {
                      val error = ErrorResult(s"Could not add ${mdMetadata.fileIdentifier} to your collection of $authUser.", None)
                      logger.warn(error.message)
                      BadRequest(Json.toJson(error)).as(JSON)
                    }
                  }
                  case e: Elem if e.label == "ExceptionReport" => {
                    val message = (e \\ "Exception" \\ "ExceptionText").text
                    //TODO SR make this InternalServerError
                    val error = ErrorResult(message, None)
                    InternalServerError(Json.toJson(error)).as(JSON)
                  }
                  case _ => {
                    val error = ErrorResult(s"Unexpected Response from CSW: ${response._1.status} - ${response._1.statusText}",
                      Some(response._1.body))
                    InternalServerError(Json.toJson(error)).as(JSON)
                  }
                }
              })
            }
          )

  }
}
