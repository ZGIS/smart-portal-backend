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

import java.sql.Connection
import java.util.UUID

import anorm.SqlParser._
import anorm._
import info.smart.models.owc100._
import utils.ClassnameLogger

/** *********
  * OwcCreatorDisplay
  * **********/
object OwcCreatorDisplayDAO extends ClassnameLogger {

  /**
    * Parse a OwcCreatorDisplay from a ResultSet
    */
  private val owcCreatorDisplayParser = {
    get[Option[Int]]("owc_creator_displays.pixel_width") ~
      get[Option[Int]]("owc_creator_displays.pixel_height") ~
      get[Option[Double]]("owc_creator_displays.mm_per_pixel") ~
      str("owc_creator_displays.uuid") map {
      case pixelWidth ~ pixelHeight ~ mmPerPixel ~ uuid =>
        OwcCreatorDisplay(pixelWidth = pixelWidth,
          pixelHeight = pixelHeight,
          mmPerPixel = mmPerPixel,
          uuid = UUID.fromString(uuid))
    }
  }

  /**
    * Retrieve all OwcCreatorDisplays.
    *
    * @param connection implicit connection
    * @return
    */
  def getAllOwcCreatorDisplays()(implicit connection: Connection): Seq[OwcCreatorDisplay] = {
    SQL(s"select owc_creator_displays.* from $tableOwcCreatorDisplays").as(owcCreatorDisplayParser *)
  }

  /**
    * Find specific OwcCreatorDisplay.
    *
    * @param uuid
    * @param connection implicit connection
    * @return
    */
  def findOwcCreatorDisplayByUuid(uuid: UUID)(implicit connection: Connection): Option[OwcCreatorDisplay] = {
    SQL(s"""select owc_creator_displays.* from $tableOwcCreatorDisplays where uuid = '${uuid.toString}'""").as(owcCreatorDisplayParser.singleOpt)
  }

  /**
    * Create an owcCreatorDisplay.
    *
    * @param owcCreatorDisplay
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def createOwcCreatorDisplay(owcCreatorDisplay: OwcCreatorDisplay)(implicit connection: Connection): Option[OwcCreatorDisplay] = {
    val rowCount = SQL(
      s"""
          insert into $tableOwcCreatorDisplays values (
           {uuid}, {pixelWidth}, {pixelHeight}, {mmPerPixel}
          )
        """.stripMargin).on(
      'pixelWidth -> owcCreatorDisplay.pixelWidth,
      'pixelHeight -> owcCreatorDisplay.pixelHeight,
      'mmPerPixel -> owcCreatorDisplay.mmPerPixel,
      'uuid -> owcCreatorDisplay.uuid
    ).executeUpdate()

    rowCount match {
      case 1 => Some(owcCreatorDisplay)
      case _ => logger.error(s"owcCreatorDisplay couldn't be created")
        None
    }
  }

  /**
    * Update single OwcCreatorDisplay
    *
    * @param owcCreatorDisplay
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def updateOwcCreatorDisplay(owcCreatorDisplay: OwcCreatorDisplay)(implicit connection: Connection): Option[OwcCreatorDisplay] = {
    val rowCount = SQL(
      s"""
          update $tableOwcCreatorDisplays set
            pixel_width = {pixelWidth},
            pixel_height = {pixelHeight},
            mm_per_pixel = {mmPerPixel} where uuid = {uuid}
        """).on(
      'pixelWidth -> owcCreatorDisplay.pixelWidth,
      'pixelHeight -> owcCreatorDisplay.pixelHeight,
      'mmPerPixel -> owcCreatorDisplay.mmPerPixel,
      'uuid -> owcCreatorDisplay.uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => Some(owcCreatorDisplay)
      case _ => logger.error(s"owcCreatorDisplay couldn't be updated")
        None
    }
  }


  /**
    * delete an OwcCreatorDisplay
    *
    * @param owcCreatorDisplay
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteOwcCreatorDisplay(owcCreatorDisplay: OwcCreatorDisplay)(implicit connection: Connection): Boolean = {
    deleteOwcCreatorDisplayByUuid(owcCreatorDisplay.uuid)
  }

  /**
    * delete an OwcCreatorDisplay
    *
    * @param uuid
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteOwcCreatorDisplayByUuid(uuid: UUID)(implicit connection: Connection): Boolean = {
    val rowCount = SQL(s"delete from $tableOwcCreatorDisplays where uuid = {uuid}").on(
      'uuid -> uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ =>
        logger.error(s"owcCreatorDisplay couldn't be deleted")
        false
    }
  }
}
