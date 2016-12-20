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

import models.owc.{OwcDocumentDAO, OwcOfferingDAO, OwcPropertiesDAO}
import models.users._
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.Controller
import services.EmailService
import utils.{ClassnameLogger, PasswordHashing}

@Singleton
class CollectionsController @Inject()(override val configuration: Configuration,
                                      override val cache: CacheApi,
                                      emailService: EmailService,
                                      userDAO: UserDAO,
                                      owcPropertiesDAO: OwcPropertiesDAO,
                                      owcOfferingDAO: OwcOfferingDAO,
                                      owcDocumentDAO: OwcDocumentDAO,
                                      override val passwordHashing: PasswordHashing) extends Controller with ClassnameLogger with Security {

  lazy private val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")

  /**
    *
    * @param id
    * @return
    */
  def getCollections(id: Option[String]) = HasOptionalToken(parse.empty) {
    authUserOption =>
      implicit request =>
        authUserOption.fold {
          id.fold {
            // docs for anonymous, no id provided => all public docs
            val docs = owcDocumentDAO.getAllOwcDocuments.map(doc => doc.toJson)
            Ok(Json.obj("status" -> "OK", "count" -> docs.size, "collections" -> JsArray(docs)))
          } {
            // docs for anonymous, but id provided only one doc if available
            id => {
              val docs = owcDocumentDAO.findOwcDocumentsById(id).map(doc => doc.toJson).toSeq
              Ok(Json.obj("status" -> "OK", "count" -> docs.size, "collections" -> JsArray(docs)))
            }
          }
        } { authUser => {
            userDAO.findByUsername(authUser).fold {
              logger.trace("User not found.")
              val docsForAnonymous = owcDocumentDAO.getAllOwcDocuments.map( doc => doc.toJson)
              Ok(Json.obj("status" -> "OK", "count" -> docsForAnonymous.size, "collections" -> JsArray(docsForAnonymous)))
            } { user =>
              val docsForUser = owcDocumentDAO.getAllOwcDocuments.map( doc => doc.toJson)
              Ok(Json.obj("status" -> "OK", "count" -> docsForUser.size, "collections" -> JsArray(docsForUser)))
            }
          }
        }

  }

  /**
    *
    * @return
    */
  def createCollection = HasToken(parse.json) {
    token =>
      authUser =>
        implicit request =>
          NotImplemented("Not yet implemented")
  }

  /**
    *
    * @return
    */
  def updateCollection = HasToken(parse.json) {
    token =>
      authUser =>
        implicit request =>
          NotImplemented("Not yet implemented")
  }

  /**
    *
    * @param id id of the OwcDoc
    * @return
    */
  def deleteCollection(id: String) = HasToken(parse.empty) {
    token =>
      authUser =>
        implicit request =>
          NotImplemented("Not yet implemented")
  }
}

