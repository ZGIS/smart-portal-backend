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

import anorm.SqlParser.get
import anorm.{NamedParameter, SQL, ~}
import utils.ClassnameLogger

/*
CREATE TABLE sessions (
token varchar(255) NOT NULL,
useragent varchar(255) NOT NULL,
email varchar(255) NOT NULL,
laststatustoken varchar(255) NOT NULL,
laststatuschange TIMESTAMP WITH TIME ZONE NOT NULL,
PRIMARY KEY (token)
);
*/

/**
  * we move the sessions into DB
  *
  * @param token the security token for an active session
  * @param useragent informative, user for hashing and separating logins from different devices
  * @param email
  * @param laststatustoken session active/expire/deactivate
  * @param laststatuschange
  */
case class UserSession(token: String,
                       useragent: String,
                       email: String,
                       laststatustoken: String,
                       laststatuschange: ZonedDateTime) extends ClassnameLogger

object UserSession extends ClassnameLogger {

  val userSessionsParser = {
    get[String]("token") ~
      get[String]("useragent") ~
      get[String]("email") ~
      get[String]("laststatustoken") ~
      get[ZonedDateTime]("laststatuschange") map {
      case token ~ useragent ~ email ~ laststatustoken ~ laststatuschange =>
        UserSession(token, useragent, email, laststatustoken, laststatuschange)
    }
  }

  def getAllUserSessions(max: Int)(implicit connection: Connection): Seq[UserSession] = {
    SQL(s"select * from $table_sessions order by laststatuschange desc limit $max").as(userSessionsParser *)
  }

  def findUserSessionByEmail(email: String, max: Int)(implicit connection: Connection): Seq[UserSession] = {
    SQL(s"select * from $table_sessions where email = {email} order by laststatuschange desc limit $max").on(
      'email -> email
    ).as(userSessionsParser *)
  }

  def findUserSessionByToken(token: String, max: Int)(implicit connection: Connection): Option[UserSession] = {
    SQL(s"select * from $table_sessions where token = {token} order by laststatuschange desc limit $max").on(
      'token -> token
    ).as(userSessionsParser.singleOpt)
  }

  def createUserSession(userSession: UserSession)(implicit connection: Connection): Option[UserSession] = {
    val nps = Seq[NamedParameter](// Tuples as NamedParameter
      "token" -> userSession.token,
      "useragent" -> userSession.useragent,
      "email" -> userSession.email,
      "laststatustoken" -> userSession.laststatustoken,
      "laststatuschange" -> userSession.laststatuschange)

    val rowCount = SQL(
      s"""
          insert into $table_sessions values (
            {token}, {useragent}, {email}, {laststatustoken}, {laststatuschange}
          )
        """).on(nps: _*).executeUpdate()

    rowCount match {
      case 1 => Some(userSession)
      case _ => None
    }
  }

  def updateUserSessionStatus(userSession: UserSession)(implicit connection: Connection): Option[UserSession] = {
    val rowCount = SQL(
      s"""
          update $table_sessions set
            laststatustoken = {laststatustoken},
            laststatuschange = {laststatuschange} where token = {token}
        """).on(
      'laststatustoken -> userSession.laststatustoken,
      'laststatuschange -> userSession.laststatuschange,
      'token -> userSession.token
    ).executeUpdate()

    rowCount match {
      case 1 => Some(userSession)
      case _ => None
    }
  }

  def deleteUserSessionByEmail(email: String)(implicit connection: Connection): Boolean = {
    val rowCount = SQL(s"delete from $table_sessions where email = {email}").on(
      'email -> email
    ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ => false
    }
  }

  def deleteUserSessionByToken(token: String)(implicit connection: Connection): Boolean = {
    val rowCount = SQL(s"delete from $table_sessions where token = {token}").on(
      'token -> token
    ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ => false
    }
  }
}