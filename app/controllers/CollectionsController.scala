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
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.{JsArray, JsError, JsValue, Json}
import play.api.mvc.{Action, Controller}
import services.{EmailService, OwcCollectionsService}
import utils.{ClassnameLogger, PasswordHashing}

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
        Ok(Json.obj("status" -> "OK", "count" -> owcJsDocs.size, "collections" -> JsArray(owcJsDocs)))
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
          // TODO SR error and OK makes no sense!
          Ok(owcJsDocs.getOrElse(Json.obj("status" -> "ERR", "message" -> "Not found :-(")))
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
              BadRequest(Json.obj("status" -> "ERR", "message" -> JsError.toJson(errors)))
            },
            owcDocument => {
              val inserted = collectionsService.insertCollection(owcDocument, authUser)
              inserted.fold {
                BadRequest(Json.obj("status" -> "ERR", "message" -> "database insert failed"))
              } {
                theDoc => {
                  Ok(Json.obj("status" -> "OK", "message" -> "owcDocument inserted", "document" -> theDoc.toJson))
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
              NotImplemented(Json.obj("status" -> "ERR", "message" -> JsError.toJson(errors)))
            },
            owcDocument => {
              val updated = collectionsService.updateCollectionMetadata(owcDocument, authUser)
              updated.fold {
                NotImplemented(Json.obj("status" -> "ERR", "message" -> "database updated failed"))
              } {
                theDoc => {
                  NotImplemented(
                    Json.obj("status" -> "OK", "message" -> "owcDocument metadata updated, but not yet implemented",
                      "document" -> theDoc.toJson))
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
              BadRequest(Json.obj("status" -> "ERR", "message" -> JsError.toJson(errors)))
            },
            owcEntry => {
              val updatedCollection = collectionsService.addEntryToCollection(collectionid, owcEntry, authUser)
              updatedCollection.fold {
                BadRequest(Json.obj("status" -> "ERR", "message" -> "database update for entry failed"))
              } {
                theDoc => {
                  Ok(Json.obj("status" -> "OK", "message" -> "owcEntry added to owcDocument",
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
              BadRequest(Json.obj("status" -> "ERR", "message" -> JsError.toJson(errors)))
            },
            owcEntry => {
              val updatedCollection = collectionsService.replaceEntryInCollection(collectionid, owcEntry, authUser)
              updatedCollection.fold {
                BadRequest(Json.obj("status" -> "ERR", "message" -> "database update for entry failed"))
              } {
                theDoc => {
                  val entryTest = theDoc.features.find(_.id.equalsIgnoreCase(owcEntry.id)).exists(
                    tEntry => tEntry.equals(owcEntry))
                  if (entryTest) {
                    Ok(Json.obj("status" -> "OK", "message" -> "owcEntry replaced in owcDocument",
                      "document" -> theDoc.toJson, "entry" -> owcEntry.toJson))
                  }
                  else {
                    BadRequest(Json.obj("status" -> "ERR", "message" -> "owcEntry could not be replaced in owcDocument",
                      "document" -> theDoc.toJson, "entry" -> owcEntry.toJson))
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
            BadRequest(Json.obj("status" -> "ERR", "message" -> "database update for entry failed"))
          } {
            theDoc => {
              val entryTest = theDoc.features.find(_.id.equalsIgnoreCase(entryid))
              if (entryTest.isDefined) {
                Ok(Json.obj("status" -> "OK", "message" -> "owcEntry removed from owcDocument",
                  "document" -> theDoc.toJson))
              }
              else {
                BadRequest(Json.obj("status" -> "ERR", "message" -> "owcEntry could not be removed from owcDocument",
                  "document" -> theDoc.toJson))
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
            Ok(Json.obj("status" -> "OK", "message" -> "owcDocument deleted", "document" -> id))
          }
          else {
            BadRequest(Json.obj("status" -> "ERR", "message" -> "deletion of OwcDocument failed", "document" -> id))
          }
  }
}

