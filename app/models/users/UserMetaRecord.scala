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
import anorm.{NamedParameter, SQL, ~}
import utils.ClassnameLogger

/**
  * User Files and Metadata Records cross ref
  */

/**
  *
  * @param uuid
  * @param users_accountsubject
  * @param originaluuid the UUID when inserted in the metadata record at creation time
  * @param cswreference the get record link to the csw server
  * @param laststatustoken
  * @param laststatuschange
  */
final case class UserMetaRecord(uuid: UUID,
                          users_accountsubject: String,
                          originaluuid: String,
                          cswreference: String,
                          laststatustoken: String,
                          laststatuschange: ZonedDateTime) extends ClassnameLogger

object UserMetaRecord extends ClassnameLogger {

  /*
CREATE TABLE usermetarecords (
  uuid varchar(255) NOT NULL,
  users_accountsubject varchar(255) REFERENCES users(accountsubject),
  originaluuid TEXT NOT NULL,
  cswreference TEXT NOT NULL,
  laststatustoken varchar(255) NOT NULL,
  laststatuschange TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (uuid)
);
*/


  val userMetaRecordsParser = {
    get[String]("uuid") ~
      get[String]("users_accountsubject") ~
      get[String]("originaluuid") ~
      get[String]("cswreference") ~
      get[String]("laststatustoken") ~
      get[ZonedDateTime]("laststatuschange") map {
      case uuid ~ users_accountsubject ~ originaluuid ~ cswreference ~ laststatustoken ~ laststatuschange =>
        UserMetaRecord(UUID.fromString(uuid), users_accountsubject, originaluuid, cswreference, laststatustoken, laststatuschange)
    }
  }

  def getAllUserMetaRecords()(implicit connection: Connection): Seq[UserMetaRecord] = {
    SQL(s"select * from $table_usermetarecords").as(userMetaRecordsParser *)
  }

  def findUserMetaRecordByAccountSubject(users_accountsubject: String)(implicit connection: Connection): Seq[UserMetaRecord] = {
    SQL(s"select * from $table_usermetarecords where users_accountsubject = {users_accountsubject}").on(
      'users_accountsubject -> users_accountsubject
    ).as(userMetaRecordsParser *)
  }

  def findUserMetaRecordByUuid(uuid: UUID)(implicit connection: Connection): Option[UserMetaRecord] = {
    SQL(s"select * from $table_usermetarecords where uuid = {uuid}").on(
      'uuid -> uuid.toString
    ).as(userMetaRecordsParser.singleOpt)
  }

  def findUserMetaRecordByXmlMetaOriginalUuid(uuid: UUID)(implicit connection: Connection): Option[UserMetaRecord] = {
    SQL(s"select * from $table_usermetarecords where originaluuid = {uuid}").on(
      'uuid -> uuid.toString
    ).as(userMetaRecordsParser.singleOpt)
  }

  def createUserMetaRecord(userMetaRecord: UserMetaRecord)(implicit connection: Connection): Option[UserMetaRecord] = {
    val nps = Seq[NamedParameter](// Tuples as NamedParameter
      "uuid" -> userMetaRecord.uuid.toString,
      "users_accountsubject" -> userMetaRecord.users_accountsubject,
      "originaluuid" -> userMetaRecord.originaluuid,
      "cswreference" -> userMetaRecord.cswreference,
      "laststatustoken" -> userMetaRecord.laststatustoken,
      "laststatuschange" -> userMetaRecord.laststatuschange)

    val rowCount = SQL(
      s"""
          insert into $table_usermetarecords values (
            {uuid}, {users_accountsubject}, {originaluuid}, {cswreference}, {laststatustoken}, {laststatuschange}
          )
        """).on(nps: _*).executeUpdate()

    rowCount match {
      case 1 => Some(userMetaRecord)
      case _ => None
    }
  }

  def updateUserMetaRecord(userMetaRecord: UserMetaRecord)(implicit connection: Connection): Option[UserMetaRecord] = ???

  def deleteUserMetaRecord(userMetaRecord: UserMetaRecord)(implicit connection: Connection): Boolean = {
    deleteUserMetaRecord(userMetaRecord.uuid)
  }

  def deleteUserMetaRecord(uuid: UUID)(implicit connection: Connection): Boolean = {
    val rowCount = SQL(s"delete from $table_usermetarecords where uuid = {uuid}").on(
      'uuid -> uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ => false
    }
  }



}