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

import java.time.format.DateTimeFormatter
import javax.inject._

import info.smart.models.owc100._
import models.ErrorResult
import org.apache.commons.lang3.StringEscapeUtils
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.{JsArray, JsError, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Controller}
import services.{EmailService, OwcCollectionsService}
import utils.{ClassnameLogger, PasswordHashing}

import scala.xml.NodeSeq

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
    * @param owcContextIdOption
    * @return
    */
  def getCollections(owcContextIdOption: Option[String]): Action[Unit] = HasOptionalToken(parse.empty) {
    authUserOption =>
      implicit request =>
        val owcJsDocs = collectionsService.getOwcContextsForUserAndId(authUserOption, owcContextIdOption).map(doc => doc.toJson)
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
          val owcJsDocs = collectionsService.getUserDefaultOwcContext(authUser).map(doc => doc.toJson)
          owcJsDocs.fold {
            val error: ErrorResult = ErrorResult(s"Could not find user collection for '${authUser}' ", None)
            BadRequest(Json.toJson(error)).as(JSON)
          } {
            owcDocJs =>
              Ok(owcDocJs)
          }
        }
  }

  /**
    * Returns a JSON array containing OwcLinks with all files uploaded by the current user
    *
    * @return
    */
  def getPersonalFilesFromDefaultCollection: Action[Unit] = HasToken(parse.empty) {
    token =>
      authUserEmail =>
        implicit request => {
          val owcDataLinks = collectionsService.getOwcLinksForOwcAuthorOwnFiles(authUserEmail).map(owcLink => owcLink.toJson)
          Ok(JsArray(owcDataLinks))
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
          request.body.validate[OwcContext] fold(
            errors => {
              logger.error(JsError.toJson(errors).toString())
              val error: ErrorResult = ErrorResult("Collection could not be inserted",
                Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
              BadRequest(Json.toJson(error)).as(JSON)
            },
            owcContext => {
              val inserted = collectionsService.insertCollection(owcContext, authUser)
              inserted.fold {
                val error: ErrorResult = ErrorResult("Collection could not be inserted",
                  Some("Database insert failed."))
                BadRequest(Json.toJson(error)).as(JSON)
              } {
                theDoc => {
                  Ok(Json.obj("message" -> "owcContext inserted", "document" -> theDoc.toJson))
                }
              }

            }
          )
  }

  /**
    *
    * @return
    */
  def updateCollection: Action[JsValue] = HasToken(parse.json) {
    token =>
      authUser =>
        implicit request =>
          request.body.validate[OwcContext] fold(
            errors => {
              logger.error(JsError.toJson(errors).toString())
              val error: ErrorResult = ErrorResult("Collection could not be updated",
                Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
              BadRequest(Json.toJson(error)).as(JSON)
            },
            owcContext => {
              val updated = collectionsService.updateCollection(owcContext, authUser)
              updated.fold {
                val error: ErrorResult = ErrorResult("Collection could not be updated",
                  Some("Database insert failed."))
                BadRequest(Json.toJson(error)).as(JSON)
              } {
                theDoc => {
                  Ok(Json.obj("message" -> "owcContext updated", "document" -> theDoc.toJson))
                }
              }

            }
          )
  }

  /**
    *
    * @param owcContextId
    * @return
    */
  def addResourceToCollection(owcContextId: String): Action[JsValue] = HasToken(parse.json) {
    token =>
      authUser =>
        implicit request =>
          request.body.validate[OwcResource] fold(
            errors => {
              logger.error(JsError.toJson(errors).toString())
              val error: ErrorResult = ErrorResult("Provided OwcResource not valid",
                Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
              BadRequest(Json.toJson(error)).as(JSON)
            },
            owcResource => {
              val collectionDoc = collectionsService.getOwcContextsForUserAndId(
                authUserOption = Some(authUser), owcContextIdOption = Some(owcContextId)).headOption
              val updatedCollection = collectionDoc.map {
                o =>
                  val updated = o.copy(resource = o.resource ++ Seq(owcResource))
                  collectionsService.updateCollection(updated, authUser)
              }.getOrElse(None)
              updatedCollection.fold {
                val error: ErrorResult = ErrorResult("database update for resource failed", None)
                BadRequest(Json.toJson(error)).as(JSON)
              } {
                theDoc => {
                  Ok(Json.obj("message" -> "owcResource added to owcContext",
                    "document" -> theDoc.toJson, "entry" -> owcResource.toJson))
                }
              }
            }
          )
  }

  /**
    *
    * @param owcContextId
    * @return
    */
  def replaceResourceInCollection(owcContextId: String): Action[JsValue] = HasToken(parse.json) {
    token =>
      authUser =>
        implicit request =>
          request.body.validate[OwcResource] fold(
            errors => {
              logger.error(JsError.toJson(errors).toString())
              val error: ErrorResult = ErrorResult("replace resource failed",
                Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
              BadRequest(Json.toJson(error)).as(JSON)
            },
            owcResource => {
              val collectionDoc = collectionsService.getOwcContextsForUserAndId(
                authUserOption = Some(authUser), owcContextIdOption = Some(owcContextId)).headOption
              val updatedCollection = collectionDoc.map {
                o =>
                  val newResources = o.resource.filterNot(r => r.id.equals(owcResource.id))
                  val updated = o.copy(resource = newResources ++ Seq(owcResource))
                  collectionsService.updateCollection(updated, authUser)
              }.getOrElse(None)
              updatedCollection.fold {
                val error: ErrorResult = ErrorResult("database update for resource failed", None)
                BadRequest(Json.toJson(error)).as(JSON)
              } {
                theDoc => {
                  val resourceTest = theDoc.resource.find(r => r.id.equals(owcResource.id)).exists(
                    tResource => tResource.equals(owcResource))
                  if (resourceTest) {
                    Ok(Json.obj("message" -> "owcResource replaced in owcContext",
                      "document" -> theDoc.toJson, "entry" -> owcResource.toJson))
                  }
                  else {
                    val error: ErrorResult = ErrorResult("owcResource could not be replaced in owcContext", None)
                    BadRequest(Json.toJson(error)).as(JSON)
                  }
                }
              }

            }
          )
  }

  /**
    *
    * @param owcContextId
    * @param owcResourceId
    * @return
    */
  def deleteResourceFromCollection(owcContextId: String, owcResourceId: String): Action[Unit] = HasToken(parse.empty) {
    token =>
      authUser =>
        implicit request =>
          val collectionDoc = collectionsService.getOwcContextsForUserAndId(
            authUserOption = Some(authUser), owcContextIdOption = Some(owcContextId)).headOption
          val updatedCollection = collectionDoc.map {
            o =>
              val newResources = o.resource.filterNot(r => r.id.toString.equals(owcResourceId))
              val updated = o.copy(resource = newResources)
              collectionsService.updateCollection(updated, authUser)
          }.getOrElse(None)
          updatedCollection.fold {
            val error: ErrorResult = ErrorResult("delete owcResource database update failed", None)
            BadRequest(Json.toJson(error)).as(JSON)
          } {
            theDoc => {
              val resourceTest = theDoc.resource.find(r => r.id.toString.equals(owcResourceId))
              if (resourceTest.isDefined) {
                val error: ErrorResult = ErrorResult("owcResource could not be removed from owcContext", None)
                BadRequest(Json.toJson(error)).as(JSON)
              }
              else {
                Ok(Json.obj("message" -> "owcResource removed from owcContext",
                  "document" -> theDoc.toJson))
              }
            }
          }
  }

  /**
    *
    * @param owcContextId id of the OwcDoc
    * @return
    */
  def deleteCollection(owcContextId: String): Action[Unit] = HasToken(parse.empty) {
    token =>
      authUser =>
        implicit request =>
          val collectionDoc = collectionsService.getOwcContextsForUserAndId(
            authUserOption = Some(authUser), owcContextIdOption = Some(owcContextId)).headOption
          val deleted = collectionDoc.exists(o => collectionsService.deleteCollection(o, authUser))
          if (deleted) {
            //TODO SR general "response" JSON?
            Ok(Json.obj("message" -> "owcContext deleted", "document" -> owcContextId))
          }
          else {
            val error = ErrorResult("Deletion of OwcContext failed.", Some(s"DocumentId: ${owcContextId}"))
            BadRequest(Json.toJson(error)).as(JSON)
          }
  }

  /**
    * Creates a sitemap xml for publicly accessible records/collections and provides links via webgui access
    * https://support.google.com/webmasters/answer/75712?visit_id=1-636439946733108040-1790029023&rd=1
    *
    * @return
    */
  def generateSitemapFromPubliccollections: Action[AnyContent] = Action {

    val owcXmlElements: scala.collection.mutable.StringBuilder = new StringBuilder
    owcXmlElements.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    owcXmlElements.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n")

    collectionsService.getOwcContextsForUserAndId(None, None).foreach{
      doc =>
        val docPart = s"""<url>
            |  <loc>https://dev.smart-project.info/#${doc.id.getPath}</loc>
            |  <lastmod>${doc.updateDate.format(DateTimeFormatter.ISO_DATE)}</lastmod>
            |  <priority>1.0</priority>
            |</url>
          """.stripMargin
        owcXmlElements.append(docPart)
        doc.resource.foreach{
          res =>
            val resPart = s"""<url>
                             |  <loc>https://dev.smart-project.info/#${res.id.getPath}</loc>
                             |  <lastmod>${doc.updateDate.format(DateTimeFormatter.ISO_DATE)}</lastmod>
                             |  <priority>1.0</priority>
                             |</url>
          """.stripMargin
            owcXmlElements.append(resPart)
        }
    }
    owcXmlElements.append("</urlset>\n")
    Ok(owcXmlElements.mkString).withHeaders("Content-type" -> "application/xml")
  }
}

