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
  * OwcContent
  * ************/

object OwcContentDAO extends ClassnameLogger {

  /**
    * Parse a OwcContent from a ResultSet
    *
    */
  private val owcContentParser = {
    str("owc_contents.uuid") ~
      str("owc_contents.mime_type") ~
      get[Option[String]]("owc_contents.url") ~
      get[Option[String]]("owc_contents.title") ~
      get[Option[String]]("owc_contents.content") map {
      case uuid ~ mimeType ~ url ~ title ~ content =>
        OwcContent(mimeType = mimeType, url = url.map(new URL(_)), title = title, content = content, uuid = UUID.fromString(uuid))
    }
  }

  /**
    * Retrieve all OwcContent.
    *
    * @param connection implicit connection
    * @return
    */
  def getAllOwcContents(implicit connection: Connection): Seq[OwcContent] = {
    SQL(s"select owc_contents.* from $tableOwcContents").as(owcContentParser *)
  }

  /**
    * Find OwcContents by uuid
    *
    * @param uuid
    * @param connection implicit connection
    * @return
    */
  def findOwcContentByUuid(uuid: UUID)(implicit connection: Connection): Option[OwcContent] = {
    SQL(s"""select owc_contents.* from $tableOwcContents where uuid = '${uuid.toString}'""").as(owcContentParser.singleOpt)
  }

  /**
    * Create an OwcContent.
    *
    * @param owcContent
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def createOwcContent(owcContent: OwcContent)(implicit connection: Connection): Option[OwcContent] = {
    val rowCount = SQL(
      s"""
          insert into $tableOwcContents values (
            {uuid}, {mimeType}, {url}, {title}, {content}
          )
        """).on(
      'uuid -> owcContent.uuid.toString,
      'mimeType -> owcContent.mimeType,
      'url -> owcContent.url.map(_.toString),
      'title -> owcContent.title,
      'content -> owcContent.content
    ).executeUpdate()

    rowCount match {
      case 1 => Some(owcContent)
      case _ =>
        logger.error(s"OwcContent couldn't be created")
        None
    }
  }

  /**
    * Update single OwcContent
    *
    * @param owcContent
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def updateOwcContent(owcContent: OwcContent)(implicit connection: Connection): Option[OwcContent] = {
    val rowCount = SQL(
      s"""
          update $tableOwcContents set
            mime_type = {mimeType},
            url = {url},
            title = {title},
            content = {content} where uuid = {uuid}
        """).on(
      'mimeType -> owcContent.mimeType,
      'url -> owcContent.url.map(_.toString),
      'title -> owcContent.title,
      'content -> owcContent.content,
      'uuid -> owcContent.uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => Some(owcContent)
      case _ => logger.error(s"OwcContent couldn't be updated")
        None
    }
  }

  /**
    * delete an OwcContent by uuid
    *
    * @param owcContent
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteOwcContent(owcContent: OwcContent)(implicit connection: Connection): Boolean = {
    deleteOwcContentByUuid(owcContent.uuid)
  }

  /**
    * delete an OwcContent by uuid
    *
    * @param uuid
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteOwcContentByUuid(uuid: UUID)(implicit connection: Connection): Boolean = {
    val rowCount = SQL(s"delete from $tableOwcContents where uuid = {uuid}").on(
      'uuid -> uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ =>
        logger.error(s"OwcContent couldn't be deleted")
        false
    }
  }
}
