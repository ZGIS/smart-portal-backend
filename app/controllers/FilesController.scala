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

import javax.inject.Inject

import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.{GoogleServicesDAO, OwcCollectionsService}
import utils.{ClassnameLogger, PasswordHashing}
import java.io.File
import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID

import models.owc.{HttpLinkOffering, OwcAuthor, OwcEntry, OwcLink, OwcOperation, OwcProperties}

import scala.concurrent.ExecutionContext

/**
  *
  * @param configuration
  * @param cache
  * @param passwordHashing
  * @param wsClient
  * @param context
  * @param googleService
  * @param collectionsService
  */
class FilesController @Inject()(val configuration: Configuration,
                                val cache: CacheApi,
                                val passwordHashing: PasswordHashing,
                                wsClient: WSClient,
                                implicit val context: ExecutionContext,
                                googleService: GoogleServicesDAO,
                                collectionsService: OwcCollectionsService
                               )
  extends Controller with ClassnameLogger with Security {

  /**
    *
    * @param filename
    * @param contentType
    * @param email
    * @return
    */
  def fileMetadata(filename: String, contentType: Option[String], email: String): OwcEntry = {

    val propsUuid = UUID.randomUUID()
    val updatedTime = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())

    val httpGetOps = OwcOperation(UUID.randomUUID(),
      "GetFile",
      "GET",
      contentType.getOrElse("application/octet-stream"),
      "http://portal.smart-project.info/fs/",
      None, None)

    val httpLinkOffering = HttpLinkOffering(
      UUID.randomUUID(),
      "http://www.opengis.net/spec/owc-geojson/1.0/req/http-link",
      List(httpGetOps),
      List()
    )

    val link1 = OwcLink(UUID.randomUUID(), "self", Some("application/json"),
      s"http://portal.smart-project.info/context/${propsUuid.toString}", None)

    val useLimitation = s"IP limitation: Please inquire with $email"

    val entryProps = OwcProperties(
      propsUuid,
      "en",
      filename,
      Some(s"$filename uploaded via GW Hub by $email"),
      Some(updatedTime),
      None,
      Some(useLimitation),
      List(),
      List(),
      None,
      Some("GNS Science"),
      List(),
      List(link1)
    )
    val owcEntry = OwcEntry(s"http://portal.smart-project.info/context/${propsUuid.toString}",
      None, entryProps, List(httpLinkOffering))

    owcEntry
  }

  /**
    *
    * @return
    */
  def uploadMultipartForm = HasToken(parse.multipartFormData) {
    token =>
      authUser =>
        implicit request =>

          request.body.file("file").map { theFile =>
            import java.io.File
            val filename = theFile.filename
            val contentType = theFile.contentType
            theFile.ref.moveTo(new File(s"/tmp/$filename"))

            val owcEntry = fileMetadata(filename, contentType, authUser)
            val insertOk = collectionsService.addPlainFileEntryToUserDefaultCollection(owcEntry, authUser)

            if (insertOk) {
              logger.debug(s"file upload $filename")
              Ok(Json.obj("status" -> "OK", "message" -> s"file uploaded $filename.", "entry" -> owcEntry.toJson))
            } else {
              logger.error(s"file metadata insert failed.")
              BadRequest(Json.obj("status" -> "ERR", "message" -> "file metadata insert failed."))
            }
          }.getOrElse {
            logger.error("file upload failed")
            BadRequest(Json.obj("status" -> "ERR", "message" -> "file upload failed."))
          }
  }

  /*  def uploadDirect = Action(parse.temporaryFile) { request =>
      request.body.moveTo(new File("/tmp/uploaded.bin"))
      Ok("File uploaded")
    }*/


}
