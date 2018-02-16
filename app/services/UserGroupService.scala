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

import java.util.UUID
import javax.inject._

import models.db.DatabaseSessionHolder
import models.owc.OwcContextDAO
import models.users._
import play.api.Configuration
import utils.ClassnameLogger

@Singleton
class UserGroupService @Inject()(dbSession: DatabaseSessionHolder,
                                 configuration: Configuration) extends ClassnameLogger {

  lazy private val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")

  /**
    * check if user can edit this particular group
    *
    * @param user
    * @param userGroup
    * @return
    */
  def amIPowerUserForGroup(user: User, userGroup: UserGroup): Boolean = {
    dbSession.viaConnection(implicit connection => {
      UserGroup.findUserGroupsForUser(user)
        .find(ug => ug.uuid.equals(userGroup.uuid))
        .exists(ug => ug.hasUsersLevel.exists(ul => ul.users_accountsubject.equals(user.accountSubject) && ul.userlevel >= 1))
    })
  }

  /**
    * finds all groups a user is part of
    *
    * @param user
    * @return
    */
  def getUsersOwnUserGroups(user: User): Seq[UserGroup] = {
    dbSession.viaTransaction(implicit connection => {
      UserGroup.findUserGroupsForUser(user)
    })
  }

  /**
    * finds a group by its if the user is part of it
    *
    * @param user
    * @param uuid
    * @return
    */
  def findUsersOwnUserGroupsById(user: User, uuid: UUID): Option[UserGroup] = {
    dbSession.viaTransaction(implicit connection => {
      UserGroup.findUserGroupsForUser(user)
        .find(ug => ug.uuid.equals(uuid))
    })
  }

  /**
    * will create a new group, assumes that calling user is listed with rights
    *
    * @param user
    * @param userGroup
    * @return
    */
  def createUsersOwnUserGroup(user: User, userGroup: UserGroup): Option[UserGroup] = {
    dbSession.viaTransaction(implicit connection => {
      UserGroup.createUserGroup(userGroup: UserGroup)
    })
  }

  /**
    * will update a group if the calling user is poweruser
    *
    * @param user
    * @param userGroup
    * @return
    */
  def updateUsersOwnUserGroup(user: User, userGroup: UserGroup): Option[UserGroup] = {
    val edit = amIPowerUserForGroup(user, userGroup)
    if (edit) {
      dbSession.viaTransaction(implicit connection => {
        UserGroup.updateUserGroup(userGroup: UserGroup)
      })
    } else {
      logger.warn(s"user ${user.accountSubject} illegally attempted to edit group ${userGroup.uuid}")
      None
    }
  }

  /**
    * will delete a group if the calling user is poweruser
    *
    * @param user
    * @param uuid
    * @return
    */
  def deleteUsersOwnUserGroup(user: User, uuid: UUID): Boolean = {
    val userGroupOption = dbSession.viaConnection(implicit connection => UserGroup.findUserGroupsById(uuid))
    userGroupOption.exists {
      userGroup =>
        val edit = amIPowerUserForGroup(user, userGroup)
        if (edit) {
          dbSession.viaTransaction(implicit connection => {
            UserGroup.deleteUserGroup(userGroup: UserGroup)
          })
        } else {
          logger.warn(s"user ${user.accountSubject} illegally attempted to edit group ${userGroup.uuid}")
          false
        }
    }
  }

  def getOwcContextsRightsMatrixForUser(user: User): Seq[OwcContextsRightsMatrix] = {
    val userGroupsList = getUsersOwnUserGroups(user)

    val originalOwnContextsBriefTuple = dbSession.viaConnection(implicit connection => {
      OwcContextDAO.findOwcContextsByUserBrief(user)
    })

    val nativeRightsPerContextInGroups = dbSession.viaConnection(implicit connection => {
      userGroupsList.flatMap {
        g =>
          val viaGroup = g.name
          val ownLevel = g.hasUsersLevel.find(_.users_accountsubject.contentEquals(user.accountSubject)).map(_.userlevel).getOrElse(-1)

          val contextsRights: Seq[OwcContextsRightsMatrix] = g.hasOwcContextsVisibility.flatMap {
            o =>
              val groupContextsBriefTuples: Seq[UserHasOwcRightsNative] = OwcContextDAO.findOwcContextsByContextBrief(o.owc_context_id)

              val rightsBundle: Seq[OwcContextsRightsMatrix] = groupContextsBriefTuples.map {
                tup =>
                  OwcContextsRightsMatrix(
                    owcContextId = tup.owcContextId,
                    queryingUserAccountSubject = user.accountSubject,
                    origOwnerAccountSubject = tup.origOwnerAccountSubject,
                    viaGroups = Seq(viaGroup),
                    contextIntrinsicVisibility = tup.contextOwnersVisibility,
                    queryingUserAccessLevel = ownLevel)
              }
              rightsBundle
          }
          contextsRights
      }
    })

    nativeRightsPerContextInGroups
  }

}
