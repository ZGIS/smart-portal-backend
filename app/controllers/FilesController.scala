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
import play.api.libs.json.{JsArray, Json}
import play.api.mvc._
import services.{GoogleServicesDAO, LocalBlobInfo, OwcCollectionsService, UserService}
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
    * upload a file to the portal, then it'll be forwarded to Google Cloud bucket and reference stored
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
          case Right(blobInfo) =>
            // return the public download link
            val userFileEntryOk = userService.insertUserFileEntry(filename, request.user.email, blobInfo.mediaLink)
            userFileEntryOk.fold {
              logger.error("file metadata insert failed.")
              val error = ErrorResult("File metadata insert failed.", None)
              BadRequest(Json.toJson(error)).as(JSON)
            } { userFile =>
              val owcResource = collectionsService.fileMetadata(userFile, contentType, Try(kilobytes.toInt).toOption)
              logger.trace(Json.prettyPrint(owcResource.toJson))
              logger.debug(s"file upload $filename to ${blobInfo.mediaLink} with reference ${userFile.linkreference}; bob details: ${blobInfo.name} ${blobInfo.bucket} ${blobInfo.contentType} ${blobInfo.contentEncoding} ${blobInfo.size} ${blobInfo.md5}")
              Try(handle.delete()).failed.map(ex => logger.error(ex.getLocalizedMessage))
              Try(Files.delete(intermTempDir)).failed.map(ex => logger.error(ex.getLocalizedMessage))

              //FIXME SR do we also want to have a general "return" object? Status is always in the respone so it does not need to be in here
              val added = collectionsService.addPlainFileResourceToUserDefaultCollection(owcResource, request.user)
              if (added) {
                Ok(Json.obj("status" -> "OK", "message" -> s"file uploaded $filename.", "file" -> userFile.linkreference, "entry" -> owcResource.toJson))
              } else {
                logger.error("Insert into own collection failed.")
                val error = ErrorResult("Insert into own collection failed.", None)
                BadRequest(Json.toJson(error)).as(JSON)
              }
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
    * retrieve the remote file download link for the uuid userfile mapping,
    * uuid can't be guessed so comparatively safe 'open' download,
    * (hide remote url so people have to download via our portal and we can count downloads)
    *
    * @param uuid
    * @return
    */
  def mappedFileLinkFor(uuid: String): Action[Unit] = optionalAuthenticationAction(parse.empty) {
    request =>
      userService.findUserFileByUuid(UUID.fromString(uuid)).fold {
        logger.error("file not found")
        val error = ErrorResult("file not found.", None)
        BadRequest(Json.toJson(error)).as(JSON)
      } {
        userFile =>
          val logRequest = UserLinkLogging(id = None,
            timestamp = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)),
            ipaddress = Some(request.remoteAddress),
            useragent = request.headers.get(UserAgentHeader),
            email = request.optionalSession.map(_.email),
            link = userFile.linkreference,
            referer = request.headers.get(RefererHeader))

          val updated = userService.logLinkInfo(logRequest)
          logger.trace(logRequest.toString)
          Ok(Json.obj("status" -> "OK", "linkreference" -> userFile.linkreference, "originalfilename" -> userFile.originalfilename))
      }
  }

  /**
    * returns user-owned file object references
    *
    * @return
    */
  def getUserFiles: Action[Unit] = (authenticationAction andThen userAction)(parse.empty) {
    request =>
      val userfiles = userService.findUserFileByAccountSubject(request.user).map(u => Json.toJson(u))
      Ok(Json.obj("status" -> "OK", "userfiles" -> JsArray(userfiles)))
  }

  /**
    * get remote blob details for user owning it
    *
    * @param uuid
    * @return
    */
  def getBlobInfoForMappedLink(uuid: String): Action[Unit] = (authenticationAction andThen userAction) (parse.empty) {
    request =>
      val foundfile = userService.findUserFileByAccountSubject(request.user).exists(f => f.uuid.equals(UUID.fromString(uuid)))
      val notFoundBlock = {
        logger.error("file not found")
        val error = ErrorResult("file not found.", None)
        BadRequest(Json.toJson(error)).as(JSON)
      }
      if (!foundfile) {
        notFoundBlock
      } else {
        userService.findUserFileByUuid(UUID.fromString(uuid))
          .map { userFile =>
            googleService.getFileBlob(userFile.originalfilename).fold[Result](
              error => BadRequest(Json.toJson(error)).as(JSON),
              blobInfo => Ok(Json.obj("status" -> "OK", "blobinfo" -> blobInfo.toJson)))
          }
          .getOrElse(notFoundBlock)
      }
  }

  /**
    * delete a remote file as owning user
    *
    * @param uuid
    * @return
    */
  def deleteBlobForMappedLink(uuid: String): Action[Unit] = (authenticationAction andThen userAction) (parse.empty) {
    request =>
      val foundfile = userService.findUserFileByAccountSubject(request.user).exists(f => f.uuid.equals(UUID.fromString(uuid)))
      val notFoundBlock = {
        logger.error("file not found")
        val error = ErrorResult("file not found.", None)
        BadRequest(Json.toJson(error)).as(JSON)
      }
      if (!foundfile) {
        notFoundBlock
      } else {
        userService.findUserFileByUuid(UUID.fromString(uuid))
          .map { userFile =>
            googleService.deleteFileBlob(userFile.originalfilename).fold[Result] {
              // if empty
              Ok(Json.obj("status" -> "OK", "linkreference" -> userFile.linkreference, "originalfilename" -> userFile.originalfilename, "message" -> "deleted"))
            } {
              error => BadRequest(Json.toJson(error)).as(JSON)
            }
          }
          .getOrElse(notFoundBlock)
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
