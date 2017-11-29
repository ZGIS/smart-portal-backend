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

package services


import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID
import javax.inject._

import com.google.cloud.storage.Blob
import controllers.ProfileJs
import models.db.DatabaseSessionHolder
import models.users._
import play.api.Configuration
import play.api.cache.CacheApi
import utils.{ClassnameLogger, PasswordHashing}

@Singleton
class AdminService @Inject()(dbSession: DatabaseSessionHolder,
                             val passwordHashing: PasswordHashing,
                             cache: CacheApi,
                             configuration: Configuration) extends ClassnameLogger {

  lazy private val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")

  /**
    * check if email is allowed in configured admin list
    *
    * @param email
    * @return
    */
  def isAdmin(email: String): Boolean = {
    configuration.getStringList("smart.admin.emails").fold(false)(
      adminList => if (adminList.contains(email)) true else false)
  }

  /**
    * list all users, of course (also for admin) must not see password
    *
    * @return
    */
  def getallUsers: Seq[User] = {
    dbSession.viaConnection( implicit connection => {
      UserDAO.getAllUsers
    })
  }

  /**
    * list all active sessions
    *
    * @return
    */
  def getActiveSessions(max: Option[Int]): Seq[UserSession] = {
    dbSession.viaConnection( implicit connection => {
      val count = max.getOrElse(100)
      UserSession.getAllUserSessions(count)
    })
  }

  /**
    * remove one active session for one specific user
    *
    * @param token
    * @param email
    * @return
    */
  def removeActiveSessions(token: String, email: String): Boolean = {
    dbSession.viaTransaction( implicit connection => {
      val viaToken = UserSession.findUserSessionByToken(token, 1)
      viaToken.exists{sess =>
        if (sess.email.equals(email)) {
          UserSession.deleteUserSessionByToken(sess.token)
        } else {
          false
        }
      }
    })
  }

  /**
    * list all active sessions
    *
    * @return
    */
  def queryActiveSessions(token: Option[String], max: Option[Int], email: Option[String]): Seq[UserSession] = {
    dbSession.viaConnection{ implicit connection =>

      val count = max.getOrElse(100)
      (token, email) match {
        case (Some(to), Some(em)) =>
          val viaToken = UserSession.findUserSessionByToken(to, count).toSeq
          val viaEmail = UserSession.findUserSessionByEmail(em, count)
          (viaToken ++ viaEmail).filter(ul => ul.token.contains(to) && ul.email.contains(em))
        case (Some(to), None) =>
          UserSession.findUserSessionByToken(to, count).toSeq
        case (None, Some(em)) =>
          UserSession.findUserSessionByEmail(em, count)
        case (None, None) =>
          UserSession.getAllUserSessions(count)
      }
    }
  }

  /**
    * block and Unblock Users by setting status token
    * @param command
    * @param email
    * @return
    */
  def blockUnblockUsers(command: String, email: String): Option[User] = {
    val userOpt = dbSession.viaConnection( implicit connection => {
      UserDAO.findUserByEmailAsString(email)
    })
    userOpt.fold[Option[User]]{
      None
    } {
      user =>
        val statusToken = if (command.equalsIgnoreCase("BLOCK")) StatusToken.BLOCKED else StatusToken.ACTIVE
        val updateUser = User(
          user.email,
          user.accountSubject,
          user.firstname,
          user.lastname,
          "***",
          s"${statusToken.value}:BY-ADMIN",
          ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

        // remove any latent active sessions
        val sessions = dbSession.viaConnection { implicit connection =>
          UserSession.deleteUserSessionByEmail(user.email.value)
        }
        if (!sessions) {
          logger.warn(s"There was a not completely successful sessions delete for ${user.email.value} when setting status to ${statusToken.value}?")
        }

        // result of Option can be evaluated upstream in controller
        dbSession.viaTransaction { implicit connection =>
          UserDAO.updateNoPass(updateUser)
        }

    }
  }

  /**
    * list all files uploaded by users (for admin)
    *
    * @return
    */
  def getallUserFiles: Seq[UserFile] = {
    dbSession.viaConnection( implicit connection => {
      UserFile.getAllUserFiles
    })
  }

  /**
    * list all csw meta entries uploaded by users (for admin)
    *
    * @return
    */
  def getallUserMetaRecords: Seq[UserMetaRecord] = {
    dbSession.viaConnection( implicit connection => {
      UserMetaRecord.getAllUserMetaRecords
    })
  }

  /**
    * list all file request loggings (for admin)
    *
    * @return
    */
  def getAllUserLinkLoggings(max: Option[Int]): Seq[UserLinkLogging] = {
    dbSession.viaConnection( implicit connection => {
      val count = max.getOrElse(100)
      UserLinkLogging.getAllUserLinkLoggings(count)
    })
  }

  /**
    * find file request loggings (for admin)
    *
    * @return
    */
  def queryUserLinkLoggings(link: Option[String], max: Option[Int], email: Option[String]): Seq[UserLinkLogging] = {
    dbSession.viaConnection{ implicit connection =>

      val count = max.getOrElse(100)
      (link, email) match {
        case (Some(li), Some(em)) =>
          val viaLinks = UserLinkLogging.findUserLinkLoggingsByLink(li, count)
          val viaEmail = UserLinkLogging.findUserLinkLoggingByEmail(em, count)
          (viaLinks ++ viaLinks).filter(ul => ul.link.contains(li) && ul.email.contains(em))
        case (Some(li), None) =>
          UserLinkLogging.findUserLinkLoggingsByLink(li, count)
        case (None, Some(em)) =>
          UserLinkLogging.findUserLinkLoggingByEmail(em, count)
        case (None, None) =>
          UserLinkLogging.getAllUserLinkLoggings(count)
      }
    }
  }

  /**
    * The first 5 fuctions handle user groups overall,
    * user groups have users with certain level of right,
    * and related owc contexts with a certain level of visibility and regulation to be edited
    *
    * the second row (3) functions allow access/modification of user rights within a group
    *
    * and third row (3) of functions expose owccontext regultation level
    *
    * However, access is unified via UserGroup object which has cascading awareness of
    * its user rigths and owccontext visibilty dependents
    *
    */

  def getAllUserGroups: Seq[UserGroup] = {
    dbSession.viaTransaction(implicit connection => {
      UserGroup.getAllUserGroups
    })
  }

  def findUserGroupsById(uuid: UUID): Option[UserGroup] = {
    dbSession.viaTransaction(implicit connection => {
      UserGroup.findUserGroupsById(uuid)
    })
  }

  def createUserGroup(userGroup: UserGroup): Option[UserGroup] = {
    dbSession.viaTransaction(implicit connection => {
      UserGroup.createUserGroup(userGroup: UserGroup)
    })
  }

  def updateUserGroup(userGroup: UserGroup): Option[UserGroup] = {
    dbSession.viaTransaction(implicit connection => {
      UserGroup.updateUserGroup(userGroup: UserGroup)
    })
  }

  def deleteUserGroup(userGroup: UserGroup): Boolean = {
    dbSession.viaTransaction(implicit connection => {
      UserGroup.deleteUserGroup(userGroup: UserGroup)
    })
  }

  /**
    * access to user rights via userGroupUsersLevels
    *
    */

  def createUserGroupUsersLevel(userGroupUsersLevel: UserGroupUsersLevel): Option[UserGroupUsersLevel] = {
    dbSession.viaTransaction(implicit connection => {
      UserGroupUsersLevel.createUserGroupUsersLevel(userGroupUsersLevel)
    })
  }

  def updateUserGroupUsersLevel(userGroupUsersLevel: UserGroupUsersLevel): Option[UserGroupUsersLevel] = {
    dbSession.viaTransaction(implicit connection => {
      UserGroupUsersLevel.updateUserGroupUsersLevel(userGroupUsersLevel)
    })
  }

  def deleteUserGroupUsersLevel(userGroupUsersLevel: UserGroupUsersLevel): Boolean = {
    dbSession.viaTransaction(implicit connection => {
      UserGroupUsersLevel.deleteUserGroupUsersLevel(userGroupUsersLevel)
    })
  }

  /**
    * access to user rights via userGroupUsersLevels
    *
    */

  def createUserGroupContextsVisibility(userGroupContextsVisibility: UserGroupContextsVisibility): Option[UserGroupContextsVisibility] = {
    dbSession.viaTransaction(implicit connection => {
      UserGroupContextsVisibility.createUserGroupContextsVisibility(userGroupContextsVisibility)
    })
  }

  def updateUserGroupContextsVisibility(userGroupContextsVisibility: UserGroupContextsVisibility): Option[UserGroupContextsVisibility] = {
    dbSession.viaTransaction(implicit connection => {
      UserGroupContextsVisibility.updateUserGroupContextsVisibility(userGroupContextsVisibility)
    })
  }

  def deleteUserGroupContextsVisibility(userGroupContextsVisibility: UserGroupContextsVisibility): Boolean = {
    dbSession.viaTransaction(implicit connection => {
      UserGroupContextsVisibility.deleteUserGroupContextsVisibility(userGroupContextsVisibility)
    })
  }

}
