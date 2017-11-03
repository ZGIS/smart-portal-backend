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

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID
import javax.inject.{Inject, Provider}

import controllers.csw.{CswDeleteRequest, CswInsertRequest, CswTransactionWithIndexUpdate}
import controllers.security.{AuthenticationAction, UserAction}
import models.ErrorResult
import models.gmd.MdMetadata
import models.users.{User, UserMetaRecord}
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.{Action, AnyContent, Controller, Result}
import play.api.{Application, Configuration}
import services.{MetadataService, OwcCollectionsService, UserService}
import utils.{ClassnameLogger, PasswordHashing}

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, Node, NodeSeq}


/**
  * Contoller that serves all CSW related requests
  *
  * @param configuration
  * @param userService
  * @param wsClient
  * @param context
  * @param appProvider
  * @param metadataService
  * @param collectionsService
  * @param authenticationAction
  * @param userAction
  */
class CswController @Inject()(implicit configuration: Configuration,
                              userService: UserService,
                              wsClient: WSClient,
                              implicit val context: ExecutionContext,
                              appProvider: Provider[Application],
                              metadataService: MetadataService,
                              collectionsService: OwcCollectionsService,
                              authenticationAction: AuthenticationAction,
                              userAction: UserAction
                             )
  extends Controller with ClassnameLogger {

  lazy val app = appProvider.get()
  lazy val cswtInsertResource = app.resource("csw/transaction.insert.xml").get
  lazy val cswtUpdateResource = app.resource("csw/transaction.update.xml").get
  lazy val cswtDeleteResource = app.resource("csw/transaction.delete.xml").get

  lazy val cswtInsertXml = scala.xml.XML.load(cswtInsertResource)
  lazy val cswtUpdateXml = scala.xml.XML.load(cswtUpdateResource)
  lazy val cswtDeleteXml = scala.xml.XML.load(cswtDeleteResource)

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

  /**
    * trying to generalise for all transaction types
    *
    * @param transaction
    * @return
    */
  def initiateTransactionRequest(transaction: CswTransactionWithIndexUpdate): Future[(WSResponse, WSResponse)] = {
    //FIXME SR I find that pretty hard to read. Is there a better way of chaining WS calls?
    // see https://www.playframework.com/documentation/2.5.x/ScalaWS#chaining-ws-calls
    for {
      getCapaResponse <- wsClient.url(CSW_OPERATIONS_METADATA_URL).get()
      transactionResponse <- {
        //check if getCapaResponse indicates, that the CSW can perform Transactions
        val operation = (getCapaResponse.xml \\ "OperationsMetadata" \\ "Operation").filter(node => {
          logger.debug(s"Attribute 'name'=${node.attribute("name").get.text.toString}")
          node.attribute("name").get.text.equals("Transaction")
        })
        logger.debug(s"operation = ${operation.toString()}")
        if (operation.isEmpty) {
          throw new UnsupportedOperationException("CSW does not support Transaction.")
        }

        // prepare transaction template
        val finalXML = transaction.transform

        logger.debug(s"finalXml: ${finalXML.toString()}")
        wsClient.url(CSW_URL).post(finalXML.toString())
      }

      updateIngesterIndexResponse <- {
        // if (transactionResponse.status == 200) {
        val response = wsClient.url(INGESTER_UPDATE_INDEX_URL).get()
        response.onSuccess {
          case response if response.status == 200 => {
            logger.info(
              s"Successfully called $INGESTER_UPDATE_INDEX_URL (${response.status} - ${response.statusText})")
            logger.info(s"response: ${response.body}")
          }
          case response => {
            logger.warn(
              s"Call to '$INGESTER_UPDATE_INDEX_URL' returned error: ${response.status} - ${response.statusText}")
            logger.warn(s"response: ${response.body}")
          }
        }
        response

      }
    } yield (transactionResponse, updateIngesterIndexResponse)
  }

  /**
    * check the response of the tansaction
    *
    * @param response
    * @param transaction
    * @return
    */
  def evaluateTransactionResponse(response: WSResponse, transaction: CswTransactionWithIndexUpdate): Either[ErrorResult, UUID] = {
    logger.debug(response.xml.toString())
    response.xml match {
      case e: Elem if e.label == "TransactionResponse" =>

        if (transaction.responsePositive(e)) {
          Right(UUID.fromString(transaction.fileIdentifier))
        } else {
          Left(ErrorResult("Identifier not clear in TransactionResponse", None))
        }

      case e: Elem if e.label == "ExceptionReport" =>
        val message = (e \\ "Exception" \\ "ExceptionText").text
        val error = ErrorResult(message, None)
        Left(error)

      case _ =>
        val error = ErrorResult(s"Unexpected Response from CSW: ${response.status} - ${response.statusText}",
          Some(response.body))
        Left(error)

    }
  }

  /**
    * processes CSW:Transaction -> Insert
    *
    * @return
    */
  def insertMetadataRecord: Action[JsValue] = (authenticationAction andThen userAction).async(parse.json) {
    request =>

      logger.debug(request.toString)
      (request.body.as[JsObject] \ "metadata").validate[MdMetadata].fold(
        errors => {
          logger.error(JsError.toJson(errors).toString())
          val error = ErrorResult("Could not validate metadata from your request.", Some(JsError.toJson(errors).toString()))
          Future {
            BadRequest(Json.toJson(error)).as(JSON)
          }
        },
        mdMetadata => {
          logger.trace(Json.prettyPrint(mdMetadata.toJson()))
          logger.debug(s"MD_Metadata XML: ${mdMetadata.toXml()}")

          val insertTransaction = CswInsertRequest(cswtInsertXml, mdMetadata)
          val futureResponse: Future[(WSResponse, WSResponse)] = initiateTransactionRequest(insertTransaction)

          futureResponse.recover {
            case e: Exception =>
              logger.error("Insert CSW threw exception", e)
              val error = ErrorResult(s"Exception (${e.getClass.getCanonicalName}) on CSW insert: ${e.getMessage}",
                None)
              InternalServerError(Json.toJson(error)).as(JSON)
          }

          futureResponse.map { response =>
            logger.debug("Ingester Update: " + response._2.body)
            val responseEval = evaluateTransactionResponse(response._1, insertTransaction)
            responseEval.fold[Result](
              error => {
                logger.warn(error.message)
                InternalServerError(Json.toJson(error)).as(JSON)
              },
              uuid => {
                logger.debug(s"Adding ${mdMetadata.fileIdentifier} to default collection of ${request.user.email}.")
                val userMetaEntryOk = userService.insertUserMetaRecordEntry(CSW_URL, mdMetadata.fileIdentifier, request.user.email)
                userMetaEntryOk.fold {
                  val error = ErrorResult(s"Could not add ${mdMetadata.fileIdentifier} to your collection of ${request.user.email}.", None)
                  logger.warn(error.message)
                  BadRequest(Json.toJson(error)).as(JSON)
                } {
                  userMetaEntry =>
                    val owcresource = collectionsService.generateMdResource(CSW_URL, mdMetadata, userMetaEntry)
                    val added = collectionsService.addMdResourceToUserDefaultCollection(CSW_URL, owcresource, userMetaEntry)
                    if (added) {
                      Ok(Json.obj("type" -> "success", "fileIdentifier" -> uuid.toString,
                        "message" -> s"Inserted as ${uuid.toString} and reference entry added to your data collection."))
                    } else {
                      val error = ErrorResult(s"Could not add ${mdMetadata.fileIdentifier} to your collection of ${request.user.email}.", None)
                      logger.warn(error.message)
                      BadRequest(Json.toJson(error)).as(JSON)
                    }
                }
              })
          }
        }
      )
  }

  /**
    * calls CSW:Transaction -> Update
    *
    * @return
    */
  def updateMetadataRecord: Action[JsValue] = (authenticationAction andThen userAction).async(parse.json) {
    request =>
      logger.debug(request.toString)
      (request.body.as[JsObject] \ "metadata").validate[MdMetadata].fold(
        errors => {
          logger.error(JsError.toJson(errors).toString())
          val error = ErrorResult("Could not validate metadata from your request.", Some(JsError.toJson(errors).toString()))
          Future {
            BadRequest(Json.toJson(error)).as(JSON)
          }
        },
        mdMetadata => {
          logger.trace(Json.prettyPrint(mdMetadata.toJson()))
          logger.debug(s"MD_Metadata XML: ${mdMetadata.toXml()}")
          val foundMeta = userService.findUserMetaRecordByAccountSubject(request.user).exists(f => f.uuid.equals(UUID.fromString(mdMetadata.fileIdentifier)))
          val notFoundBlock = {
            logger.error("metadata ref not found")
            val error = ErrorResult("metadata ref not found.", None)
            BadRequest(Json.toJson(error)).as(JSON)
          }
          if (!foundMeta) {
            Future(notFoundBlock)
          } else {
            // create transaction update request
            val updateTransaction = CswInsertRequest(cswtUpdateXml, mdMetadata)
            // sent this transaction update request
            // if successful sent catalogue update link
            val futureResponse: Future[(WSResponse, WSResponse)] = initiateTransactionRequest(updateTransaction)

            futureResponse.recover {
              case e: Exception =>
                logger.error("Insert CSW threw exception", e)
                val error = ErrorResult(s"Exception (${e.getClass.getCanonicalName}) on CSW insert: ${e.getMessage}",
                  None)
                InternalServerError(Json.toJson(error)).as(JSON)
            }

            futureResponse.map { response =>
              // TODO all the stuff
              logger.debug("Ingester Update: " + response._2.body)
              val responseEval = evaluateTransactionResponse(response._1, updateTransaction)
              responseEval.fold[Result](
                error => {
                  logger.warn(error.message)
                  InternalServerError(Json.toJson(error)).as(JSON)
                },
                uuid => {
                  logger.debug(s"Updating ${mdMetadata.fileIdentifier} in default collection of ${request.user.email}.")
                  val userMetaEntryUpdateOk = userService.findUserMetaRecordByUuid(uuid)
                    .flatMap { mt =>
                      val newMeta = mt.copy(laststatustoken = "UPDATE",
                        laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))
                      userService.updateUserMetaRecord(newMeta)
                    }

                  userMetaEntryUpdateOk.fold {
                    val error = ErrorResult(s"Could not update ${mdMetadata.fileIdentifier} in your collection of ${request.user.email}.", None)
                    logger.warn(error.message)
                    BadRequest(Json.toJson(error)).as(JSON)
                  } {
                    userMetaEntry =>
                      val owcresource = collectionsService.generateMdResource(CSW_URL, mdMetadata, userMetaEntry)
                      val added = collectionsService.updateMdResourceInUserDefaultCollection(CSW_URL, owcresource, userMetaEntry)
                      if (added) {
                        Ok(Json.obj("type" -> "success", "fileIdentifier" -> uuid.toString,
                          "message" -> s"Updated ${uuid.toString} and reference entry in your data collection."))
                      } else {
                        val error = ErrorResult(s"Could not update ${mdMetadata.fileIdentifier} in your collection of ${request.user.email}.", None)
                        logger.warn(error.message)
                        BadRequest(Json.toJson(error)).as(JSON)
                      }
                  }
                })
            }
          }
        }
      )
  }

  /**
    * calls CSW:Transaction -> Delete
    *
    * @return
    */
  def deleteMetadataRecord(uuid: String): Action[JsValue] = (authenticationAction andThen userAction).async(parse.json) {
    request =>
      val foundMeta = userService.findUserMetaRecordByAccountSubject(request.user).exists(f => f.uuid.equals(UUID.fromString(uuid)))
      val notFoundBlock = {
        logger.error("metadata ref not found")
        val error = ErrorResult("metadata ref not found.", None)
        BadRequest(Json.toJson(error)).as(JSON)
      }
      if (!foundMeta) {
        Future(notFoundBlock)
      } else {
        // create transaction delete request
        val deleteTransaction = CswDeleteRequest(cswtDeleteXml, UUID.fromString(uuid))
        // sent this transaction delete request
        val futureResponse: Future[(WSResponse, WSResponse)] = initiateTransactionRequest(deleteTransaction)

        futureResponse.recover {
          case e: Exception =>
            logger.error("Insert CSW threw exception", e)
            val error = ErrorResult(s"Exception (${e.getClass.getCanonicalName}) on CSW insert: ${e.getMessage}",
              None)
            InternalServerError(Json.toJson(error)).as(JSON)
        }

        // TODO all the stuff
        futureResponse.map { response =>
          val responseEval = evaluateTransactionResponse(response._1, deleteTransaction)
          responseEval.fold[Result](
            error => {
              logger.warn(error.message)
              InternalServerError(Json.toJson(error)).as(JSON)
            },
            ok => {
              NotImplemented(Json.obj("status" -> "OK", "fileIdentifier" -> uuid.toString))
            })
          // if successful sent catalogue update link
          // if both return nicely send acknowledgement as result
          // else sent error message and hope for the best

        }
      }
  }

}
