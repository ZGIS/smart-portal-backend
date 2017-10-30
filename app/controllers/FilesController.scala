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
import java.util.UUID
import javax.inject.Inject

import controllers.security._
import models.ErrorResult
import models.users.UserLinkLogging
import play.api.Configuration
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc._
import services.{GoogleServicesDAO, OwcCollectionsService, UserService}
import utils.ClassnameLogger

import scala.util.Try

/**
  *
  * @param configuration
  * @param googleService
  * @param collectionsService
  */
class FilesController @Inject()(implicit configuration: Configuration,
                                userService: UserService,
                                googleService: GoogleServicesDAO,
                                collectionsService: OwcCollectionsService,
                                optionalAuthenticationAction: OptionalAuthenticationAction,
                                authenticationAction: AuthenticationAction,
                                userAction: UserAction
                               ) extends Controller with ClassnameLogger {
  /**
    *
    * @return
    */
  def uploadMultipartForm: Action[MultipartFormData[TemporaryFile]] = (authenticationAction andThen userAction) (parse.multipartFormData) {
    request =>
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
            val userFileEntryOk = userService.insertUserFileEntry(filename, request.user.email, blob.getMediaLink())
            userFileEntryOk.fold {
              logger.error("file metadata insert failed.")
              val error = ErrorResult("File metadata insert failed.", None)
              BadRequest(Json.toJson(error)).as(JSON)
            } { userFile =>
              val owcResource = collectionsService.fileMetadata(userFile, contentType, Try(kilobytes.toInt).toOption)
              logger.trace(Json.prettyPrint(owcResource.toJson))
              logger.debug(s"file upload $filename to ${blob.getMediaLink} with reference ${userFile.linkreference}")
              Try(handle.delete()).failed.map(ex => logger.error(ex.getLocalizedMessage))
              Try(Files.delete(intermTempDir)).failed.map(ex => logger.error(ex.getLocalizedMessage))
              //FIXME SR do we also want to have a general "return" object? Status is always in the respone so it does not need to be in here
              Ok(Json.obj("status" -> "OK", "message" -> s"file uploaded $filename.", "file" -> userFile.linkreference, "entry" -> owcResource.toJson))
            }
          case _ =>
            logger.error("file upload to cloud storage failed")
            val error = ErrorResult("File upload to cloud storage failed.", None)
            BadRequest(Json.toJson(error)).as(JSON)
        }
      }.getOrElse {
        logger.error("file upload failed, no file found for upload")
        val error = ErrorResult("file upload failed, no file found for upload", None)
        BadRequest(Json.toJson(error)).as(JSON)
      }
  }

  /**
    *
    * @param id
    * @return
    */
  def mappedFileLinkFor(id: String): Action[Unit] = optionalAuthenticationAction(parse.empty) {
    request =>
      userService.findUserFileByUuid(UUID.fromString(id)).fold{
        logger.error("file not found")
        val error = ErrorResult("file not found.", None)
        BadRequest(Json.toJson(error)).as(JSON)
      }{
        userFile =>
          Ok(Json.obj("status" -> "OK", "filelink" -> userFile.linkreference, "filename" -> userFile.originalfilename))
      }
  }

  /**
    * fire an entry into db for a url (somebody clicked ok to conset download file)
    *
    * @param link
    * @return
    */
  def logLinkInfo(link: String): Action[Unit] = optionalAuthenticationAction(parse.empty) {
    request =>
      val logRequest = UserLinkLogging(id = None,
        timestamp = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)),
        ipaddress = Some(request.remoteAddress),
        useragent = request.headers.get(UserAgentHeader),
        email = request.optionalSession.map(_.email),
        link = link,
        referer = request.headers.get(RefererHeader))

      val updated = userService.logLinkInfo(logRequest)

      logger.trace(logRequest.toString)
      Ok(Json.obj("status" -> "OK", "message" -> s"$updated file request logged."))
  }

}
