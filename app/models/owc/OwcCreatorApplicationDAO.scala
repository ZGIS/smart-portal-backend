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

/** *********
  * DAO for [[OwcCreatorApplication]]
  * **********/
object OwcCreatorApplicationDAO extends ClassnameLogger {

  /**
    * Parse a OwcCreatorApplication from a ResultSet
    */
  private val owcCreatorApplicationParser = {
    get[Option[String]]("owc_creator_applications.title") ~
      get[Option[String]]("owc_creator_applications.uri") ~
      get[Option[String]]("owc_creator_applications.version") ~
      str("owc_creator_applications.uuid") map {
      case title ~ uri ~ version ~ uuid =>
        OwcCreatorApplication(title = title,
          uri = uri.map(new URL(_)),
          version = version,
          uuid = UUID.fromString(uuid))
    }
  }

  /**
    * Retrieve all OwcCreatorApplications.
    *
    * @param connection implicit connection
    * @return
    */
  def getAllOwcCreatorApplications()(implicit connection: Connection): Seq[OwcCreatorApplication] = {
    SQL(s"select owc_creator_applications.* from $tableOwcCreatorApplications").as(owcCreatorApplicationParser *)
  }

  /**
    * Find specific OwcCreatorApplication.
    *
    * @param uuid
    * @param connection implicit connection
    * @return
    */
  def findOwcCreatorApplicationByUuid(uuid: UUID)(implicit connection: Connection): Option[OwcCreatorApplication] = {
    SQL(s"""select owc_creator_applications.* from $tableOwcCreatorApplications where uuid = '${uuid.toString}'""").as(owcCreatorApplicationParser.singleOpt)
  }

  /**
    * Create an owcCreatorApplication.
    *
    * @param owcCreatorApplication
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def createOwcCreatorApplication(owcCreatorApplication: OwcCreatorApplication)(implicit connection: Connection): Option[OwcCreatorApplication] = {
    val rowCount = SQL(
      s"""
          insert into $tableOwcCreatorApplications values (
            {uuid}, {title}, {uri}, {version}
          )
        """).on(
      'uuid -> owcCreatorApplication.uuid.toString,
      'title -> owcCreatorApplication.title,
      'uri -> owcCreatorApplication.uri.map(_.toString),
      'version -> owcCreatorApplication.version
    ).executeUpdate()

    rowCount match {
      case 1 => Some(owcCreatorApplication)
      case _ => logger.error("owcCreatorApplication couldn't be created")
        None
    }
  }

  /**
    * Update single OwcCreatorApplication
    *
    * @param owcCreatorApplication
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def updateOwcCreatorApplication(owcCreatorApplication: OwcCreatorApplication)(implicit connection: Connection): Option[OwcCreatorApplication] = {
    val rowCount = SQL(
      s"""
          update $tableOwcCreatorApplications set
            title = {title},
            uri = {uri},
            version = {version} where uuid = {uuid}
        """).on(
      'title -> owcCreatorApplication.title,
      'uri -> owcCreatorApplication.uri.map(_.toString()),
      'version -> owcCreatorApplication.version,
      'uuid -> owcCreatorApplication.uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => Some(owcCreatorApplication)
      case _ => logger.error("owcCreatorApplication couldn't be updated")
        None
    }
  }

  /**
    * delete an OwcCreatorApplication
    *
    * @param owcCreatorApplication
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteOwcCreatorApplication(owcCreatorApplication: OwcCreatorApplication)(implicit connection: Connection): Boolean = {
    deleteOwcCreatorApplicationByUuid(owcCreatorApplication.uuid)
  }

  /**
    * delete an OwcCreatorApplication
    *
    * @param uuid
    * @param connection implicit connection should be managed via transaction from calling entity
    * @return
    */
  def deleteOwcCreatorApplicationByUuid(uuid: UUID)(implicit connection: Connection): Boolean = {
    val rowCount = SQL(s"delete from $tableOwcCreatorApplications where uuid = {uuid}").on(
      'uuid -> uuid.toString
    ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ =>
        logger.error("owcCreatorApplication couldn't be deleted")
        false
    }
  }
}
