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
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import controllers.security._
import javax.inject._
import models.ErrorResult
import models.rdf._
import models.users._
import org.apache.commons.lang3.StringEscapeUtils
import org.apache.poi.ss.usermodel.WorkbookFactory
import play.api.Configuration
import play.api.libs.Files.TemporaryFile
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._
import services._
import utils.ClassnameLogger

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AdminController @Inject()(wsClient: WSClient,
                                implicit val context: ExecutionContext,
                                implicit val configuration: Configuration,
                                userService: UserService,
                                emailService: EmailService,
                                adminService: AdminService,
                                collectionsService: OwcCollectionsService,
                                googleService: GoogleServicesDAO,
                                authenticationAction: AuthenticationAction,
                                userAction: UserAction,
                                adminPermissionCheckAction: AdminPermissionCheckAction)
  extends Controller with ClassnameLogger {

  /**
    * the eawesome action composition, be aware that some perform DB queries
    */
  private val defaultAdminAction = authenticationAction andThen userAction andThen adminPermissionCheckAction



  private lazy val appSecret = configuration.getString("play.crypto.secret").getOrElse("insecure")

  /**
    * Am I Admin? conf value compared with logged-in user based on security token, as Angular guard
    *
    * @return
    */
  def amiAdmin: Action[Unit] = defaultAdminAction(parse.empty) {
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
  def getAllUserGroups: Action[Unit] = defaultAdminAction(parse.empty) {
    request =>
      val usergroups = adminService.getAllUserGroups.map(u => Json.toJson(u))
      Ok(Json.obj("status" -> "OK", "usergroups" -> JsArray(usergroups)))
  }

  def createUserGroupAsAdmin: Action[JsValue] = defaultAdminAction(parse.json) {

    request =>
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
              Ok(Json.obj("status" -> "OK", "usergroup" -> Json.toJson(ugroup)))
          }
        })
  }

  def updateUserGroupAsAdmin: Action[JsValue] = defaultAdminAction(parse.json) {
    request =>
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
              Ok(Json.obj("status" -> "OK", "usergroup" -> Json.toJson(ugroup)))
          }
        })
  }

  def deleteUserGroupAsAdmin: Action[JsValue] = defaultAdminAction(parse.json) {
    request =>
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

  /**
    * TODO
    *
    * stub to upload
    *
    * @return
    */
  def sparqleUpdateCollection: Action[MultipartFormData[TemporaryFile]] = defaultAdminAction.async(parse.multipartFormData) {
    request =>
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

        val converter = new XlsToSparqlRdfConverter
        val now = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone))
        val date = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val updateProcess = filename match {
          case "PortalCategories.xlsx" => // FIXME in case of categories_test.rdf
            val workbook = WorkbookFactory.create(tmpFile)
            val worksheet = workbook.getSheet("science domain categories")
            val synonyms_sheets = workbook.getSheet("synonyms")
            val rdfCategories = converter.buildCategoriesFromSheet(worksheet, synonyms_sheets)
            val fullRdfString: String = CategoryHolder.toCompleteRdf(rdfCategories)
            updateVocabBucketWith(fullRdfString, "categories_test.rdf", vocabBucketFolder)
          case "ResearchPrgrm.xlsx" => // TODO in case of research-pg.rdf
            val workbook = WorkbookFactory.create(tmpFile)
            val worksheet = workbook.getSheet("Research programmes")
            val rdfResearchPGs = converter.buildResearchPgFromSheet(worksheet)
            val fullRdfString: String = ResearchPGHolder.toCompleteCollectionRdf(rdfResearchPGs, date)
            updateVocabBucketWith(fullRdfString, "research-pg.rdf", vocabBucketFolder)
          case "awahou.xlsx" => // TODO in case of awahou.rdf
            val fullRdfString = "awahou.rdf"
            // updateVocabBucketWith(fullRdfString, "_awahou.rdf", vocabBucketFolder)
            Left(ErrorResult(s"$fullRdfString not yet implemented, unable to proceed.", None))
          case "glossary.xlsx" => // TODO in case of glossary.rdf
            val fullRdfString = "glossary.rdf"
            // updateVocabBucketWith(fullRdfString, "_glossary.rdf", vocabBucketFolder)
            Left(ErrorResult(s"$fullRdfString not yet implemented, unable to proceed.", None))
          case "ngmp.xlsx" => // TODO in case of ngmp.rdf
            val fullRdfString = "ngmp.rdf"
            // updateVocabBucketWith(fullRdfString, "_ngmp.rdf", vocabBucketFolder)
            Left(ErrorResult(s"$fullRdfString not yet implemented, unable to proceed.", None))
          case "papawai.xlsx" => // TODO in case of papawai_3.rdf
            val fullRdfString = "papawai_3.rdf"
            // updateVocabBucketWith(fullRdfString, "_papawai_3.rdf", vocabBucketFolder)
            Left(ErrorResult(s"$fullRdfString not yet implemented, unable to proceed.", None))
          case _ => logger.error("no file name retrieved unable to proceed")
            Left(ErrorResult("no file name retrieved unable to proceed.", None))
        }

        updateProcess match {
          case Left(errorResult) =>
            logger.error(errorResult.message + errorResult.details.mkString)
            Future(InternalServerError(Json.toJson(errorResult)).as(JSON))
          case Right(blobInfo) =>
            logger.debug(blobInfo.toJson.toString())
            // and close https://github.com/ZGIS/smart-portal-backend/issues/23
            val jenaReload = callJenaKubeReload
            jenaReload.map {
              case None => Ok(Json.obj("status" -> "OK", "updated" -> blobInfo.toJson))
              case Some(error) => logger.error(error.message)
                BadRequest(Json.toJson(error)).as(JSON)
              case _ => logger.error("Bad Error jena reload")
                val error = ErrorResult("Update of running vocab service failed.", None)
                BadRequest(Json.toJson(error)).as(JSON)
            }
        }

      } getOrElse {
        logger.error("file upload from client failed")
        val error = ErrorResult("File upload from client failed.", None)
        Future (BadRequest(Json.toJson(error)).as(JSON))
      }
  }

  def updateVocabBucketWith(vocabString: String, fileName: String, pathPrefix: String): Either[ErrorResult, LocalBlobInfo] = {
    googleService.updateVocabFileGoogleBucket(vocabString, fileName, pathPrefix) match {
      case Left(errorResult) =>
        Left(errorResult)
      case Right(blobInfo) =>
        logger.debug(s"vocab/sparql collection file uploaded $fileName.")
        Right(blobInfo)
    }
  }

  def callJenaKubeReload: Future[Option[ErrorResult]] = {
    val digestHash = MessageDigest.getInstance("SHA-512").digest(appSecret.getBytes("UTF-8"))
    val hex = digestHash.map("%02X" format _).mkString
    wsClient.url(ADMIN_JENA_UPDATE_URL).withHeaders("Authorization" -> s"Bearer $hex").get().map { response =>
      if (response.status == 200) {
        logger.info(
          s"Successfully called $ADMIN_JENA_UPDATE_URL (${response.status} - ${response.statusText})")
        logger.info(s"response: ${response.body}")
        None
      } else {
        logger.warn(
          s"Call to '$ADMIN_JENA_UPDATE_URL' returned error: ${response.status} - ${response.statusText}")
        logger.warn(s"response: ${response.body}")
        Some(ErrorResult("Update of running vocab service failed.", Some(s"Call to '$ADMIN_JENA_UPDATE_URL' returned error: ${response.status} - ${response.statusText}")))
      }
    }
  }

  /**
    * for admin view list all users
    *
    * @return
    */
  def getAllUsers: Action[Unit] = defaultAdminAction(parse.empty) {
    request =>
      val users = adminService.getallUsers.map(u => Json.toJson(u.asProfileJs))
      Ok(Json.obj("status" -> "OK", "users" -> JsArray(users)))

  }

  /**
    * for admin view list all users
    *
    * @return
    */
  def getActiveSessions(max: Option[Int]): Action[Unit] = defaultAdminAction(parse.empty) {
    request =>
      val sessions = adminService.getActiveSessions(max: Option[Int]).map(u => Json.toJson(u))
      Ok(Json.obj("status" -> "OK", "sessions" -> JsArray(sessions)))

  }

  /**
    * for admin view list all users
    *
    * @return
    */
  def queryActiveSessions(token: Option[String], max: Option[Int], email: Option[String]): Action[Unit] = defaultAdminAction(parse.empty) {
    request =>
      val sessions = adminService.queryActiveSessions(token, max, email).map(u => Json.toJson(u))
      Ok(Json.obj("status" -> "OK", "sessions" -> JsArray(sessions)))

  }

  /**
    * remove a specific session, will force that user device/browser combo to login again
    *
    * @param token
    * @param email
    * @return
    */
  def removeActiveSessions(token: String, email: String): Action[Unit] = defaultAdminAction(parse.empty) {
    request =>
      val sessions = adminService.removeActiveSessions(token, email)
      Ok(Json.obj("status" -> "OK", "token" -> token, "email" -> email, "removed" -> sessions))
  }

  /**
    * for admin view list all user files
    *
    * @return
    */
  def getAllUserFiles: Action[Unit] = defaultAdminAction(parse.empty) {
    request =>
      val userfiles = adminService.getallUserFiles.map(u => Json.toJson(u))
      Ok(Json.obj("status" -> "OK", "userfiles" -> JsArray(userfiles)))
  }

  /**
    * admin list of remote files
    *
    * @return
    */
  def getAllGoogleFiles: Action[Unit] = defaultAdminAction(parse.empty) {
    request =>
      val bucketList = googleService.listbucket
      bucketList.fold[Result](
        error => BadRequest(Json.toJson(error)).as(JSON),
        blobSeq => Ok(Json.obj("status" -> "OK", "blobs" -> JsArray(blobSeq.map(b => b.toJson)))))
  }

  /**
    * for admin view list all user metadata records
    *
    * @return
    */
  def getallUserMetaRecords: Action[Unit] = defaultAdminAction(parse.empty) {
    request =>
      val metarecords = adminService.getallUserMetaRecords.map(u => Json.toJson(u))
      Ok(Json.obj("status" -> "OK", "metarecords" -> JsArray(metarecords)))
  }

  /**
    * for admin view list all user link request logs
    *
    * @return
    */
  def getallUserLinkLoggings(max: Option[Int]): Action[Unit] = defaultAdminAction(parse.empty) {
    request =>
      val loglist = adminService.getAllUserLinkLoggings(max).map(u => Json.toJson(u))
      Ok(Json.obj("status" -> "OK", "loglist" -> JsArray(loglist)))
  }

  /**
    * for admin view find user link request logs by file name or link
    *
    * @return
    */
  def queryLinkLoggings(link: Option[String], max: Option[Int], email: Option[String]): Action[Unit] = defaultAdminAction(parse.empty) {
    request =>
      val loglist = adminService.queryUserLinkLoggings(link, max, email).map(u => Json.toJson(u))
      Ok(Json.obj("status" -> "OK", "loglist" -> JsArray(loglist)))
  }

  /**
    * handle for admin to block users by their email so they can't log-in (and unblock them again)
    *
    * @param command
    * @param email
    * @return
    */
  def blockUnblockUsers(command: String, email: String): Action[Unit] = defaultAdminAction(parse.empty) {
    request =>
      adminService.blockUnblockUsers(command, email).fold {
        logger.error(s"User $email : status update ($command) failed.")
        val error = ErrorResult(s"User $email : status update ($command) failed.", None)
        BadRequest(Json.toJson(error)).as(JSON)
      } {
        updatedUser =>
          Ok(Json.obj("status" -> "Ok", command -> updatedUser.laststatustoken, "email" -> updatedUser.email.value))
      }
  }
}
