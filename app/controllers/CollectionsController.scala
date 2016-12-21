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

import models.owc.{OwcDocument, OwcDocumentDAO, OwcOfferingDAO, OwcPropertiesDAO}
import models.users._
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.{JsArray, JsError, Json}
import play.api.mvc.Controller
import services.{EmailService, OwcCollectionsService}
import utils.{ClassnameLogger, PasswordHashing}

@Singleton
class CollectionsController @Inject()(config: Configuration,
                                      cacheApi: CacheApi,
                                      emailService: EmailService,
                                      collectionsService: OwcCollectionsService,
                                      override val passwordHashing: PasswordHashing) extends Controller with Security with ClassnameLogger {

  val cache: play.api.cache.CacheApi = cacheApi
  val configuration: play.api.Configuration = config
  lazy private val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")

  /**
    *
    * @param id
    * @return
    */
  def getCollections(id: Option[String]) = HasOptionalToken(parse.empty) {
    authUserOption =>
        implicit request =>
        val owcJsDocs = collectionsService.getOwcDocumentsForUserAndId(authUserOption, id).map( doc => doc.toJson)
        Ok(Json.obj("status" -> "OK", "count" -> owcJsDocs.size, "collections" -> JsArray(owcJsDocs)))

  }

  /**
    *
    * @return
    */
  def insertCollection = HasToken(parse.json) {
    token =>
      authUser =>
        implicit request =>
          request.body.validate[OwcDocument]fold(
            errors => {
              logger.error(JsError.toJson(errors).toString())
              BadRequest(Json.obj("status" -> "ERR", "message" -> JsError.toJson(errors)))
            },
            owcDocument => {
              val inserted = collectionsService.insertCollection(owcDocument, authUser)
              Ok(Json.obj("status" -> "OK", "message" -> "owcDocument inserted"))
            }
          )
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

