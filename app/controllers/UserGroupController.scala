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

import java.util.UUID
import javax.inject._

import controllers.security._
import models.ErrorResult
import models.users._
import org.apache.commons.lang3.StringEscapeUtils
import play.api.Configuration
import play.api.libs.json.{JsArray, JsError, JsValue, Json}
import play.api.mvc._
import services._
import utils.ClassnameLogger

@Singleton
class UserGroupController @Inject()(implicit configuration: Configuration,
                                    userService: UserService,
                                    userGroupService: UserGroupService,
                                    emailService: EmailService,
                                    collectionsService: OwcCollectionsService,
                                    googleService: GoogleServicesDAO,
                                    authenticationAction: AuthenticationAction,
                                    userAction: UserAction)
  extends Controller with ClassnameLogger {

  /**
    * default actions composition, much more readable and "composable than original HasToken style implementation
    */
  private val defaultAuthAction = authenticationAction andThen userAction

  /**
    * get user's own groups
    *
    * @return
    */
  def getUsersOwnUserGroups: Action[Unit] = defaultAuthAction(parse.empty) {
    implicit request =>
      val ugList = userGroupService.getUsersOwnUserGroups(request.user)
      Ok(Json.obj("status" -> "OK", "usergroups" -> JsArray(ugList.map(ug => Json.toJson(ug)))))
  }

  /**
    * this is a specific misuse of the UserGroup container to get one user's collections
    * access rights list, one group item per OwcContext with one userlevel,
    * where only the user is important (the original owner of the current mentioned context),
    * and the context visibility has the context and the actual context's visibilty,
    * the groups name, and the uuid's should be generic and even hint on the verballhornte use
    *
    * @return
    */
  def getOwcContextsRightsMatrixForUser: Action[Unit] = defaultAuthAction(parse.empty) {
    implicit request =>
      // beware already also contains owcs from the groups
      val ugList = userGroupService.getOwcContextsRightsMatrixForUser(request.user)

      Ok(Json.obj("status" -> "OK", "rights" -> JsArray(ugList.map(ug => Json.toJson(ug)))))
  }

  /**
    * get a specific group in the hope you are member
    *
    * @param id
    * @return
    */
  def findUsersOwnUserGroupsById(id: String): Action[Unit] = defaultAuthAction(parse.empty) {
    implicit request =>
      val ugList = userGroupService.findUsersOwnUserGroupsById(request.user, UUID.fromString(id))
      ugList.fold {
        Ok(Json.obj("status" -> "OK", "usergroups" -> JsArray(), "message" -> "not found"))
      } {
        ug => Ok(Json.obj("status" -> "OK", "usergroup" -> Json.toJson(ug)))
      }
  }

  /**
    * create a new user group. user must already be in the sent object rights list as power user
    *
    * @return
    */
  def createUsersOwnUserGroup: Action[JsValue] = defaultAuthAction(parse.json) {
    request =>
      request.body.validate[UserGroup].fold(
        errors => {
          logger.error(JsError.toJson(errors).toString())
          val error: ErrorResult = ErrorResult("Usergroup format could not be read.",
            Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
          BadRequest(Json.toJson(error)).as(JSON)
        },
        userGroup => {
          // check if creating user is in the member list and has power user right
          val isPresent = userGroup.hasUsersLevel.exists(ul => ul.users_accountsubject.equals(request.user.accountSubject) && ul.userlevel >= 2)
          if (!isPresent) {
            logger.error("User not listed with adequate rights. We would create a group that you can't edit, aborting.")
            val error = ErrorResult("User not listed with adequate rights. We would create a group that you can't edit, aborting.", None)
            BadRequest(Json.toJson(error)).as(JSON)
          } else {
            userGroupService.createUsersOwnUserGroup(request.user, userGroup).fold {
              logger.error("Error creating the user group.")
              val error = ErrorResult("Error creating the user group.", None)
              BadRequest(Json.toJson(error)).as(JSON)
            } {
              ugroup =>
                Ok(Json.obj("status" -> "OK", "usergroup" -> Json.toJson(ugroup)))
            }
          }
        })
  }

  /**
    * updates an exisiting user group. user must already be in the rights list of the database item as power user
    *
    * @return
    */
  def updateUsersOwnUserGroup: Action[JsValue] = defaultAuthAction(parse.json) {
    request =>
      request.body.validate[UserGroup].fold(
        errors => {
          logger.error(JsError.toJson(errors).toString())
          val error: ErrorResult = ErrorResult("Usergroup format could not be read.",
            Some(StringEscapeUtils.escapeJson(errors.mkString("; "))))
          BadRequest(Json.toJson(error)).as(JSON)
        },
        userGroup => {
          // check if creating user is in the member list and has power user rights from the database happens in underlying service
          // (so that you cannot smuggle yourself in)
          // however, as a poweruser you can remove yourself from the group or degrade your rights (subsequently you won't be able to edit anymore)
          userGroupService.updateUsersOwnUserGroup(request.user, userGroup).fold {
            logger.error("Error updating the user group.")
            val error = ErrorResult("Error updating the user group.", None)
            BadRequest(Json.toJson(error)).as(JSON)
          } {
            ugroup =>
              Ok(Json.obj("status" -> "OK", "usergroup" -> Json.toJson(ugroup)))
          }
        })
  }

  /**
    * delete a user group, user must be member with poweruser rights, checked in usergroupService
    *
    * @param id
    * @return
    */
  def deleteUsersOwnUserGroup(id: String): Action[Unit] = defaultAuthAction(parse.empty) {
    implicit request =>
      val ugDel = userGroupService.deleteUsersOwnUserGroup(request.user, UUID.fromString(id))
      if (ugDel) {
        Ok(Json.obj("status" -> "OK", "message" -> "usergroup deleted", "usergroup" -> id))
      } else {
        logger.error("Error deleting the user group.")
        val error = ErrorResult("Error deleting the user group.", None)
        BadRequest(Json.toJson(error)).as(JSON)
      }

  }

  /**
    *
    * @param accountSubject
    * @param email
    * @return
    */
  def resolveUserInfo(accountSubject: Option[String], email: Option[String]): Action[Unit] = defaultAuthAction(parse.empty) {
    implicit request =>
      val userOpt: Option[User] = accountSubject.flatMap(acc => userService.findUserByAccountSubject(acc))
      val emailUserOpt: Option[User] = email.flatMap(em => userService.findUserByEmailAsString(em))

      userOpt.orElse(emailUserOpt).map {
        user =>
          Ok(Json.obj("status" -> "OK", "user" -> user.asProfileJs))
      }.getOrElse {
        logger.error("Error retrieving a user.")
        val error = ErrorResult("Error retrieving a user.", Some("Not found"))
        BadRequest(Json.toJson(error)).as(JSON)
      }
  }

  def updateOwcContextVisibility(owcContextId: String, visibility: Int): Action[Unit] = defaultAuthAction(parse.empty) {
    request =>
      // does collection exist, user exists because must be authenticated
      val ugList = userGroupService.getOwcContextsRightsMatrixForUser(request.user)

      val areYouPowerUserForThisContext = ugList.filter(_.owcContextId.contentEquals(owcContextId)).exists(_.queryingUserAccessLevel >= 2)

      if (areYouPowerUserForThisContext) {
        val visOk = collectionsService.updateOwcContextVisibility(owcContextId, request.user, visibility)
        if (visOk) {
          Ok(Json.obj("status" -> "OK", "owcContext" -> owcContextId, "user" -> request.user.accountSubject, "visibility" -> visibility))
        } else {
          logger.error("Error changing the visibility of the context, database error.")
          val error = ErrorResult("Error changing the visibility of the context, database error.", None)
          BadRequest(Json.toJson(error)).as(JSON)
        }
      } else {
        logger.error("Error changing the visibility of the context, not allowed.")
        val error = ErrorResult("Error changing the visibility of the context, not allowed.", None)
        BadRequest(Json.toJson(error)).as(JSON)
      }
  }

}
