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
import java.util.UUID
import javax.inject.{Inject, Singleton}

import anorm.SqlParser.{get, str}
import anorm.{RowParser, SQL, ~}
import info.smart.models.owc100._
import play.api.db.Database
import utils.ClassnameLogger

/**
  * OwcOfferingDAO - store and retrieve OWS Context Documents
  * An OWC document is an extended FeatureCollection, where the features (aka entries) hold a variety of metadata
  * about the things they provide the context for (i.e. other data sets, services, metadata records)
  * OWC documents do not duplicate a CSW MD_Metadata record, but a collection of referenced resources;
  *
  * @param db
  */
@Singleton
class OwcOfferingDAO @Inject()(db: Database, owcPropertiesDAO: OwcPropertiesDAO) extends ClassnameLogger {

  /** *********
    * OwcOperation
    * **********/

  /**
    * Parse a OwcOperation from a ResultSet
    *
    */
  val owcOperationParser = {
    str("uuid") ~
      str("code") ~
      str("method") ~
      get[Option[String]]("mime_type") ~
      str("request_url") ~
      get[Option[String]]("request_uuid") ~
      get[Option[String]]("result_uuid") map {
      case uuid ~ code ~ method ~ mimeType ~ requestUrl ~ requestContentUuid ~ resultContentUuid =>
        OwcOperation(uuid = UUID.fromString(uuid),
          code = code,
          method = method,
          mimeType = mimeType,
          requestUrl = new URL(requestUrl),
          request = requestContentUuid.map(u => owcPropertiesDAO.findOwcContentsByUuid(UUID.fromString(u))).getOrElse(None),
          result = requestContentUuid.map(u => owcPropertiesDAO.findOwcContentsByUuid(UUID.fromString(u))).getOrElse(None))
    }
  }

  /**
    * Retrieve all OwcOperations.
    *
    * @return
    */
  def getAllOwcOperations: Seq[OwcOperation] = {
    db.withConnection { implicit connection =>
      SQL(s"select * from $tableOwcOperations").as(owcOperationParser *)
    }
  }

