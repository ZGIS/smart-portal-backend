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
  * User Files cross ref
  */


/**
  *
  * @param uuid
  * @param users_accountsubject
  * @param originalfilename the name of the file when uploaded
  * @param linkreference    the full Google Cloud Bucket linkref
  * @param laststatustoken
  * @param laststatuschange
  */
case class UserFile(uuid: UUID,
                    users_accountsubject: String,
                    originalfilename: String,
                    linkreference: String,
                    laststatustoken: String,
                    laststatuschange: ZonedDateTime) extends ClassnameLogger

object UserFile extends ClassnameLogger {

  /*
CREATE TABLE userfiles (
  uuid varchar(255) NOT NULL,
  users_accountsubject varchar(255) REFERENCES users(accountsubject),
  originalfilename TEXT NOT NULL,
  linkreference TEXT NOT NULL,
  laststatustoken varchar(255) NOT NULL,
  laststatuschange TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (uuid)
);
 */

  val userFilesParser = {
    get[String]("uuid") ~
      get[String]("users_accountsubject") ~
      get[String]("originalfilename") ~
      get[String]("linkreference") ~
      get[String]("laststatustoken") ~
      get[ZonedDateTime]("laststatuschange") map {
      case uuid ~ users_accountsubject ~ originalfilename ~ linkreference ~ laststatustoken ~ laststatuschange =>
        UserFile(UUID.fromString(uuid), users_accountsubject, originalfilename, linkreference, laststatustoken, laststatuschange)
    }
  }

  def getAllUserFiles()(implicit connection: Connection): Seq[UserFile] = {
    SQL(s"select * from $table_userfiles").as(userFilesParser *)
  }

  def findUserFileByAccountSubject(users_accountsubject: String)(implicit connection: Connection): Seq[UserFile] = {
    SQL(s"select * from $table_userfiles where users_accountsubject = {users_accountsubject}").on(
      'users_accountsubject -> users_accountsubject
    ).as(userFilesParser *)
  }

  def findUserFileByUuid(uuid: UUID)(implicit connection: Connection): Option[UserFile] = {
    SQL(s"select * from $table_userfiles where uuid = {uuid}").on(
      'uuid -> uuid.toString
    ).as(userFilesParser.singleOpt)
  }

  def findUserFilesByLink(link: String)(implicit connection: Connection): Seq[UserFile] = {
    SQL(s"select * from $table_userfiles where linkreference LIKE '%{link}%' OR originalfilename LIKE '%{link}%'").on(
      'link -> link
    ).as(userFilesParser *)
  }

  def createUserFile(userFile: UserFile)(implicit connection: Connection): Option[UserFile] = {
    val nps = Seq[NamedParameter](// Tuples as NamedParameter
      "uuid" -> userFile.uuid.toString,
      "users_accountsubject" -> userFile.users_accountsubject,
      "originalfilename" -> userFile.originalfilename,
      "linkreference" -> userFile.linkreference,
      "laststatustoken" -> userFile.laststatustoken,
      "laststatuschange" -> userFile.laststatuschange)

    val rowCount = SQL(
      s"""
          insert into $table_userfiles values (
            {uuid}, {users_accountsubject}, {originalfilename}, {linkreference}, {laststatustoken}, {laststatuschange}
          )
        """).on(nps: _*).executeUpdate()

    rowCount match {
      case 1 => Some(userFile)
      case _ => None
    }
  }

  def updateUserFile(userFile: UserFile)(implicit connection: Connection): Option[UserFile] = ???

  def deleteUserFile(userFile: UserFile)(implicit connection: Connection): Boolean = {
    deleteUserFile(userFile.uuid)
  }

  def deleteUserFile(uuid: UUID)(implicit connection: Connection): Boolean = {
    val rowCount = SQL(s"delete from $table_userfiles where uuid = {uuid}").on(
      'uuid -> uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ => false
    }
  }

}