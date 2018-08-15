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

import anorm.SqlParser.{get, str}
import anorm.{RowParser, SQL, ~}
import info.smart.models.owc100._
import utils.ClassnameLogger

/** *********
  * OwcOperation
  * **********/

object OwcOperationDAO extends ClassnameLogger {

  /**
    * Parse a OwcOperation from a ResultSet
    *
    */
  private def owcOperationParser(implicit connection: Connection): RowParser[OwcOperation] = {
    str("owc_operations.uuid") ~
      str("owc_operations.code") ~
      str("owc_operations.method") ~
      get[Option[String]]("owc_operations.mime_type") ~
      str("owc_operations.request_url") ~
      get[Option[String]]("owc_operations.request_uuid") ~
      get[Option[String]]("owc_operations.result_uuid") map {
      case uuid ~ code ~ method ~ mimeType ~ requestUrl ~ requestContentUuid ~ resultContentUuid =>
        OwcOperation(uuid = UUID.fromString(uuid),
          code = code,
          method = method,
          mimeType = mimeType,
          requestUrl = new URL(requestUrl),
          request = requestContentUuid.map(u => OwcContentDAO.findOwcContentByUuid(UUID.fromString(u))).getOrElse(None),
          result = resultContentUuid.map(u => OwcContentDAO.findOwcContentByUuid(UUID.fromString(u))).getOrElse(None))
    }
  }

  /**
    * Retrieve all OwcOperations.
    *
    * @return
    */
  def getAllOwcOperations(implicit connection: Connection): Seq[OwcOperation] = {
    SQL(s"select owc_operations.* from $tableOwcOperations").as(owcOperationParser *)
  }

  /**
    * Find specific OwcOperation.
    *
    * @param code
    * @return
    */
  def findOwcOperationByCode(code: String)(implicit connection: Connection): Seq[OwcOperation] = {
    SQL(s"""select owc_operations.* from $tableOwcOperations where code like '${code}'""").as(owcOperationParser *)
  }

  /**
    * Find specific OwcOperation.
    *
    * @param uuid
    * @return
    */
  def findOwcOperationByUuid(uuid: UUID)(implicit connection: Connection): Option[OwcOperation] = {
    SQL(s"""select owc_operations.* from $tableOwcOperations where uuid = '${uuid.toString}'""").as(owcOperationParser.singleOpt)
  }


