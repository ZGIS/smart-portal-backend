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

import java.net.URL
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject._

import controllers.security.{AuthenticationAction, OptionalAuthenticationAction, UserAction}
import info.smart.models.owc100._
import models.ErrorResult
import models.owc.OwcContextDAO
import org.apache.commons.lang3.StringEscapeUtils
import play.api.libs.json.{JsArray, JsError, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Controller}
import services.{EmailService, OwcCollectionsService, UserService}
import utils.ClassnameLogger

@Singleton
class CollectionsController @Inject()(userService: UserService,
                                      emailService: EmailService,
                                      collectionsService: OwcCollectionsService,
                                      authenticationAction: AuthenticationAction,
                                      optionalAuthenticationAction: OptionalAuthenticationAction,
                                      userAction: UserAction)
  extends Controller with ClassnameLogger {

  /**
    * default actions composition
    */
  private val defaultAuthAction = authenticationAction andThen userAction

  /**
    * Gets all public collections + collections of a user (if logged in) + collection for ID if provided
    *
    * @param owcContextIdOption
    * @return
    */
  def getCollections(owcContextIdOption: Option[String]): Action[Unit] = optionalAuthenticationAction(parse.empty) {
    request =>
      val userOption = request.optionalSession.flatMap(session => userService.findUserByEmailAsString(session.email))

      val owcJsDocs = collectionsService.getOwcContextsForUserAndId(userOption, owcContextIdOption)
        .map(doc => doc.toJson)
      Ok(Json.obj("count" -> owcJsDocs.size, "collections" -> JsArray(owcJsDocs)))
  }

  /**
    *
    * @return
    */
  def getPersonalDefaultCollection: Action[Unit] = defaultAuthAction(parse.empty) {
    request =>
      val owcJsDocs = collectionsService.getUserDefaultOwcContext(request.user).map(doc => doc.toJson)
      owcJsDocs.fold {
        val error: ErrorResult = ErrorResult(s"Could not find user collection for '${request.user.email}' ", None)
        BadRequest(Json.toJson(error)).as(JSON)
      } {
        owcDocJs =>
          Ok(owcDocJs)
      }

  }

  def createNewCustomCollection: Action[Unit] = defaultAuthAction(parse.empty) {
    request =>
      val owcJsDocs = collectionsService.createNewCustomCollection(request.user).map(doc => doc.toJson)
      owcJsDocs.fold {
        val error: ErrorResult = ErrorResult(s"Could not find user collection for '${request.user.email}' ", None)
        BadRequest(Json.toJson(error)).as(JSON)
      } {
        owcDocJs =>
          Ok(owcDocJs)
      }

  }

  /**
    * Returns a JSON array containing OwcLinks with all files uploaded by the current user
    *
    * @return
    */
  def getPersonalFilesFromDefaultCollection: Action[Unit] = defaultAuthAction(parse.empty) {
    request =>
      val owcDataLinks = collectionsService.getOwcLinksForOwcAuthorOwnFiles(request.user).map(owcLink => owcLink.toJson)
      Ok(Json.obj("status" -> "OK", "datalinks" -> JsArray(owcDataLinks)))
  }

  /**
    * add collection AS IS, beware may quickly result in unique DB constraint violations and unwanted id URLs
    *
    * @return
    */
  def insertCollection: Action[JsValue] = defaultAuthAction(parse.json) {
    request =>
      request.body.validate[OwcContext] fold(
        errors => {
          logger.error(JsError.toJson(errors).toString())
          val error: ErrorResult = ErrorResult("Collection could not be inserted",
            Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
          BadRequest(Json.toJson(error)).as(JSON)
        },
        owcContext => {
          logger.trace(Json.prettyPrint(owcContext.toJson))
          // TODO mangle Context and Resource IDs
          val inserted = collectionsService.insertCollection(owcContext, request.user)
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
    * insert a new collection, but makes deep NewOf copy with refreshed IDs
    *
    * @return
    */
  def insertCopyOfCollection: Action[JsValue] = defaultAuthAction(parse.json) {
    request =>
      request.body.validate[OwcContext] fold(
        errors => {
          logger.error(JsError.toJson(errors).toString())
          val error: ErrorResult = ErrorResult("Collection could not be inserted",
            Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
          BadRequest(Json.toJson(error)).as(JSON)
        },
        owcContext => {
          logger.trace(Json.prettyPrint(owcContext.toJson))
          // TODO mangle Context and Resource IDs
          val idLink = new URL(s"https://portal.smart-project.info/context/document/${UUID.randomUUID().toString}")
          val updatedResources = owcContext.resource.map{
            owcResource =>
              val idLink = new URL(s"https://portal.smart-project.info/context/resource/${UUID.randomUUID().toString}")
              owcResource.newOf(idLink)
          }
          val refreshedCopyOfContext = OwcContextDAO.refreshedCopy(owcContext, idLink, Some(updatedResources))

          val inserted = collectionsService.insertCollection(refreshedCopyOfContext, request.user)
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
  def updateCollection: Action[JsValue] = defaultAuthAction(parse.json) {
    request =>
      request.body.validate[OwcContext] fold(
        errors => {
          logger.error(JsError.toJson(errors).toString())
          val error: ErrorResult = ErrorResult("Collection could not be updated",
            Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
          BadRequest(Json.toJson(error)).as(JSON)
        },
        owcContext => {
          logger.trace(Json.prettyPrint(owcContext.toJson))
          val updated = collectionsService.updateCollection(owcContext, request.user)
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
    * add resource AS IS to collection, beware may quickly result in unique DB constraint violations
    *
    * @param owcContextId
    * @return
    */
  def addResourceToCollection(owcContextId: String): Action[JsValue] = defaultAuthAction(parse.json) {
    request =>
      request.body.validate[OwcResource] fold(
        errors => {
          logger.error(JsError.toJson(errors).toString())
          val error: ErrorResult = ErrorResult("Provided OwcResource not valid",
            Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
          BadRequest(Json.toJson(error)).as(JSON)
        },
        owcResource => {
          logger.trace(Json.prettyPrint(owcResource.toJson))
          val collectionDoc = collectionsService.getOwcContextsForUserAndId(
            userOption = Some(request.user), owcContextIdOption = Some(owcContextId)).headOption
          val updatedCollection = collectionDoc.map {
            o =>
              val updated = o.copy(resource = o.resource ++ Seq(owcResource))
              collectionsService.updateCollection(updated, request.user)
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
    * add resource to collection, but makes deep NewOf copy with refreshed IDs
    *
    * @param owcContextId
    * @return
    */
  def addCopyOfResourceToCollection(owcContextId: String): Action[JsValue] = defaultAuthAction(parse.json) {
    request =>
      request.body.validate[OwcResource] fold(
        errors => {
          logger.error(JsError.toJson(errors).toString())
          val error: ErrorResult = ErrorResult("Provided OwcResource not valid",
            Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
          BadRequest(Json.toJson(error)).as(JSON)
        },
        owcResource => {
          logger.trace(Json.prettyPrint(owcResource.toJson))
          val collectionDoc = collectionsService.getOwcContextsForUserAndId(
            userOption = Some(request.user), owcContextIdOption = Some(owcContextId)).headOption
          val updatedCollection = collectionDoc.map {
            o =>
              val idLink = new URL(s"https://portal.smart-project.info/context/resource/${UUID.randomUUID().toString}")
              val refreshedCopyOfResource = owcResource.newOf(idLink)
              val updated = o.copy(resource = o.resource ++ Seq(refreshedCopyOfResource))
              collectionsService.updateCollection(updated, request.user)
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
  def replaceResourceInCollection(owcContextId: String): Action[JsValue] = defaultAuthAction(parse.json) {
    request =>
      request.body.validate[OwcResource] fold(
        errors => {
          logger.error(JsError.toJson(errors).toString())
          val error: ErrorResult = ErrorResult("replace resource failed",
            Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
          BadRequest(Json.toJson(error)).as(JSON)
        },
        owcResource => {
          logger.trace(Json.prettyPrint(owcResource.toJson))
          val collectionDoc = collectionsService.getOwcContextsForUserAndId(
            userOption = Some(request.user), owcContextIdOption = Some(owcContextId)).headOption
          val updatedCollection = collectionDoc.map {
            o =>
              val newResources = o.resource.filterNot(r => r.id.equals(owcResource.id))
              val updated = o.copy(resource = newResources ++ Seq(owcResource))
              collectionsService.updateCollection(updated, request.user)
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
  def deleteResourceFromCollection(owcContextId: String, owcResourceId: String): Action[Unit] = defaultAuthAction(parse.empty) {
    request =>
      val collectionDoc = collectionsService.getOwcContextsForUserAndId(
        userOption = Some(request.user), owcContextIdOption = Some(owcContextId)).headOption
      val updatedCollection = collectionDoc.map {
        o =>
          val newResources = o.resource.filterNot(r => r.id.toString.equals(owcResourceId))
          val updated = o.copy(resource = newResources)
          collectionsService.updateCollection(updated, request.user)
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
  def deleteCollection(owcContextId: String): Action[Unit] = defaultAuthAction(parse.empty) {
    request =>
      val collectionDoc = collectionsService.getOwcContextsForUserAndId(
        userOption = Some(request.user), owcContextIdOption = Some(owcContextId)).headOption
      val deleted = collectionDoc.exists(o => collectionsService.deleteCollection(o, request.user))
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

    collectionsService.getOwcContextsForUserAndId(None, None).foreach {
      doc =>
        val docPart =
          s"""<url>
             |  <loc>https://dev.smart-project.info/#${doc.id.getPath}</loc>
             |  <lastmod>${doc.updateDate.format(DateTimeFormatter.ISO_DATE)}</lastmod>
             |  <priority>1.0</priority>
             |</url>
          """.stripMargin
        owcXmlElements.append(docPart)
        doc.resource.foreach {
          res =>
            val resPart =
              s"""<url>
                 |  <loc>https://dev.smart-project.info/#${res.id.getPath}</loc>
                 |  <lastmod>${doc.updateDate.format(DateTimeFormatter.ISO_DATE)}</lastmod>
                 |  <priority>1.0</priority>
                 |</url>
          """.stripMargin
            owcXmlElements.append(resPart)
        }
    }
    owcXmlElements.append("</urlset>\n")
    Ok(owcXmlElements.mkString).as("application/xml")
  }
}
