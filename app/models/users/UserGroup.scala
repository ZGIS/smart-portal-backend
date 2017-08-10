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

package models.users

import java.sql.Connection
import java.time.ZonedDateTime
import java.util.UUID

import anorm.SqlParser.get
import anorm.{RowParser, SQL, ~}
import info.smart.models.owc100.OwcContext
import models.owc.OwcContextDAO
import utils.ClassnameLogger

case class UserGroup(uuid: UUID,
                     name: String,
                     shortinfo: String,
                     laststatustoken: String,
                     laststatuschange: ZonedDateTime,
                     hasUsersLevel: List[UserGroupUsersLevel],
                     hasOwcContextsVisibility: List[UserGroupContextsVisibility]) extends ClassnameLogger {

}


object UserGroup extends ClassnameLogger {

  private def groupsParser(implicit connection: Connection): RowParser[UserGroup] = {

    get[String]("uuid") ~
      get[String]("name") ~
      get[String]("shortinfo") ~
      get[String]("laststatustoken") ~
      get[ZonedDateTime]("laststatuschange") map {
      case uuidstring ~ name ~ shortinfo ~ laststatustoken ~ laststatuschange =>
        UserGroup(uuid = UUID.fromString(uuidstring),
          name = name,
          shortinfo = shortinfo,
          laststatustoken = laststatustoken,
          laststatuschange = laststatuschange,
          hasUsersLevel = UserGroupUsersLevel.findUserLevelsForGroup(uuidstring).toList,
          hasOwcContextsVisibility = UserGroupContextsVisibility.findContextsVisibilitiesForGroup(uuidstring).toList)
    }
  }

  private def preCreateCheckUsers(users: List[UserGroupUsersLevel])(implicit connection: Connection): Boolean = {

    if (users.nonEmpty) {
      val userTest = users.map(gl => UserDAO.findByAccountSubject(gl.users_accountsubject)).count(_.isDefined)
      if (userTest != users.size) {
        logger.error(s"(createUserGroup/users) user with accountSubject one of: ${users.map(_.users_accountsubject).mkString(":")} does not exist, abort")
        false
      } else {
        true
      }
    } else {
      true
    }
  }

  private def preCreateCheckContexts(contexts: List[UserGroupContextsVisibility])(implicit connection: Connection): Boolean = {

    if (contexts.nonEmpty) {
      val contextsTest = contexts.map(cv => OwcContextDAO.findOwcContextsById(cv.owc_context_id)).count(_.isDefined)
      if (contextsTest != contexts.size) {
        logger.error(s"(createUserGroup/contexts) user with contextsId one of: ${contexts.map(_.owc_context_id).mkString(":")} does not exist, abort")
        false
      } else {
        true
      }
    } else {
      true
    }
  }

  def createUserGroup(userGroup: UserGroup)(implicit connection: Connection): Option[UserGroup] = {

    if (preCreateCheckUsers(userGroup.hasUsersLevel) && preCreateCheckContexts(userGroup.hasOwcContextsVisibility)) {

      val rowCountUserGroupInsert = SQL(
        s"""
            insert into $table_groups values (
              {uuid}, {name}, {shortinfo}, {laststatustoken}, {laststatuschange}
            )
          """).on(
        'uuid -> userGroup.uuid.toString,
        'name -> userGroup.name,
        'shortinfo -> userGroup.shortinfo,
        'laststatustoken -> userGroup.laststatustoken,
        'laststatuschange -> userGroup.laststatuschange
      ).executeUpdate()

      val usersDepsInsert = userGroup.hasUsersLevel.map (gl =>
          UserGroupUsersLevel.createUserGroupUsersLevel(gl)).count(_.isDefined)

      val contextDepsInsert = userGroup.hasOwcContextsVisibility.map(cv =>
        UserGroupContextsVisibility.createUserGroupContextsVisibility(cv)).count(_.isDefined)

      val allInserts = rowCountUserGroupInsert + usersDepsInsert + contextDepsInsert
      val testValue = 1 + userGroup.hasUsersLevel.size + userGroup.hasOwcContextsVisibility.size

      if (allInserts == testValue) {
        Some(userGroup)
      } else {
        logger.error("userGroup/create: one of " +
          s"rowCountUserGroupInsert: $rowCountUserGroupInsert (should be 1) + " +
          s"usersDepsInsert: $usersDepsInsert (${userGroup.hasUsersLevel.size}) + " +
          s"contextDepsInsert: $contextDepsInsert (${userGroup.hasOwcContextsVisibility.size}) failed")
        logger.error("userGroup couldn't be created")
        connection.rollback()
        None
      }
    } else {
      logger.error("userGroup/create was not successful")
      logger.error("Precondition failed, won't create userGroup")
      connection.rollback()
      None
    }
  }