  /**
    * Create an owcOperation
    *
    * @param owcOperation
    * @return
    */
  def createOwcOperation(owcOperation: OwcOperation)(implicit connection: Connection): Option[OwcOperation] = {

    val preRequest: Boolean = if (owcOperation.request.isDefined) {
      val exists = OwcContentDAO.findOwcContentByUuid(owcOperation.request.get.uuid).isDefined
      if (exists) {
        logger.error(s"(createOwcOperation/request) OwcContent with UUID: ${owcOperation.request.get.uuid} exists already, won't create OwcOperation")
        false
      } else {
        OwcContentDAO.createOwcContent(owcOperation.request.get).isDefined
      }
    } else {
      true
    }

    val preResult: Boolean = if (owcOperation.result.isDefined) {
      val exists = OwcContentDAO.findOwcContentByUuid(owcOperation.result.get.uuid).isDefined
      if (exists) {
        logger.error(s"(createOwcOperation/result) OwcContent with UUID: ${owcOperation.result.get.uuid} exists already, won't create OwcOperation")
        false
      } else {
        OwcContentDAO.createOwcContent(owcOperation.result.get).isDefined
      }
    } else {
      true
    }

    if (preRequest && preResult) {

      val rowCount = SQL(
        s"""insert into $tableOwcOperations values ( {uuid}, {code}, {method}, {mime_type}, {request_url}, {request_uuid}, {result_uuid})""").on(
        'uuid -> owcOperation.uuid.toString,
        'code -> owcOperation.code,
        'method -> owcOperation.method,
        'mime_type -> owcOperation.mimeType,
        'request_url -> owcOperation.requestUrl.toString,
        'request_uuid -> owcOperation.request.map(_.uuid.toString),
        'result_uuid -> owcOperation.result.map(_.uuid.toString)
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcOperation)
        case _ => logger.error("OwcOperation couldn't be created")
          None
      }
    } else {
      logger.error("Precondition failed, won't create OwcOperation")
      None
    }
  }

  /**
    * Update single OwcOperation
    *
    * @param owcOperation
    * @return
    */
  def updateOwcOperation(owcOperation: OwcOperation)(implicit connection: Connection): Option[OwcOperation] = {

    val preRequest: Boolean = if (owcOperation.request.isDefined) {
      val exists = owcOperation.request.map(c => OwcContentDAO.findOwcContentByUuid(c.uuid)).isDefined
      if (exists) {
        val update = owcOperation.request.map(OwcContentDAO.updateOwcContent(_))
        update.isDefined
      } else {
        val insert = owcOperation.request.map(OwcContentDAO.createOwcContent(_))
        insert.isDefined
      }
    } else {
      val toBeDeleted = findOwcOperationByUuid(owcOperation.uuid).map(_.request).getOrElse(None)
      if (toBeDeleted.isDefined) {
        toBeDeleted.exists(OwcContentDAO.deleteOwcContent(_))
      } else {
        true
      }
    }

    val preResult: Boolean = if (owcOperation.result.isDefined) {
      val exists = owcOperation.result.map(c => OwcContentDAO.findOwcContentByUuid(c.uuid)).isDefined
      if (exists) {
        val update = owcOperation.result.map(OwcContentDAO.updateOwcContent(_))
        update.isDefined
      } else {
        val insert = owcOperation.result.map(OwcContentDAO.createOwcContent(_))
        insert.isDefined
      }
    } else {
      val toBeDeleted = findOwcOperationByUuid(owcOperation.uuid).map(_.result).getOrElse(None)
      if (toBeDeleted.isDefined) {
        toBeDeleted.exists(OwcContentDAO.deleteOwcContent(_))
      } else {
        true
      }
    }

    if (preRequest && preResult) {
      val rowCount = SQL(
        s"""
           |update $tableOwcOperations set
           |code = {code},
           |method = {method},
           |mime_type = {mime_type},
           |request_url = {request_url},
           |request_uuid = {request_uuid},
           |result_uuid = {result_uuid}
           | where uuid = {uuid}
          """.stripMargin).on(
        'code -> owcOperation.code,
        'method -> owcOperation.method,
        'mime_type -> owcOperation.mimeType,
        'request_url -> owcOperation.requestUrl.toString,
        'request_uuid -> owcOperation.request.map(_.uuid.toString),
        'result_uuid -> owcOperation.result.map(_.uuid.toString),
        'uuid -> owcOperation.uuid.toString
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcOperation)
        case _ =>
          logger.error("OwcOperation couldn't be updated")
          None
      }
    } else {
      logger.error("Precondition failed, won't updated OwcOperation")
      None
    }
  }

  /**
    * delete an OwcOperation
    *
    * @param owcOperation
    * @return
    */
  def deleteOwcOperation(owcOperation: OwcOperation)(implicit connection: Connection): Boolean = {

    val preDeleteCheckRequest = if (owcOperation.request.isDefined) {
      owcOperation.request.exists(OwcContentDAO.deleteOwcContent(_))
    } else {
      true
    }

    val preDeleteCheckResult = if (owcOperation.result.isDefined) {
      owcOperation.result.exists(OwcContentDAO.deleteOwcContent(_))
    } else {
      true
    }

    if (preDeleteCheckRequest && preDeleteCheckResult) {
      val rowCount = SQL(s"delete from $tableOwcOperations where uuid = {uuid}").on(
        'uuid -> owcOperation.uuid.toString
      ).executeUpdate()

      rowCount match {
        case 1 => true
        case _ =>
          logger.error("OwcOperation couldn't be deleted")
          false
      }
    } else {
      logger.error("Precondition failed, won't delete OwcOperation")
      false
    }
  }
}
