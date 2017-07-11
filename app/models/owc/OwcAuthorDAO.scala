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

package models.owc

import java.net.URL
import java.sql.Connection
import java.util.UUID

import anorm.SqlParser._
import anorm._
import info.smart.models.owc100._
import uk.gov.hmrc.emailaddress.EmailAddress
import utils.ClassnameLogger

/** *********
  * OwcAuthor
  * **********/

object OwcAuthorDAO extends ClassnameLogger {

  /**
    * Parse a OwcAuthor from a ResultSet
    */
  private val owcAuthorParser = {
    get[Option[String]]("owc_authors.name") ~
      get[Option[String]]("owc_authors.email") ~
      get[Option[String]]("owc_authors.uri") ~
      str("owc_authors.uuid") map {
      case name ~ email ~ uri ~ uuid =>
        OwcAuthor(name, email.map(EmailAddress(_)), uri.map(new URL(_)), UUID.fromString(uuid))
    }
  }

  /**
    * Retrieve all OwcAuthors.
    *
    * @param connection implicit connection
    * @return
    */
  def getAllOwcAuthors()(implicit connection: Connection): Seq[OwcAuthor] = {
    SQL(s"select owc_authors.* from $tableOwcAuthors").as(owcAuthorParser *)
  }

  /**
    * Find specific OwcAuthor.
    *
    * @param uuid
    * @param connection implicit connection
    * @return
    */
  def findOwcAuthorByUuid(uuid: UUID)(implicit connection: Connection): Option[OwcAuthor] = {
    SQL(s"""select owc_authors.* from $tableOwcAuthors where uuid = '${uuid.toString}'""").as(owcAuthorParser.singleOpt)
  }

  /**
    * Create an owcAuthor.
    *
    * @param owcAuthor
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def createOwcAuthor(owcAuthor: OwcAuthor)(implicit connection: Connection): Option[OwcAuthor] = {
    val rowCount = SQL(
      s"""
          insert into $tableOwcAuthors values (
            {uuid}, {name}, {email}, {uri}
          )
        """).on(
      'uuid -> owcAuthor.uuid.toString,
      'name -> owcAuthor.name,
      'email -> owcAuthor.email.map(_.toString()),
      'uri -> owcAuthor.uri.map(_.toString)
    ).executeUpdate()

    rowCount match {
      case 1 => Some(owcAuthor)
      case _ => logger.error(s"owcAuthor couldn't be created")
        None
    }
  }

  /**
    * Update single OwcAuthor
    *
    * @param owcAuthor
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def updateOwcAuthor(owcAuthor: OwcAuthor)(implicit connection: Connection): Option[OwcAuthor] = {
    val rowCount = SQL(
      s"""
          update $tableOwcAuthors set
            name = {name},
            email = {email},
            uri = {uri} where uuid = {uuid}
        """).on(
      'name -> owcAuthor.name,
      'email -> owcAuthor.email.map(_.toString()),
      'uri -> owcAuthor.uri.map(_.toString()),
      'uuid -> owcAuthor.uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => Some(owcAuthor)
      case _ => logger.error(s"owcAuthor couldn't be updated")
        None
    }
  }


  /**
    * delete an OwcAuthor
    *
    * @param owcAuthor
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteOwcAuthor(owcAuthor: OwcAuthor)(implicit connection: Connection): Boolean = {
    deleteOwcAuthorByUuid(owcAuthor.uuid)
  }

  /**
    * delete an OwcAuthor
    *
    * @param uuid
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteOwcAuthorByUuid(uuid: UUID)(implicit connection: Connection): Boolean = {
    val rowCount = SQL(s"delete from $tableOwcAuthors where uuid = {uuid}").on(
      'uuid -> uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ =>
        logger.error(s"owcAuthor couldn't be deleted")
        false
    }
  }
}
