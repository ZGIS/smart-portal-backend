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

case class UserGroupContextsVisibility(usergroups_uuid: UUID,
                                       owc_context_id: String,
                                       visibility: Int) extends ClassnameLogger {

}

object UserGroupContextsVisibility extends ClassnameLogger {

  private val groupsContextParser = {
    get[String]("usergroups_uuid") ~
      get[String]("owc_context_id") ~
      get[Int]("visibility") map {
      case usergroups_uuid ~ owc_context_id ~ visibility =>
        UserGroupContextsVisibility(UUID.fromString(usergroups_uuid), owc_context_id, visibility)
    }
  }

  def createUserGroupContextsVisibility(userGroupContextsVisibility: UserGroupContextsVisibility)
                                       (implicit connection: Connection): Option[UserGroupContextsVisibility] = {
    val rowCount = SQL(
      s"""
          insert into $table_groups_context values (
            {usergroups_uuid}, {owc_context_id}, {visibility}
          )
        """).on(
      'usergroups_uuid -> userGroupContextsVisibility.usergroups_uuid.toString,
      'owc_context_id -> userGroupContextsVisibility.owc_context_id,
      'visibility -> userGroupContextsVisibility.visibility
    ).executeUpdate()

    rowCount match {
      case 1 => Some(userGroupContextsVisibility)
      case _ => logger.error("userGroupContextsVisibility couldn't be created")
        None
    }
  }

  /**
    * Update single UserGroupContextsVisibility
    *
    * @param userGroupContextsVisibility
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def updateUserGroupContextsVisibility(userGroupContextsVisibility: UserGroupContextsVisibility)
                                       (implicit connection: Connection): Option[UserGroupContextsVisibility] = {
    val rowCount = SQL(
      s"""
          update $table_groups_context set
            visibility = {visibility},
            where usergroups_uuid = {usergroups_uuid} and owc_context_id = {owc_context_id}
        """).on(
      'visibility -> userGroupContextsVisibility.visibility,
      'owc_context_id -> userGroupContextsVisibility.owc_context_id,
      'usergroups_uuid -> userGroupContextsVisibility.usergroups_uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => Some(userGroupContextsVisibility)
      case _ => logger.error(s"userGroupContextsVisibility ${userGroupContextsVisibility.toString} " +
        "couldn't be updated")
        None
    }
  }


  /**
    * delete an UserGroupContextsVisibility
    *
    * @param userGroupContextsVisibility
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteUserGroupContextsVisibility(userGroupContextsVisibility: UserGroupContextsVisibility)(implicit connection: Connection): Boolean = {
    val rowCount = SQL(s"delete from $table_groups_context where usergroups_uuid = {usergroups_uuid} and owc_context_id = {owc_context_id}").on(
      'owc_context_id -> userGroupContextsVisibility.owc_context_id,
      'usergroups_uuid -> userGroupContextsVisibility.usergroups_uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ =>
        logger.error(s"userGroupContextsVisibility ${userGroupContextsVisibility.toString} " +
          "couldn't be deleted")
        false
    }
  }

  def findContextsVisibilitiesForGroup(groupUuid: String)(implicit connection: Connection): Seq[UserGroupContextsVisibility] = {
    SQL(s"""select * from $table_groups_context where usergroups_uuid = '$groupUuid'""").as(groupsContextParser *)
  }

  def findContextVisibilityForGroupAndUser(usergroups_uuid: String, owc_context_id: String)
                                         (implicit connection: Connection): Option[UserGroupContextsVisibility] = {
    SQL(s"""select * from $table_groups_context where usergroups_uuid = {usergroups_uuid} and owc_context_id = {owc_context_id}""").on(
      'owc_context_id -> owc_context_id,
      'usergroups_uuid -> usergroups_uuid).as(groupsContextParser.singleOpt)
  }
}
