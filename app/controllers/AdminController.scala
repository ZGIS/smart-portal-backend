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
import javax.inject._

import models.ErrorResult
import models.users._
import org.apache.commons.lang3.StringEscapeUtils
import play.api.Configuration
import play.api.http.MimeTypes
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsArray, JsError, JsValue, Json}
import play.api.mvc._
import services._
import utils.{ClassnameLogger, PasswordHashing}

@Singleton
class AdminController @Inject()(val configuration: Configuration,
                                val userService: UserService,
                                val passwordHashing: PasswordHashing,
                                emailService: EmailService,
                                adminService: AdminService,
                                collectionsService: OwcCollectionsService,
                                googleService: GoogleServicesDAO,
                                authenticationAction: AuthenticationAction,
                                adminPermissionCheckAction: AdminPermissionCheckAction)
  extends Controller with ClassnameLogger with Security {

  lazy private val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")
  lazy private val uploadDataPath: String = configuration.getString("smart.upload.datapath")
    .getOrElse("/tmp")

  /**
    * Am I Admin? conf value compared with logged-in user based on security token, as Angular guard
    *
    * @return
    */
  def amiAdmin: Action[Unit] =
    (authenticationAction
      andThen userAction(userService)
      andThen adminPermissionCheckAction)(parse.empty) {

      request =>
        Ok(Json.obj("status" -> "OK",
          "token" -> request.authenticatedRequest.userSession.token,
          "email" -> request.user.email.value))

  }

  /**
    * get all users to list for admin
    *
    * @return
    */
  def getAllUserGroups: Action[Unit] = HasToken(parse.empty) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          if (!adminService.isAdmin(cachedSecUserEmail)) {
            logger.error("User email not Admin.")
            val error = ErrorResult("User email not Admin.", None)
            Unauthorized(Json.toJson(error)).as(JSON)
          } else {
            val userGroupList = adminService.getAllUserGroups.map(u => Json.toJson(u))
            Ok(Json.obj("status" -> "OK", "usergroups" -> JsArray(userGroupList)))
          }
  }

  def createUserGroupAsAdmin: Action[JsValue] = HasToken(parse.json) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          if (!adminService.isAdmin(cachedSecUserEmail)) {
            logger.error("User email not Admin.")
            val error = ErrorResult("User email not Admin.", None)
            Unauthorized(Json.toJson(error)).as(JSON)
          } else {
            request.body.validate[UserGroup].fold(
              errors => {
                logger.error(JsError.toJson(errors).toString())
                val error: ErrorResult = ErrorResult("Usergroup format could not be read.",
                  Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
                BadRequest(Json.toJson(error)).as(JSON)
              },
              userGroup => {
                adminService.createUserGroup(userGroup).fold {
                  logger.error("Error creating the user group.")
                  val error = ErrorResult("Error creating the user group.", None)
                  BadRequest(Json.toJson(error)).as(JSON)
                } {
                  ugroup =>
                    Ok(Json.obj("status" -> "OK", "token" -> token, "usergroup" -> Json.toJson(ugroup)))
                }
              })
          }
  }

  def updateUserGroupAsAdmin: Action[JsValue] = HasToken(parse.json) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          if (!adminService.isAdmin(cachedSecUserEmail)) {
            logger.error("User email not Admin.")
            val error = ErrorResult("User email not Admin.", None)
            Unauthorized(Json.toJson(error)).as(JSON)
          } else {
            request.body.validate[UserGroup].fold(
              errors => {
                logger.error(JsError.toJson(errors).toString())
                val error: ErrorResult = ErrorResult("Usergroup format could not be read.",
                  Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
                BadRequest(Json.toJson(error)).as(JSON)
              },
              userGroup => {
                adminService.updateUserGroup(userGroup).fold {
                  logger.error("Error updating the user group.")
                  val error = ErrorResult("Error updating the user group.", None)
                  BadRequest(Json.toJson(error)).as(JSON)
                } {
                  ugroup =>
                    Ok(Json.obj("status" -> "OK", "token" -> token, "usergroup" -> Json.toJson(ugroup)))
                }
              })
          }
  }

  def deleteUserGroupAsAdmin: Action[JsValue] = HasToken(parse.json) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          if (!adminService.isAdmin(cachedSecUserEmail)) {
            logger.error("User email not Admin.")
            val error = ErrorResult("User email not Admin.", None)
            Unauthorized(Json.toJson(error)).as(JSON)
          } else {
            request.body.validate[UserGroup].fold(
              errors => {
                logger.error(JsError.toJson(errors).toString())
                val error: ErrorResult = ErrorResult("Usergroup format could not be read.",
                  Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
                BadRequest(Json.toJson(error)).as(JSON)
              },
              userGroup => {
                if (!adminService.deleteUserGroup(userGroup)) {
                  logger.error("Error deleting the user group.")
                  val error = ErrorResult("Error deleting the user group.", None)
                  BadRequest(Json.toJson(error)).as(JSON)
                } else {
                  Ok(Json.obj("status" -> "OK", "deleted" -> Json.toJson(userGroup)))
                }
              })
          }
  }

  /**
    * TODO
    *
    * stub to upload
    *
    * @return
    */
  def sparqleUpdateCollection: Action[MultipartFormData[TemporaryFile]] = HasToken(parse.multipartFormData) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          if (!adminService.isAdmin(cachedSecUserEmail)) {
            logger.error("User email not Admin.")
            val error = ErrorResult("User email not Admin.", None)
            Unauthorized(Json.toJson(error)).as(JSON)
          } else {

            request.body.file("file").map { theFile =>

              val filename = theFile.filename
              val contentType = theFile.contentType
              val pathOfUploadTmp = Paths.get(uploadDataPath)
              val intermTempDir = Files.createTempDirectory(pathOfUploadTmp, "sac-upload-")
              val tmpFile = new File(intermTempDir.resolve(filename).toAbsolutePath.toString)
              val handle = theFile.ref.moveTo(tmpFile)
              val kilobytes = handle.length / 1024

              // TODO upload handle sparql update into Jena from here
              val parts = request.body.dataParts
              parts.foreach(tuple1 =>
                logger.info(s"${tuple1._1} + ${tuple1._2.mkString("; ")}")
              )

              NotImplemented(Json.obj("status" -> "NotImplemented", "message" -> s"vocab/sparql collection file uploaded $filename."))
            } getOrElse {
              logger.error("file upload from client failed")
              val error = ErrorResult("File upload from client failed.", None)
              NotImplemented(Json.toJson(error)).as(JSON)
            }
          }
  }

  /**
    * for admin view list all users
    *
    * @return
    */
  def getAllUsers: Action[Unit] = HasToken(parse.empty) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          if (!adminService.isAdmin(cachedSecUserEmail)) {
            logger.error("User email not Admin.")
            val error = ErrorResult("User email not Admin.", None)
            Unauthorized(Json.toJson(error)).as(JSON)
          } else {
            val userList = adminService.getallUsers.map(u => Json.toJson(u))
            Ok(Json.obj("status" -> "OK", "users" -> JsArray(userList)))
          }
  }

  /**
    * for admin view list all user files
    *
    * @return
    */
  def getAllUserFiles: Action[Unit] = HasToken(parse.empty) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          if (!adminService.isAdmin(cachedSecUserEmail)) {
            logger.error("User email not Admin.")
            val error = ErrorResult("User email not Admin.", None)
            Unauthorized(Json.toJson(error)).as(JSON)
          } else {
            val userList = adminService.getallUserFiles.map(u => Json.toJson(u))
            Ok(Json.obj("status" -> "OK", "userfiles" -> JsArray(userList)))
          }
  }

  /**
    * for admin view list all user metadata records
    *
    * @return
    */
  def getallUserMetaRecords: Action[Unit] = HasToken(parse.empty) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          if (!adminService.isAdmin(cachedSecUserEmail)) {
            logger.error("User email not Admin.")
            val error = ErrorResult("User email not Admin.", None)
            Unauthorized(Json.toJson(error)).as(JSON)
          } else {
            val userList = adminService.getallUserMetaRecords.map(u => Json.toJson(u))
            Ok(Json.obj("status" -> "OK", "metarecords" -> JsArray(userList)))
          }
  }

  /**
    * for admin view list all user link request logs
    *
    * @return
    */
  def getallUserLinkLoggings: Action[Unit] = HasToken(parse.empty) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          if (!adminService.isAdmin(cachedSecUserEmail)) {
            logger.error("User email not Admin.")
            val error = ErrorResult("User email not Admin.", None)
            Unauthorized(Json.toJson(error)).as(JSON)
          } else {
            val loglist = adminService.getAllUserLinkLoggings.map(u => Json.toJson(u))
            Ok(Json.obj("status" -> "OK", "loglist" -> JsArray(loglist)))
          }
  }

  /**
    * for admin view find user link request logs by file name or link
    *
    * @return
    */
  def findUserLinkLoggingsByLink(link: String): Action[Unit] = HasToken(parse.empty) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          if (!adminService.isAdmin(cachedSecUserEmail)) {
            logger.error("User email not Admin.")
            val error = ErrorResult("User email not Admin.", None)
            Unauthorized(Json.toJson(error)).as(JSON)
          } else {
            val loglist = adminService.findUserLinkLoggingsByLink(link).map(u => Json.toJson(u))
            Ok(Json.obj("status" -> "OK", "loglist" -> JsArray(loglist)))
          }
  }

  /**
    * handle for admin to block users by their email so they can't log-in (and unblock them again)
    *
    * @param command
    * @param email
    * @return
    */
  def blockUnblockUsers(command: String, email: String): Action[Unit] = HasToken(parse.empty) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          if (!adminService.isAdmin(cachedSecUserEmail)) {
            logger.error("User email not Admin.")
            val error = ErrorResult("User email not Admin.", None)
            Unauthorized(Json.toJson(error)).as(JSON)
          } else {
            adminService.blockUnblockUsers(command, email).fold {
              logger.error(s"User $email : status update ($command) failed.")
              val error = ErrorResult(s"User $email : status update ($command) failed.", None)
              BadRequest(Json.toJson(error)).as(JSON)
            } {
              updatedUser =>
                Ok(Json.obj("status" -> "Ok", command -> updatedUser.laststatustoken, "email" -> cachedSecUserEmail))
            }
          }
  }

}
