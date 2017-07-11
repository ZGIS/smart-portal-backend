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

/** ************
  * OwcCategory
  * ************/
object OwcCategoryDAO extends ClassnameLogger {

  /**
    * Parse a OwcCategory from a ResultSet
    */
  private val owcCategoryParser = {
    str("owc_categories.uuid") ~
      get[Option[String]]("owc_categories.scheme") ~
      str("owc_categories.term") ~
      get[Option[String]]("owc_categories.label") map {
      case uuid ~ scheme ~ term ~ label =>
        OwcCategory(term, scheme, label, UUID.fromString(uuid))
    }
  }

  /**
    * Retrieve all OwcCategory.
    *
    * @param connection implicit connection
    * @return
    */
  def getAllOwcCategories(implicit connection: Connection): Seq[OwcCategory] = {
    SQL(s"select owc_categories.* from $tableOwcCategories").as(owcCategoryParser *)
  }

  /**
    * Find OwcCategories by uuid
    *
    * @param uuid
    * @param connection implicit connection
    * @return
    */
  def findOwcCategoryByUuid(uuid: UUID)(implicit connection: Connection): Option[OwcCategory] = {
    SQL(s"""select owc_categories.* from $tableOwcCategories where uuid = '${uuid.toString}'""").as(owcCategoryParser.singleOpt)
  }

  /**
    * Create an OwcCategory.
    *
    * @param owcCategory
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def createOwcCategory(owcCategory: OwcCategory)(implicit connection: Connection): Option[OwcCategory] = {
    val rowCount = SQL(
      s"""
          insert into $tableOwcCategories values (
            {uuid}, {scheme}, {term}, {label}
          )
        """).on(
      'uuid -> owcCategory.uuid.toString,
      'scheme -> owcCategory.scheme,
      'term -> owcCategory.term,
      'label -> owcCategory.label
    ).executeUpdate()

    rowCount match {
      case 1 => Some(owcCategory)
      case _ => logger.error("OwcCategory couldn't be created")
        None
    }
  }

  /**
    * Update single OwcCategory (update the label basically only)
    *
    * @param owcCategory
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def updateOwcCategory(owcCategory: OwcCategory)(implicit connection: Connection): Option[OwcCategory] = {
    val rowCount = SQL(
      s"""
          update $tableOwcCategories set
            term = {term},
            scheme = {scheme},
            label = {label} where uuid = {uuid}
        """).on(
      'term -> owcCategory.term,
      'scheme -> owcCategory.scheme,
      'label -> owcCategory.label,
      'uuid -> owcCategory.uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => Some(owcCategory)
      case _ => logger.error("OwcCategory couldn't be updated")
        None
    }
  }

  /**
    * delete an OwcCategory
    *
    * @param owcCategory
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteOwcCategory(owcCategory: OwcCategory)(implicit connection: Connection): Boolean = {
    deleteOwcCategoryByUuid(owcCategory.uuid)
  }

  /**
    * delete an OwcCategory
    *
    * @param uuid
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteOwcCategoryByUuid(uuid: UUID)(implicit connection: Connection): Boolean = {
    val rowCount = SQL(s"delete from $tableOwcCategories where uuid = {uuid}").on(
      'uuid -> uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ => logger.error("OwcCategory couldn't be deleted")
        false
    }
  }
}
