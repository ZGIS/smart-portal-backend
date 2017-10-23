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

import models.ErrorResult
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