  def preUpdateCheckUserGroupLevels(userGroup: UserGroup)(implicit connection: Connection): Boolean = {
    // get user rules for group current list,
    val current: List[String] = userGroup.hasUsersLevel.map(_.users_accountsubject).toList

    // get user rules for group old list,
    val oldUserGroup = findUserGroupsById(userGroup.uuid)
    val old: List[String] = oldUserGroup.map(gl => gl.hasUsersLevel.map(_.users_accountsubject)).getOrElse(List())

    // in old but not current -> delete user rules for this account subject (and current usergroup uuid)
    val toBeDeleted = old.diff(current)

    val deletedCount = oldUserGroup.map { ogl =>
      ogl.hasUsersLevel.filter(gl => toBeDeleted.contains(gl.users_accountsubject))
      .map { gl =>
        UserGroupUsersLevel.deleteUserGroupUsersLevel(gl)
      }.count(_ == true)}.getOrElse(0)

    val deleted: Boolean = deletedCount == toBeDeleted.length

    // in both lists -> update
    val toBeUpdated = current.intersect(old)

    val updated = userGroup.hasUsersLevel.filter(gl => toBeUpdated.contains(gl.users_accountsubject))
      .map(gl => UserGroupUsersLevel.updateUserGroupUsersLevel(gl))
      .count(_.isDefined) == toBeUpdated.length

    // in current but not in old -> insert
    val toBeInserted = current.diff(old)

    val inserted = userGroup.hasUsersLevel.filter(gl => toBeInserted.contains(gl.users_accountsubject))
      .map { userGroupUsersLevel =>
        // create UserGroupUsersLevel relation
        UserGroupUsersLevel.createUserGroupUsersLevel(userGroupUsersLevel)
      }
      .count(_.isDefined) == toBeInserted.length

    if (deleted && updated && inserted) {
      true
    } else {
      logger.error(s"(updateUserGroup/userGroupUsersLevel) one of deleted: $deleted , updated: $updated , inserted: $inserted not complete, recommend abort")
      false
    }
  }

  private def preUpdateCheckContextsVisibilities(userGroup: UserGroup)(implicit connection: Connection): Boolean = {
    // get user rules for group current list,
    val current: List[String] = userGroup.hasOwcContextsVisibility.map(_.owc_context_id).toList

    // get user rules for group old list,
    val oldUserGroup = findUserGroupsById(userGroup.uuid)
    val old: List[String] = oldUserGroup.map(cv => cv.hasOwcContextsVisibility.map(_.owc_context_id)).getOrElse(List())

    // in old but not current -> delete user rules for this account subject (and current usergroup uuid)
    val toBeDeleted = old.diff(current)
    val deletedCount = oldUserGroup.map { ocv =>
      ocv.hasOwcContextsVisibility.filter(cv => toBeDeleted.contains(cv.owc_context_id))
        .map { userGroupContextsVisibility =>
          // delete UserGroupContextsVisibility relation
          UserGroupContextsVisibility.deleteUserGroupContextsVisibility(userGroupContextsVisibility)
        }.count(_ == true)}.getOrElse(0)
    val deleted: Boolean = deletedCount == toBeDeleted.length

    // in both lists -> update
    val toBeUpdated = current.intersect(old)
    val updated = userGroup.hasOwcContextsVisibility.filter(cv => toBeUpdated.contains(cv.owc_context_id))
      .map(cv => UserGroupContextsVisibility.updateUserGroupContextsVisibility(cv))
      .count(_.isDefined) == toBeUpdated.length

    // in current but not in old -> insert
    val toBeInserted = current.diff(old)
    val inserted = userGroup.hasOwcContextsVisibility.filter(cv => toBeInserted.contains(cv.owc_context_id))
      .map { userGroupContextsVisibility =>
        // delete UserGroupContextsVisibility relation
        UserGroupContextsVisibility.createUserGroupContextsVisibility(userGroupContextsVisibility)
      }
      .count(_.isDefined) == toBeInserted.length

    if (deleted && updated && inserted) {
      true
    } else {
      logger.error(s"(updateUserGroup/userGroupContextsVisibility) one of deleted: $deleted , updated: $updated , inserted: $inserted not complete, recommend abort")
      false
    }
  }

