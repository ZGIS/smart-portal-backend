/*
 * Copyright (C) 2011-2017 Interfaculty Department of Geoinformatics, University of
 * Salzburg (Z_GIS) & Institute of Geological and Nuclear Sciences Limited (GNS Science)
 * in the SMART Aquifer Characterisation (SAC) programme funded by the New Zealand
 * Ministry of Business, Innovation and Employment (MBIE)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import javax.inject._

import models.owc.{OwcDocument, OwcEntry}
import models.ErrorResult
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.{JsArray, JsError, JsValue, Json}
import play.api.mvc.{Action, Controller}
import services.{EmailService, OwcCollectionsService}
import utils.{ClassnameLogger, PasswordHashing}
import org.apache.commons.lang3.StringEscapeUtils

@Singleton
class CollectionsController @Inject()(config: Configuration,
                                      cacheApi: CacheApi,
                                      emailService: EmailService,
                                      collectionsService: OwcCollectionsService,
                                      override val passwordHashing: PasswordHashing)
  extends Controller with Security with ClassnameLogger {

  val cache: play.api.cache.CacheApi = cacheApi
  val configuration: play.api.Configuration = config
  lazy private val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")

  /**
    * Gets all public collections + collections of a user (if logged in) + collection for ID if provided
    *
    * @param id
    * @return
    */
  def getCollections(id: Option[String]): Action[Unit] = HasOptionalToken(parse.empty) {
    authUserOption =>
      implicit request =>
        val owcJsDocs = collectionsService.getOwcDocumentsForUserAndId(authUserOption, id).map(doc => doc.toJson)
        Ok(Json.obj("count" -> owcJsDocs.size, "collections" -> JsArray(owcJsDocs)))
  }

  /**
    *
    * @return
    */
  def getPersonalDefaultCollection: Action[Unit] = HasToken(parse.empty) {
    token =>
      authUser =>
        implicit request => {
          val owcJsDocs = collectionsService.getUserDefaultOwcDocument(authUser).map(doc => doc.toJson)
          owcJsDocs.fold{
            val error: ErrorResult = ErrorResult(s"Could not find user collection for '${authUser}' ", None)
            BadRequest(Json.toJson(error)).as(JSON)
          } {
            owcDocJs =>
              Ok(owcDocJs)
          }
        }
  }

  /**
    * Returns a JSON array containing OwcProperties with all files uploaded by the current user
    *
    * @return
    */
  def getPersonalFilesFromDefaultCollection: Action[Unit] = HasToken(parse.empty) {
    token =>
      authUser =>
        implicit request => {
          val uploadedFilesProperties =
            collectionsService.getOwcPropertiesForOwcAuthorOwnFiles(authUser).map(doc => doc.toJson)
          Ok(JsArray(uploadedFilesProperties))
        }
  }

  /**
    *
    * @return
    */
  def insertCollection: Action[JsValue] = HasToken(parse.json) {
    token =>
      authUser =>
        implicit request =>
          request.body.validate[OwcDocument] fold(
            errors => {
              logger.error(JsError.toJson(errors).toString())
              val error: ErrorResult = ErrorResult(s"Collection could not be inserted", Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
              BadRequest(Json.toJson(error)).as(JSON)
            },
            owcDocument => {
              val inserted = collectionsService.insertCollection(owcDocument, authUser)
              inserted.fold {
                val error: ErrorResult = ErrorResult(s"Collection could not be inserted", Some("Database insert failed."))
                BadRequest(Json.toJson(error)).as(JSON)
              } {
                theDoc => {
                  Ok(Json.obj("message" -> "owcDocument inserted", "document" -> theDoc.toJson))
                }
              }

            }
          )
  }

  /**
    *
    * @return
    */
  def updateCollectionMetadata: Action[JsValue] = HasToken(parse.json) {
    token =>
      authUser =>
        implicit request =>
          request.body.validate[OwcDocument] fold(
            errors => {
              logger.error(JsError.toJson(errors).toString())
              val error: ErrorResult = ErrorResult(s"Collection could not be updated", Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
              NotImplemented(Json.toJson(error)).as(JSON)
            },
            owcDocument => {
              val updated = collectionsService.updateCollectionMetadata(owcDocument, authUser)
              updated.fold {
                val error: ErrorResult = ErrorResult(s"Collection could not be updated", Some("Database insert failed."))
                NotImplemented(Json.toJson(error)).as(JSON)
              } {
                theDoc => {
                  val error: ErrorResult = ErrorResult(s"owcDocument metadata would have been updated, " +
                    s"but method is not yet implemented", Some(theDoc.toJson.toString()))
                  NotImplemented(Json.toJson(error)).as(JSON)
                }
              }

            }
          )
  }

  /**
    *
    * @param collectionid
    * @return
    */
  def addEntryToCollection(collectionid: String): Action[JsValue] = HasToken(parse.json) {
    token =>
      authUser =>
        implicit request =>
          request.body.validate[OwcEntry] fold(
            errors => {
              logger.error(JsError.toJson(errors).toString())
              val error: ErrorResult = ErrorResult(s"Provided OwcEntry not valid", Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
              BadRequest(Json.toJson(error)).as(JSON)
            },
            owcEntry => {
              val updatedCollection = collectionsService.addEntryToCollection(collectionid, owcEntry, authUser)
              updatedCollection.fold {
                val error: ErrorResult = ErrorResult(s"database update for entry failed", None)
                BadRequest(Json.toJson(error)).as(JSON)
              } {
                theDoc => {
                  Ok(Json.obj("message" -> "owcEntry added to owcDocument",
                    "document" -> theDoc.toJson, "entry" -> owcEntry.toJson))
                }
              }
            }
          )
  }

  /**
    *
    * @param collectionid
    * @return
    */
  def replaceEntryInCollection(collectionid: String): Action[JsValue] = HasToken(parse.json) {
    token =>
      authUser =>
        implicit request =>
          request.body.validate[OwcEntry] fold(
            errors => {
              logger.error(JsError.toJson(errors).toString())
              val error: ErrorResult = ErrorResult(s"replace entry failed", Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
              BadRequest(Json.toJson(error)).as(JSON)
            },
            owcEntry => {
              val updatedCollection = collectionsService.replaceEntryInCollection(collectionid, owcEntry, authUser)
              updatedCollection.fold {
                val error: ErrorResult = ErrorResult(s"replace database update for entry failed", None)
                BadRequest(Json.toJson(error)).as(JSON)
              } {
                theDoc => {
                  val entryTest = theDoc.features.find(_.id.equalsIgnoreCase(owcEntry.id)).exists(
                    tEntry => tEntry.equals(owcEntry))
                  if (entryTest) {
                    Ok(Json.obj("message" -> "owcEntry replaced in owcDocument",
                      "document" -> theDoc.toJson, "entry" -> owcEntry.toJson))
                  }
                  else {
                    val error: ErrorResult = ErrorResult(s"owcEntry could not be replaced in owcDocument", None)
                    BadRequest(Json.toJson(error)).as(JSON)
                  }
                }
              }
            }
          )
  }

  /**
    *
    * @param collectionid
    * @param entryid
    * @return
    */
  def deleteEntryFromCollection(collectionid: String, entryid: String): Action[Unit] = HasToken(parse.empty) {
    token =>
      authUser =>
        implicit request =>
          val updatedCollection = collectionsService.deleteEntryFromCollection(collectionid, entryid, authUser)
          updatedCollection.fold {
            val error: ErrorResult = ErrorResult(s"delete owcEntry database update failed", None)
            BadRequest(Json.toJson(error)).as(JSON)
          } {
            theDoc => {
              val entryTest = theDoc.features.find(_.id.equalsIgnoreCase(entryid))
              if (entryTest.isDefined) {
                Ok(Json.obj("message" -> "owcEntry removed from owcDocument",
                  "document" -> theDoc.toJson))
              }
              else {
                val error: ErrorResult = ErrorResult(s"owcEntry could not be removed from owcDocument", None)
                BadRequest(Json.toJson(error)).as(JSON)
              }
            }
          }
  }

  /**
    *
    * @param id id of the OwcDoc
    * @return
    */
  def deleteCollection(id: String): Action[Unit] = HasToken(parse.empty) {
    token =>
      authUser =>
        implicit request =>
          val deleted = collectionsService.deleteCollection(id, authUser)
          if (deleted) {
            Ok(Json.obj("message" -> "owcDocument deleted", "document" -> id))
          }
          else {
            BadRequest(Json.obj("status" -> "ERR", "message" -> "deletion of OwcDocument failed", "document" -> id))
          }
  }
}

