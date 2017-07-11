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
import utils.ClassnameLogger

/** ************
  * OwcLink
  * ************/
object OwcLinkDAO extends ClassnameLogger {

  /**
    * Parse a OwcLink from a ResultSet
    */
  private val owcLinkParser = {
    str("owc_links.uuid") ~
      str("owc_links.href") ~
      get[Option[String]]("owc_links.mime_type") ~
      get[Option[String]]("owc_links.lang") ~
      get[Option[String]]("owc_links.title") ~
      get[Option[Int]]("owc_links.length") ~
      str("owc_links.rel") map {
      case uuid ~ href ~ mimeType ~ lang ~ title ~ length ~ rel =>
        OwcLink(new URL(href), mimeType, lang, title, length, rel, UUID.fromString(uuid))
    }
  }

  /**
    * Retrieve all OwcLink.
    *
    * @param connection implicit connection
    * @return
    */
  def getAllOwcLinks(implicit connection: Connection): Seq[OwcLink] = {
    SQL(s"select owc_links.* from $tableOwcLinks").as(owcLinkParser *)
  }

  /**
    * Find OwcLinks by uuid
    *
    * @param uuid
    * @param connection implicit connection
    * @return
    */
  def findOwcLinkByUuid(uuid: UUID)(implicit connection: Connection): Option[OwcLink] = {
    SQL(s"""select owc_links.* from $tableOwcLinks where uuid = '${uuid.toString}'""").as(owcLinkParser.singleOpt)
  }

  /**
    * Create an OwcLink.
    *
    * @param owcLink
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def createOwcLink(owcLink: OwcLink)(implicit connection: Connection): Option[OwcLink] = {
    val rowCount = SQL(
      s"""
          insert into $tableOwcLinks values (
            {uuid}, {href}, {mimeType}, {lang}, {title}, {length}, {rel}
          )
        """).on(
      'uuid -> owcLink.uuid.toString,
      'href -> owcLink.href.toString,
      'mimeType -> owcLink.mimeType,
      'lang -> owcLink.lang,
      'title -> owcLink.title,
      'length -> owcLink.length,
      'rel -> owcLink.rel
    ).executeUpdate()

    rowCount match {
      case 1 => Some(owcLink)
      case _ =>
        logger.error(s"OwcLink couldn't be created")
        None
    }
  }

  /**
    * Update single OwcLink
    *
    * @param owcLink
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def updateOwcLink(owcLink: OwcLink)(implicit connection: Connection): Option[OwcLink] = {
    val rowCount = SQL(
      s"""
          update $tableOwcLinks set
            href = {href},
            mime_type = {mimeType},
            lang = {lang},
            title = {title},
            length = {length},
            rel = {rel} where uuid = {uuid}
        """).on(
      'href -> owcLink.href.toString,
      'mimeType -> owcLink.mimeType,
      'lang -> owcLink.lang,
      'title -> owcLink.title,
      'length -> owcLink.length,
      'rel -> owcLink.rel,
      'uuid -> owcLink.uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => Some(owcLink)
      case _ =>
        logger.error(s"OwcLink couldn't be updated")
        None
    }
  }

  /**
    * delete an OwcLink
    *
    * @param owcLink
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteOwcLink(owcLink: OwcLink)(implicit connection: Connection): Boolean = {
    deleteOwcLinkByUuid(owcLink.uuid)
  }

  /**
    * delete an OwcLink by uuid
    *
    * @param uuid
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteOwcLinkByUuid(uuid: UUID)(implicit connection: Connection): Boolean = {
    val rowCount = SQL(s"delete from $tableOwcLinks where uuid = {uuid}").on(
      'uuid -> uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ => logger.error(s"OwcLink couldn't be deleted")
        false
    }
  }
}