  def updateUserGroup(userGroup: UserGroup)(implicit connection: Connection): Option[UserGroup] = {

    if (preUpdateCheckUserGroupLevels(userGroup) && preUpdateCheckContextsVisibilities(userGroup)) {

      val rowCount = SQL(
        s"""
            update $table_groups set
              name = {name},
              shortinfo = {shortinfo},
              laststatustoken = {laststatustoken},
              laststatuschange = {laststatuschange} where uuid = {uuid}
          """).on(
        'uuid -> userGroup.uuid.toString,
        'name -> userGroup.name,
        'shortinfo -> userGroup.shortinfo,
        'laststatustoken -> userGroup.laststatustoken,
        'laststatuschange -> userGroup.laststatuschange
      ).executeUpdate()

      rowCount match {
        case 1 => Some(userGroup)
        case _ => logger.error(s"UserGroup couldn't be updated, error $rowCount")
          // we need to think where to place rollback most appropriately
          connection.rollback()
          None
      }
    } else {
      logger.error("UserGroup couldn't be updated because of failed precondition")
      // we need to think where to place rollback most appropriately
      connection.rollback()
      None
    }
  }

  def deleteUserGroup(userGroup: UserGroup)(implicit connection: Connection): Boolean = {

    val rowCountUsers = userGroup.hasUsersLevel.map(gl =>
      UserGroupUsersLevel.deleteUserGroupUsersLevel(gl)).count(_ == true)

    val rowCountContexts = userGroup.hasOwcContextsVisibility.map(cv =>
      UserGroupContextsVisibility.deleteUserGroupContextsVisibility(cv)).count(_ == true)

    val rowCountDeleteGroup = SQL(s"delete from $table_groups where uuid = {uuid}").on(
      'uuid -> userGroup.uuid.toString
    ).executeUpdate()

    val allDeletes = rowCountUsers + rowCountContexts + rowCountDeleteGroup
    val testValue = userGroup.hasUsersLevel.size + userGroup.hasOwcContextsVisibility.size + 1

    if (allDeletes == testValue) {
      true
    } else {
      logger.error("UserGroup/delete: one of " +
        s"rowCountUsers: $rowCountUsers (should be ${userGroup.hasUsersLevel.size}) + " +
        s"rowCountContexts: $rowCountContexts  (${userGroup.hasOwcContextsVisibility.size}) " +
        s"rowCountDeleteGroup: $rowCountDeleteGroup (1) failed")
      logger.error("UserGroup couldn't be deleted")
      connection.rollback()
      false
    }
  }

  def findUserGroupsById(uuid: UUID)(implicit connection: Connection): Option[UserGroup] = {
    SQL(s"""select * from $table_groups where uuid = '${uuid.toString}'""").as(groupsParser.singleOpt)
  }

  def findUserGroupsForUser(user: User)(implicit connection: Connection): List[UserGroup] = {
    SQL(s"""select * from $table_groups""").as(groupsParser *).filter(group =>
      group.hasUsersLevel.exists(gl =>
        gl.users_accountsubject.equalsIgnoreCase(user.accountSubject)
      )
    )
  }

  def findUserGroupsForContext(owcContext: OwcContext)(implicit connection: Connection): List[UserGroup] = {
    SQL(s"""select * from $table_groups""").as(groupsParser *).filter(group =>
      group.hasOwcContextsVisibility.exists(cv =>
        cv.owc_context_id.equalsIgnoreCase(owcContext.id.toString)
      )
    )
  }

}

