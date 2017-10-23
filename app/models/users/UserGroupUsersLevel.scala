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
import java.util.UUID

import anorm.SqlParser.get
import anorm.{SQL, ~}
import utils.ClassnameLogger

/**
  * UserGroup Users Level object (members and their rights level)
  * @param usergroups_uuid
  * @param users_accountsubject
  * @param userlevel 0 = normal group member, 1 = power-user ? is that enough (3 overlord admin?)
  */
case class UserGroupUsersLevel(usergroups_uuid: UUID,
                               users_accountsubject: String,
                               userlevel: Int) extends ClassnameLogger {

}

object UserGroupUsersLevel extends ClassnameLogger {

  private val groupsUsersParser = {
    get[String]("usergroups_uuid") ~
      get[String]("users_accountsubject") ~
      get[Int]("userlevel") map {
      case usergroups_uuid ~ users_accountsubject ~ userlevel =>
        UserGroupUsersLevel(UUID.fromString(usergroups_uuid), users_accountsubject, userlevel)
    }
  }

  def createUserGroupUsersLevel(userGroupUsersLevel: UserGroupUsersLevel)
                               (implicit connection: Connection): Option[UserGroupUsersLevel] = {
    val rowCount = SQL(
      s"""
          insert into $table_groups_users values (
            {usergroups_uuid}, {users_accountsubject}, {userlevel}
          )
        """).on(
      'usergroups_uuid -> userGroupUsersLevel.usergroups_uuid.toString,
      'users_accountsubject -> userGroupUsersLevel.users_accountsubject,
      'userlevel -> userGroupUsersLevel.userlevel
    ).executeUpdate()

    rowCount match {
      case 1 => Some(userGroupUsersLevel)
      case _ => logger.error("userGroupUsersLevel couldn't be created")
        None
    }
  }

  /**
    * Update single UserGroupUsersLevel
    *
    * @param userGroupUsersLevel
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def updateUserGroupUsersLevel(userGroupUsersLevel: UserGroupUsersLevel)
                               (implicit connection: Connection): Option[UserGroupUsersLevel] = {
    val rowCount = SQL(
      s"""
          update $table_groups_users set
            userlevel = {userlevel}
            where usergroups_uuid = {usergroups_uuid} and users_accountsubject = {users_accountsubject}
        """).on(
      'userlevel -> userGroupUsersLevel.userlevel,
      'users_accountsubject -> userGroupUsersLevel.users_accountsubject,
      'usergroups_uuid -> userGroupUsersLevel.usergroups_uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => Some(userGroupUsersLevel)
      case _ => logger.error(s"userGroupUsersLevel ${userGroupUsersLevel.toString} " +
        "couldn't be updated")
        None
    }
  }


  /**
    * delete an UserGroupUsersLevel
    *
    * @param userGroupUsersLevel
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteUserGroupUsersLevel(userGroupUsersLevel: UserGroupUsersLevel)(implicit connection: Connection): Boolean = {

    val rowCount = SQL(s"delete from $table_groups_users where usergroups_uuid = {usergroups_uuid} and users_accountsubject = {users_accountsubject}").on(
      'users_accountsubject -> userGroupUsersLevel.users_accountsubject,
      'usergroups_uuid -> userGroupUsersLevel.usergroups_uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ =>
        logger.error(s"userGroupUsersLevel ${userGroupUsersLevel.toString} " +
          "couldn't be deleted")
        false
    }
  }

  def findUserLevelsForGroup(usergroups_uuid: String)(implicit connection: Connection): Seq[UserGroupUsersLevel] = {
    SQL(s"""select * from $table_groups_users where usergroups_uuid = '$usergroups_uuid'""").as(groupsUsersParser *)
  }

  def findUserLevelForGroupAndUser(usergroups_uuid: String, users_accountsubject: String)(implicit connection: Connection): Option[UserGroupUsersLevel] = {
    SQL(s"""select * from $table_groups_users where usergroups_uuid = {usergroups_uuid} and users_accountsubject = {users_accountsubject}""").on(
      'users_accountsubject -> users_accountsubject,
      'usergroups_uuid -> usergroups_uuid).as(groupsUsersParser.singleOpt)
  }

}
