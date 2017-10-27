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

import java.io.File
import java.nio.file.{Files, Paths}
import java.time.{ZoneId, ZonedDateTime}
import javax.inject.Inject

import controllers.security.{RefererHeader, Secured, UserAgentHeader}
import models.ErrorResult
import models.users.UserLinkLogging
import play.api.Configuration
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc._
import services.{GoogleServicesDAO, OwcCollectionsService, UserService}
import utils.{ClassnameLogger, PasswordHashing}

import scala.util.Try

/**
  *
  * @param configuration
  * @param passwordHashing
  * @param googleService
  * @param collectionsService
  */
class FilesController @Inject()(val configuration: Configuration,
                                val userService: UserService,
                                val passwordHashing: PasswordHashing,
                                googleService: GoogleServicesDAO,
                                collectionsService: OwcCollectionsService
                               ) extends Controller with ClassnameLogger with Secured {

  lazy private val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")
  lazy private val uploadDataPath: String = configuration.getString("smart.upload.datapath")
    .getOrElse("/tmp")

  /**
    *
    * @return
    */
  def uploadMultipartForm: Action[MultipartFormData[TemporaryFile]] = HasToken(parse.multipartFormData) {
    token =>
      authUser =>
        implicit request =>

          request.body.file("file").map { theFile =>

            val filename = theFile.filename
            val contentType = theFile.contentType
            val pathOfUploadTmp = Paths.get(uploadDataPath)
            val intermTempDir = Files.createTempDirectory(pathOfUploadTmp, "sac-upload-")
            val tmpFile = new File(intermTempDir.resolve(filename).toAbsolutePath.toString)
            val handle = theFile.ref.moveTo(tmpFile)
            val kilobytes = handle.length / 1024

            googleService.uploadFileGoogleBucket(handle) match {
              case Left(errorResult) =>
                logger.error(errorResult.message + errorResult.details.mkString)
                InternalServerError(Json.toJson(errorResult)).as(JSON)
              case Right(blob) =>
                // return the public download link
                val owcResource = collectionsService.fileMetadata(filename, contentType, authUser, blob.getMediaLink(), Try(kilobytes.toInt).toOption)
                logger.trace(Json.prettyPrint(owcResource.toJson))

                val userFileEntryOk = userService.insertUserFileEntry(filename, authUser, blob.getMediaLink())
                val insertOk = collectionsService.addPlainFileResourceToUserDefaultCollection(owcResource, authUser)

                if (insertOk && userFileEntryOk.isDefined) {
                  logger.debug(s"file upload $filename to ${blob.getMediaLink}")
                  Try(handle.delete()).failed.map(ex => logger.error(ex.getLocalizedMessage))
                  Try(Files.delete(intermTempDir)).failed.map(ex => logger.error(ex.getLocalizedMessage))
                  //FIXME SR do we also want to have a general "return" object? Status is always in the respone so it does not need to be in here
                  Ok(Json.obj("status" -> "OK", "message" -> s"file uploaded $filename.", "file" -> blob.getMediaLink(), "entry" -> owcResource.toJson))
                } else {
                  logger.error("file metadata insert failed.")
                  val error = ErrorResult("File metadata insert failed.", None)
                  BadRequest(Json.toJson(error)).as(JSON)
                }
              case _ =>
                logger.error("file upload to cloud storage failed")
                val error = ErrorResult("File upload to cloud storage failed.", None)
                BadRequest(Json.toJson(error)).as(JSON)
            }

          }.getOrElse {
            logger.error("file upload from client failed")
            val error = ErrorResult("File upload from client failed.", None)
            BadRequest(Json.toJson(error)).as(JSON)
          }
  }

  /**
    * fire an entry into db for a url (somebody clicked ok to conset download file)
    *
    * @param link
    * @return
    */
  def logLinkInfo(link: String): Action[Unit] = HasOptionalToken(parse.empty) {
    authUserOption =>
      implicit request =>

        val logRequest = UserLinkLogging(id = None,
          timestamp = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)),
          ipaddress = Some(request.remoteAddress),
          useragent = request.headers.get(UserAgentHeader),
          email = authUserOption,
          link = link,
          referer = request.headers.get(RefererHeader))

        val updated = userService.logLinkInfo(logRequest)

        logger.trace(logRequest.toString)
        Ok(Json.obj("status" -> "OK", "message" -> s"$updated file request logged."))
  }

}