  /**
    * Find specific OwcOperation.
    *
    * @param code
    * @return
    */
  def findOwcOperationByCode(code: String): Seq[OwcOperation] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcOperations where code like '${code}'""").as(owcOperationParser *)
    }
  }

  /**
    * Find specific OwcOperation.
    *
    * @param uuid
    * @return
    */
  def findOwcOperationByUuid(uuid: UUID): Option[OwcOperation] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcOperations where uuid = '${uuid.toString}'""").as(owcOperationParser.singleOpt)
    }
  }


  /**
    * Create an owcOperation
    *
    * @param owcOperation
    * @return
    */
  def createOwcOperation(owcOperation: OwcOperation): Option[OwcOperation] = {

    val preRequest: Boolean = if (owcOperation.request.isDefined) {
      val exists = owcOperation.request.map(c => owcPropertiesDAO.findOwcContentsByUuid(c.uuid)).isDefined
      if (exists) {
        logger.error(s"OwcContent with UUID: ${owcOperation.request.get.uuid} exists already, won't create OwcOperation")
        false
      } else {
        val insert = owcOperation.request.map(owcPropertiesDAO.createOwcContent(_))
        insert.isDefined
      }
    } else {
      true
    }

    val preResult: Boolean = if (owcOperation.result.isDefined) {
      val exists = owcOperation.result.map(c => owcPropertiesDAO.findOwcContentsByUuid(c.uuid)).isDefined
      if (exists) {
        logger.error(s"OwcContent with UUID: ${owcOperation.result.get.uuid} exists already, won't create OwcOperation")
        false
      } else {
        val insert = owcOperation.result.map(owcPropertiesDAO.createOwcContent(_))
        insert.isDefined
      }
    } else {
      true
    }

    if (preRequest && preResult) {
      db.withConnection { implicit connection =>
        val rowCount = SQL(
          s"""
          insert into $tableOwcOperations values (
            {uuid}, {code}, {method}, {mime_type}, {result_url}, {request_uuid}, {result_uuid}
          )
        """).on(
          'uuid -> owcOperation.uuid.toString,
          'code -> owcOperation.code,
          'method -> owcOperation.method,
          'mime_type -> owcOperation.mimeType,
          'request_url -> owcOperation.requestUrl.toString,
          'request_uuid -> owcOperation.request.map(_.uuid.toString),
          'result_uuid -> owcOperation.request.map(_.uuid.toString)
        ).executeUpdate()

        rowCount match {
          case 1 => Some(owcOperation)
          case _ => logger.error(s"OwcOperation couldn't be created")
            None
        }
      }
    } else {
      logger.error(s"Precondition failed, won't create OwcOperation")
      None
    }
  }

  /**
    * Update single OwcOperation
    *
    * @param owcOperation
    * @return
    */
  def updateOwcOperation(owcOperation: OwcOperation): Option[OwcOperation] = {

    val preRequest: Boolean = if (owcOperation.request.isDefined) {
      val exists = owcOperation.request.map(c => owcPropertiesDAO.findOwcContentsByUuid(c.uuid)).isDefined
      if (exists) {
        val update = owcOperation.request.map(owcPropertiesDAO.updateOwcContent(_))
        update.isDefined
      } else {
        val insert = owcOperation.request.map(owcPropertiesDAO.createOwcContent(_))
        insert.isDefined
      }
    } else {
      val toBeDeleted = findOwcOperationByUuid(owcOperation.uuid).map(_.request).getOrElse(None)
      if (toBeDeleted.isDefined) {
        toBeDeleted.exists(owcPropertiesDAO.deleteOwcContent(_))
      } else {
        true
      }
    }

    val preResult: Boolean = if (owcOperation.result.isDefined) {
      val exists = owcOperation.result.map(c => owcPropertiesDAO.findOwcContentsByUuid(c.uuid)).isDefined
      if (exists) {
        val update = owcOperation.result.map(owcPropertiesDAO.updateOwcContent(_))
        update.isDefined
      } else {
        val insert = owcOperation.result.map(owcPropertiesDAO.createOwcContent(_))
        insert.isDefined
      }
    } else {
      val toBeDeleted = findOwcOperationByUuid(owcOperation.uuid).map(_.result).getOrElse(None)
      if (toBeDeleted.isDefined) {
        toBeDeleted.exists(owcPropertiesDAO.deleteOwcContent(_))
      } else {
        true
      }
    }

    if (preRequest && preResult) {

      db.withConnection { implicit connection =>
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
          'result_uuid -> owcOperation.request.map(_.uuid.toString),
          'uuid -> owcOperation.uuid.toString
        ).executeUpdate()

        rowCount match {
          case 1 => Some(owcOperation)
          case _ =>
            logger.error(s"OwcOperation couldn't be updated")
            None
        }
      }
    } else {
      logger.error(s"Precondition failed, won't updated OwcOperation")
      None
    }
  }

  /**
    * delete an OwcOperation
    *
    * @param owcOperation
    * @return
    */
  def deleteOwcOperation(owcOperation: OwcOperation): Boolean = {

    val preDeleteCheckRequest = if (owcOperation.request.isDefined) {
      owcOperation.request.exists(owcPropertiesDAO.deleteOwcContent(_))
    } else {
      true
    }

    val preDeleteCheckResult = if (owcOperation.result.isDefined) {
      owcOperation.result.exists(owcPropertiesDAO.deleteOwcContent(_))
    } else {
      true
    }

    if (preDeleteCheckRequest && preDeleteCheckResult) {
      db.withConnection { implicit connection =>
        val rowCount = SQL(s"delete from $tableOwcOperations where uuid = {uuid}").on(
          'uuid -> owcOperation.uuid.toString
        ).executeUpdate()

        rowCount match {
          case 1 => true
          case _ =>
            logger.error(s"OwcOperation couldn't be deleted")
            false
        }
      }
    } else {
      logger.error(s"Precondition failed, won't delete OwcOperation")
      false
    }
  }

  /** *********
    * OwcOffering
    * **********/

  /**
    * Parse a OwcOperation from a ResultSet
    */
  val owcOfferingParser: RowParser[OwcOffering] = {
    str("uuid") ~
      str("code") ~
      get[Option[String]]("operations") ~
      get[Option[String]]("contents") ~
      get[Option[String]]("styles") map {
      case uuid ~ code ~ operationsUuids ~ contentsUuids ~ stylesUuids =>
        OwcOffering(
          new URL(code),
          operations = operationsUuids.map(u => findByPropertiesUUID[OwcOperation](Some(u))(OwcOperationEvidence).toList).getOrElse(List()),
          contents = contentsUuids.map(u => findByPropertiesUUID[OwcContent](Some(u))(OwcContentEvidence).toList).getOrElse(List()),
          styles = stylesUuids.map(u => findByPropertiesUUID[OwcStyleSet](Some(u))(OwcStyleSetEvidence).toList).getOrElse(List()),
          uuid = UUID.fromString(uuid)
        )
    }
  }

  /**
    * get all the offerings
    *
    * @return
    */
  def getAllOwcOfferings: Seq[OwcOffering] = {
    db.withConnection { implicit connection =>
      SQL(s"select * from $tableOwcOfferings").as(owcOfferingParser *)
    }
  }

  /**
    * finds the distinct offering by uuid
    *
    * @param uuid
    * @return
    */
  def findOwcOfferingByUuid(uuid: UUID): Option[OwcOffering] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcOfferings where uuid = '${uuid.toString}'""").as(owcOfferingParser.singleOpt)
    }
  }

  /**
    * takes a list of operations (e.g. for createOfferring) and tests that non of these exist,
    * and if so creates them in the database and returns success of the overall create
    *
    * @param operations
    * @return
    */
  def preCreateCheckOperations(operations: List[OwcOperation]): Boolean = {
    if (operations.nonEmpty) {
      val uuidString = operations.map(_.uuid.toString).mkString(":")
      val exists = findByPropertiesUUID[OwcOperation](Some(uuidString))(OwcOperationEvidence).toList.nonEmpty
      if (exists) {
        logger.error(s"OwcOperation with UUID one of: ${uuidString} exists already, recommend abort")
        false
      } else {
        val insert = operations.map(createOwcOperation(_)).filter(_.isDefined)
        insert.length == operations.length
      }
    } else {
      true
    }
  }

  /**
    * takes a list of contents (e.g. for createOfferring) and tests that non of these exist,
    * and if so creates them in the database and returns success of the overall create
    *
    * @param contents
    * @return
    */
  def preCreateCheckContents(contents: List[OwcContent]): Boolean = {
    if (contents.nonEmpty) {
      val uuidString = contents.map(_.uuid.toString).mkString(":")
      val exists = findByPropertiesUUID[OwcContent](Some(uuidString))(OwcContentEvidence).toList.isEmpty
      if (exists) {
        logger.error(s"OwcContent with UUID one of: ${uuidString} exists already, recommend abort")
        false
      } else {
        val insert = contents.map(owcPropertiesDAO.createOwcContent(_)).filter(_.isDefined)
        insert.length == contents.length
      }
    } else {
      true
    }
  }

  /**
    * takes a list of styles (e.g. for createOfferring) and tests that non of these exist,
    * and if so creates them in the database and returns success of the overall create
    *
    * @param styles
    * @return
    */
  def preCreateCheckStyleSets(styles: List[OwcStyleSet]): Boolean = {
    if (styles.nonEmpty) {
      val uuidString = styles.map(_.uuid.toString).mkString(":")
      val exists = findByPropertiesUUID[OwcStyleSet](Some(uuidString))(OwcStyleSetEvidence).toList.isEmpty
      if (exists) {
        logger.error(s"OwcStyleSet with UUID one of: ${uuidString} exists already, recommend abort")
        false
      } else {
        val insert = styles.map(owcPropertiesDAO.createOwcStyleSet(_)).filter(_.isDefined)
        insert.length == styles.length
      }
    } else {
      true
    }
  }

  /**
    * creates an offering and its corresponding child operations, contents, styles
    *
    * @param owcOffering
    * @return
    */
  def createOwcOffering(owcOffering: OwcOffering): Option[OwcOffering] = {

    if (preCreateCheckOperations(owcOffering.operations) &&
      preCreateCheckContents(owcOffering.contents) &&
      preCreateCheckStyleSets(owcOffering.styles)) {

      db.withTransaction {
        implicit connection => {

          val rowCount = SQL(
            s"""
          insert into $tableOwcOfferings values (
            {uuid}, {code}, {operations}, {contents}, {styles}
          )
        """).on(
            'uuid -> owcOffering.uuid.toString,
            'code -> owcOffering.code.toString,
            'operations -> owcOffering.operations.map(_.uuid.toString).mkString(":"),
            'contents -> owcOffering.contents.map(_.uuid.toString).mkString(":"),
            'styles -> owcOffering.styles.map(_.uuid.toString).mkString(":")
          ).executeUpdate()

          rowCount match {
            case 1 => Some(owcOffering)
            case _ =>
              logger.error(s"OwcOperation couldn't be created")
              None
          }
        }
      }
    } else {
      logger.error(s"Precondition failed, won't create OwcOffering")
      None
    }
  }

  /**
    * pre Check for OwcOperations Update For an OwcOffering, will delete, update and insert
    * required operations in order to make OwcOffering Update consistent
    *
    * @param owcOffering
    * @return
    */
  def preUpdateCheckOperationsForOffering(owcOffering: OwcOffering): Boolean = {

    // get current list,
    val current: List[UUID] = owcOffering.operations.map(_.uuid)

    // get old list,
    val old: List[UUID] = findOwcOfferingByUuid(owcOffering.uuid)
      .map(o => o.operations.map(_.uuid)).getOrElse(List())

    // in old but not current -> delete
    val toBeDeleted = old.diff(current)
    val deleted = owcOffering.operations.filter(o => toBeDeleted.contains(o.uuid))
      .exists(deleteOwcOperation(_))

    // in both lists -> update
    val toBeUpdated = current.intersect(old)
    val updated = owcOffering.operations.filter(o => toBeUpdated.contains(o.uuid))
      .map(updateOwcOperation(_))
      .count(_.isDefined) == toBeUpdated.length

    // in current but not in old -> insert
    val toBeInserted = current.diff(old)
    val inserted = owcOffering.operations.filter(o => toBeInserted.contains(o.uuid))
      .map(createOwcOperation(_))
      .count(_.isDefined) == toBeInserted.length

    deleted && updated && inserted
  }

  /**
    * pre Check for OwcContents Update For an OwcOffering, will delete, update and insert
    * required contents in order to make OwcOffering Update consistent
    *
    * @param owcOffering
    * @return
    */
  def preUpdateCheckContentsForOffering(owcOffering: OwcOffering): Boolean = {

    // get current list,
    val current: List[UUID] = owcOffering.contents.map(_.uuid)

    // get old list,
    val old: List[UUID] = findOwcOfferingByUuid(owcOffering.uuid)
      .map(o => o.contents.map(_.uuid)).getOrElse(List())

    // in old but not current -> delete
    val toBeDeleted = old.diff(current)
    val deleted = owcOffering.contents.filter(o => toBeDeleted.contains(o.uuid))
      .exists(owcPropertiesDAO.deleteOwcContent(_))

    // in both lists -> update
    val toBeUpdated = current.intersect(old)
    val updated = owcOffering.contents.filter(o => toBeUpdated.contains(o.uuid))
      .map(owcPropertiesDAO.updateOwcContent(_))
      .count(_.isDefined) == toBeUpdated.length

    // in current but not in old -> insert
    val toBeInserted = current.diff(old)
    val inserted = owcOffering.contents.filter(o => toBeInserted.contains(o.uuid))
      .map(owcPropertiesDAO.createOwcContent(_))
      .count(_.isDefined) == toBeInserted.length

    deleted && updated && inserted
  }

  /**
    * pre Check for OwcStyleSets Update For an OwcOffering, will delete, update and insert
    * required StyleSets in order to make OwcOffering Update consistent
    *
    * @param owcOffering
    * @return
    */
  def preUpdateCheckStyleSetsForOffering(owcOffering: OwcOffering): Boolean = {

    // get current list,
    val current: List[UUID] = owcOffering.styles.map(_.uuid)

    // get old list,
    val old: List[UUID] = findOwcOfferingByUuid(owcOffering.uuid)
      .map(o => o.styles.map(_.uuid)).getOrElse(List())

    // in old but not current -> delete
    val toBeDeleted = old.diff(current)
    val deleted = owcOffering.styles.filter(o => toBeDeleted.contains(o.uuid))
      .exists(owcPropertiesDAO.deleteOwcStyleSet(_))

    // in both lists -> update
    val toBeUpdated = current.intersect(old)
    val updated = owcOffering.styles.filter(o => toBeUpdated.contains(o.uuid))
      .map(owcPropertiesDAO.updateOwcStyleSet(_))
      .count(_.isDefined) == toBeUpdated.length

    // in current but not in old -> insert
    val toBeInserted = current.diff(old)
    val inserted = owcOffering.styles.filter(o => toBeInserted.contains(o.uuid))
      .map(owcPropertiesDAO.createOwcStyleSet(_))
      .count(_.isDefined) == toBeInserted.length

    deleted && updated && inserted
  }

  /**
    * update OwcOffering and dependent operations, contents, styles
    *
    * @param owcOffering
    * @return
    */
  def updateOwcOfferings(owcOffering: OwcOffering): Option[OwcOffering] = {

    // for each operations, contents and styles
    val preUpdateCheckOperations = preUpdateCheckOperationsForOffering(owcOffering)
    val preUpdateCheckContents = preUpdateCheckContentsForOffering(owcOffering)
    val preUpdateCheckStyleSets = preUpdateCheckStyleSetsForOffering(owcOffering)

    if (preUpdateCheckOperations &&
      preUpdateCheckContents &&
      preUpdateCheckStyleSets) {

      db.withTransaction {
        implicit connection => {

          val rowCount = SQL(
            s"""
          update into $tableOwcOfferings values (
            {uuid}, {code}, {operations}, {contents}, {styles}
          )
        """).on(
            'uuid -> owcOffering.uuid.toString,
            'code -> owcOffering.code.toString,
            'operations -> owcOffering.operations.map(_.uuid.toString).mkString(":"),
            'contents -> owcOffering.contents.map(_.uuid.toString).mkString(":"),
            'styles -> owcOffering.styles.map(_.uuid.toString).mkString(":")
          ).executeUpdate()

          rowCount match {
            case 1 => Some(owcOffering)
            case _ => logger.error(s"OwcOffering couldn't be updated")
              None
          }
        }
      }
    } else {
      logger.error(s"Precondition failed, won't update OwcOffering")
      None
    }
  }

  /**
    * deletes an offering and its corresponding operations, tries to eliminate orphaned operations
    *
    * @param owcOffering
    * @return
    */
  def deleteOwcOffering(owcOffering: OwcOffering): Boolean = {

    val preDeleteCheckOperation = if (owcOffering.operations.nonEmpty) {
      owcOffering.operations.exists(deleteOwcOperation(_))
    } else {
      true
    }

    val preDeleteCheckContents = if (owcOffering.contents.nonEmpty) {
      owcOffering.contents.exists(owcPropertiesDAO.deleteOwcContent(_))
    } else {
      true
    }

    val preDeleteCheckStyleSets = if (owcOffering.styles.nonEmpty) {
      owcOffering.styles.exists(owcPropertiesDAO.deleteOwcStyleSet(_))
    } else {
      true
    }

    if (preDeleteCheckOperation && preDeleteCheckContents && preDeleteCheckStyleSets) {

      db.withTransaction {
        implicit connection => {

          val rowCount = SQL(s"delete from $tableOwcOfferings where uuid = {uuid}").on(
            'uuid -> owcOffering.uuid.toString
          ).executeUpdate()

          rowCount match {
            case 1 => true
            case _ =>
              logger.error(s"OwcOffering couldn't be deleted")
              false
          }

        }
      }
    } else {
      logger.error(s"Precondition failed, won't delete OwcOffering")
      false
    }
  }

  /**
    * finds owc props like author, category, ink, content, stylesets, operation relation via its uuids
    * obviously this could be further generalised
    *
    * @param propertiesUuid A String, which may contain 0, 1 or more UUIDs, multiple UUIDs must be separated by ':' colons
    * @param a              implicit type evidence
    * @tparam A Type of OwcAuthor, OwcCategory, OwcLink, OwcContent, OwcStyleSet, OwcOperation
    * @return Sequence of objects of type A if found, otherwise empty Seq
    */
  def findByPropertiesUUID[A](propertiesUuid: Option[String])(implicit a: A): Seq[A] = {

    import utils.StringUtils._

    val values = propertiesUuid.map(_.split(":").toSeq).map(
      potUuids => {
        val uuids = potUuids.map(_.toUuidOption).filter(_.isDefined).map(_.get)
        uuids.map { uid =>
          a match {
            case a: OwcAuthor => owcPropertiesDAO.findOwcAuthorByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcCategory => owcPropertiesDAO.findOwcCategoriesByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcLink => owcPropertiesDAO.findOwcLinksByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcContent => owcPropertiesDAO.findOwcContentsByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcStyleSet => owcPropertiesDAO.findOwcStyleSetsByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcOperation => findOwcOperationByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcOffering => findOwcOfferingByUuid(uid).asInstanceOf[Option[A]]
            case _ => throw new InvalidClassException("evidence parameter type not supported, only ")
          }

        }.filter(_.isDefined).map(_.get)
      }
    )
    values.getOrElse(Seq())
  }

  //  /**
  //    * get OwcOfferings for a featuretype (OwcEntry or OwcDocument)
  //    * @param featureTypeId
  //    * @return
  //    */
  //  def findOwcOfferingsForOwcEntry(featureTypeId: String) : Seq[OwcOffering] = {
  //    db.withConnection { implicit connection =>
  //      SQL(
  //        s"""SELECT
  //           |o.uuid as uuid, o.offering_type as offering_type, o.code as code, o.content as content
  //           |FROM $tableOwcOfferings o JOIN $tableOwcEntriesHasOwcOfferings eof ON o.uuid=eof.owc_offerings_uuid
  //           |WHERE eof.owc_feature_types_as_entry_id={owc_feature_types_as_entry_id}""".stripMargin).on(
  //        'owc_feature_types_as_entry_id -> featureTypeId
  //      )
  //        .as(owcOfferingParser *)
  //    }
  //  }
  //
  //  /**
  //    * finds owc operations from the offerings relation
  //    *
  //    * @param offeringUuid
  //    * @return
  //    */
  //  def findOwcOperationsByOfferingUUID(offeringUuid: UUID): Seq[OwcOperation] = {
  //    db.withConnection { implicit connection =>
  //      SQL(
  //        s"""SELECT op.uuid as uuid, op.code as code, op.method as method, op.content_type as content_type,
  //           | op.href as href, op.request_content_type as request_content_type, op.request_post_data as request_post_data,
  //           | op.result_content_type as result_content_type, op.result_data as result_data
  //           | FROM $tableOwcOperations op JOIN $tableOwcOfferingsHasOwcOperations ofop ON op.uuid=ofop.owc_operations_uuid
  //           | WHERE ofop.owc_offerings_uuid={uuid}""".stripMargin).on(
  //        'uuid -> offeringUuid.toString
  //      )
  //        .as(owcOperationParser *)
  //    }
  //  }

}
