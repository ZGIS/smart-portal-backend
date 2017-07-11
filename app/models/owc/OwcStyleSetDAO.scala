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

import java.io.InvalidClassException
import java.net.URL
import java.sql.Connection
import java.util.UUID

import anorm.SqlParser._
import anorm._
import info.smart.models.owc100._
import utils.ClassnameLogger
import utils.StringUtils.OptionUuidConverters

/** ************
  * OwcStyleSet
  * ************/
object OwcStyleSetDAO extends ClassnameLogger {

  /**
    * content_uuid.map(u => u.toUuidOption.map(findOwcContentByUuid(_)).getOrElse(None)).getOrElse(None)
    *
    * @return
    */
  private def owcStyleSetParser(implicit connection: Connection): RowParser[OwcStyleSet] = {
      str("owc_stylesets.name") ~
        str("title") ~
        get[Option[String]]("owc_stylesets.abstrakt") ~
        get[Option[Boolean]]("owc_stylesets.is_default") ~
        get[Option[String]]("owc_stylesets.legend_url") ~
        get[Option[String]]("owc_stylesets.content_uuid") ~
        str("owc_stylesets.uuid") map {
        case name ~ title ~ abstrakt ~ isDefault ~ legendUrl ~ content_uuid ~ uuidstring =>
          OwcStyleSet(name = name,
            legendUrl = legendUrl.map(new URL(_)),
            title = title,
            abstrakt = abstrakt,
            default = isDefault,
            content = content_uuid.map(u => u.toUuidOption.map(OwcContentDAO.findOwcContentByUuid(_)).getOrElse(None)).getOrElse(None),
            uuid = UUID.fromString(uuidstring))
      }
  }

  /**
    * Retrieve all OwcStyleSet.
    *
    * @param connection implicit connection
    * @return
    */
  def getAllOwcStyleSets(implicit connection: Connection): Seq[OwcStyleSet] = {
      SQL(s"select owc_stylesets.* from $tableOwcStyleSets").as(owcStyleSetParser *)
  }

  /**
    * Find OwcStyleSets by uuid
    *
    * @param uuid
    * @param connection implicit connection
    * @return
    */
  def findOwcStyleSetByUuid(uuid: UUID)(implicit connection: Connection): Option[OwcStyleSet] = {
      SQL(s"""select owc_stylesets.* from $tableOwcStyleSets where uuid = '${uuid.toString}'""").as(owcStyleSetParser.singleOpt)
  }

  /**
    * Create an OwcStyleSet.
    *
    * @param owcStyleSet
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def createOwcStyleSet(owcStyleSet: OwcStyleSet)(implicit connection: Connection): Option[OwcStyleSet] = {
    val pre: Boolean = if (owcStyleSet.content.isDefined) {
      val exists = OwcContentDAO.findOwcContentByUuid(owcStyleSet.content.get.uuid).isDefined
      if (exists) {
        logger.error(s"(createOwcStyleSet) OwcContent with UUID: ${owcStyleSet.content.get.uuid} exists already, won't create OwcStyleSet")
        false
      } else {
        val insert = OwcContentDAO.createOwcContent(owcStyleSet.content.get)
        insert.isDefined
      }
    } else {
      true
    }

    if (pre) {
          val rowCount = SQL(
            s"""insert into $tableOwcStyleSets values (
              {uuid}, {name}, {title}, {abstrakt}, {isDefault}, {legendUrl}, {content}
            )""").on(
            'uuid -> owcStyleSet.uuid.toString,
            'name -> owcStyleSet.name,
            'title -> owcStyleSet.title,
            'abstrakt -> owcStyleSet.abstrakt,
            'isDefault -> owcStyleSet.default,
            'legendUrl -> owcStyleSet.legendUrl.map(_.toString),
            'content -> owcStyleSet.content.map(_.uuid.toString)
          ).executeUpdate()

          rowCount match {
            case 1 => Some(owcStyleSet)
            case _ => {
              logger.error(s"OwcStyleSet couldn't be created")
              None
            }
          }
    } else {
      logger.error(s"Precondition failed, won't create OwcStyleSet")
      None
    }
  }

  /**
    * Update single OwcStyleSet
    *
    * @param owcStyleSet
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def updateOwcStyleSet(owcStyleSet: OwcStyleSet)(implicit connection: Connection): Option[OwcStyleSet] = {
    val pre: Boolean = if (owcStyleSet.content.isDefined) {
        val exists = OwcContentDAO.findOwcContentByUuid(owcStyleSet.content.get.uuid).isDefined
        if (exists) {
          val update = OwcContentDAO.updateOwcContent(owcStyleSet.content.get)
          update.isDefined
        } else {
          val insert = OwcContentDAO.createOwcContent(owcStyleSet.content.get)
          insert.isDefined
        }
      } else {
        val toBeDeleted = findOwcStyleSetByUuid(owcStyleSet.uuid).map(_.content).getOrElse(None)
        if (toBeDeleted.isDefined) {
          OwcContentDAO.deleteOwcContent(toBeDeleted.get)
        } else {
          true
        }
      }

      if (pre) {
        val rowCount1 = SQL(
          s"""update $tableOwcStyleSets set
            name = {name},
            title = {title},
            abstrakt = {abstrakt},
            is_default = {isDefault},
            legend_url = {legendUrl},
            content_uuid = {content} where uuid = {uuid}""").on(
          'name -> owcStyleSet.name,
          'title -> owcStyleSet.title,
          'abstrakt -> owcStyleSet.abstrakt,
          'isDefault -> owcStyleSet.default,
          'legendUrl -> owcStyleSet.legendUrl.map(_.toString),
          'content -> owcStyleSet.content.map(_.uuid.toString),
          'uuid -> owcStyleSet.uuid.toString
        ).executeUpdate()

        rowCount1 match {
          case 1 => Some(owcStyleSet)
          case _ => logger.error(s"OwcStyleSet couldn't be updated")
            None
        }
      } else {
        logger.error(s"Precondition failed, won't update OwcStyleSet")
        None
      }
  }

  /**
    * delete an OwcStyleSet by uuid
    *
    * @param owcStyleSet
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteOwcStyleSet(owcStyleSet: OwcStyleSet)(implicit connection: Connection): Boolean = {
      val pre: Boolean = if (owcStyleSet.content.isDefined) {
        val exists = OwcContentDAO.findOwcContentByUuid(owcStyleSet.content.get.uuid).isDefined
        if (exists) {
          val delete = OwcContentDAO.deleteOwcContent(owcStyleSet.content.get)
          delete
        } else {
          true
        }
      } else {
        true
      }

      if (pre) {
        val rowCount = SQL(s"delete from $tableOwcStyleSets where uuid = {uuid}").on(
          'uuid -> owcStyleSet.uuid.toString
        ).executeUpdate()

        rowCount match {
          case 1 => true
          case _ => logger.error(s"OwcStyleSet couldn't be deleted")
            false
        }
      } else {
        logger.error(s"Precondition failed, won't delete OwcStyleSet")
        false
      }
  }
}
