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
CREATE TABLE consentlogging (
id BIGINT SERIAL NOT NULL ,
timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
ipaddress varchar(255),
useragent varchar(255),
email varchar(255),
accountsubject varchar(255),
link TEXT,
referrer TEXT,
PRIMARY KEY (id)
);
*/

/**
  *
  * @param id
  * @param timestamp
  * @param ipaddress
  * @param useragent
  * @param email
  * @param link
  * @param referer
  */
case class UserLinkLogging(id: Option[Long],
                           timestamp: ZonedDateTime,
                           ipaddress: Option[String],
                           useragent: Option[String],
                           email: Option[String],
                           link: String,
                           referer: Option[String]) extends ClassnameLogger

object UserLinkLogging extends ClassnameLogger {

  val userLinkLoggingParser = {
    get[Long]("id") ~
      get[ZonedDateTime]("timestamp") ~
      get[Option[String]]("ipaddress") ~
      get[Option[String]]("useragent") ~
      get[Option[String]]("email") ~
      get[String]("link") ~
      get[Option[String]]("referer") map {
      case id ~ timestamp ~ ipaddress ~ useragent ~ email ~ link ~ referer =>
        UserLinkLogging(Some(id), timestamp, ipaddress, useragent, email, link, referer)
    }
  }

  def getAllUserLinkLoggings(max: Int)(implicit connection: Connection): Seq[UserLinkLogging] = {
    SQL(s"select * from $table_consentlogging order by timestamp desc limit $max").as(userLinkLoggingParser *)
  }

  def findUserLinkLoggingByEmail(email: String, max: Int)(implicit connection: Connection): Seq[UserLinkLogging] = {
    SQL(s"select * from $table_consentlogging where email = {email} order by timestamp desc limit $max").on(
      'email -> email
    ).as(userLinkLoggingParser *)
  }

  def findUserLinkLoggingById(id: Long, max: Int)(implicit connection: Connection): Option[UserLinkLogging] = {
    SQL(s"select * from $table_consentlogging where id = {id} order by timestamp desc limit $max").on(
      'id -> id
    ).as(userLinkLoggingParser.singleOpt)
  }

  def findUserLinkLoggingsByLink(link: String, max: Int)(implicit connection: Connection): Seq[UserLinkLogging] = {

    SQL(s"select * from $table_consentlogging where link LIKE '%{link}%' order by timestamp desc limit $max").on(
      'link -> link
    ).as(userLinkLoggingParser *)
  }

  def createUserLinkLogging(UserLinkLogging: UserLinkLogging)(implicit connection: Connection): Int = {
    val nps = Seq[NamedParameter](
      "timestamp" -> UserLinkLogging.timestamp,
      "ipaddress" -> UserLinkLogging.ipaddress,
      "useragent" -> UserLinkLogging.useragent,
      "email" -> UserLinkLogging.email,
      "link" -> UserLinkLogging.link,
      "referer" -> UserLinkLogging.referer)

    // we insert without ID because we want to have database auto increment
    SQL(
      s"""
          insert into $table_consentlogging (timestamp, ipaddress, useragent, email, link, referer) values (
            {timestamp}, {ipaddress}, {useragent}, {email}, {link}, {referer}
          )
        """).on(nps: _*).executeUpdate()
  }

  def deleteUserLinkLogging(UserLinkLogging: UserLinkLogging)(implicit connection: Connection): Boolean = {
    val rowCount = SQL(s"delete from $table_consentlogging where id = {id}").on(
      'id -> UserLinkLogging.id
    ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ => false
    }
  }
}