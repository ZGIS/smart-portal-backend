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

import anorm.SqlParser.{get, str}
import anorm.{RowParser, SQL, ~}
import info.smart.models.owc100._
import utils.ClassnameLogger

/** *********
  * OwcOffering
  * **********/
object OwcOfferingDAO extends ClassnameLogger {

  /**
    * Parse a OwcOperation from a ResultSet
    */
  private def owcOfferingParser(implicit connection: Connection): RowParser[OwcOffering] = {
    str("owc_offerings.uuid") ~
      str("owc_offerings.code") ~
      get[Option[String]]("owc_offerings.operations") ~
      get[Option[String]]("owc_offerings.contents") ~
      get[Option[String]]("owc_offerings.styles") map {
      case uuid ~ code ~ operationsUuids ~ contentsUuids ~ stylesUuids =>
        OwcOffering(
          new URL(code),
          operations = operationsUuids.map(u => findByPropertiesUUID[OwcOperation](Some(u))(OwcOperationEvidence, connection).toList).getOrElse(List()),
          contents = contentsUuids.map(u => findByPropertiesUUID[OwcContent](Some(u))(OwcContentEvidence, connection).toList).getOrElse(List()),
          styles = stylesUuids.map(u => findByPropertiesUUID[OwcStyleSet](Some(u))(OwcStyleSetEvidence, connection).toList).getOrElse(List()),
          uuid = UUID.fromString(uuid)
        )
    }
  }

  /**
    * get all the offerings
    *
    * @return
    */
  def getAllOwcOfferings(implicit connection: Connection): Seq[OwcOffering] = {
    SQL(s"select owc_offerings.* from $tableOwcOfferings").as(owcOfferingParser *)
  }

  /**
    * finds the distinct offering by uuid
    *
    * @param uuid
    * @return
    */
  def findOwcOfferingByUuid(uuid: UUID)(implicit connection: Connection): Option[OwcOffering] = {
    SQL(s"""select owc_offerings.* from $tableOwcOfferings where uuid = '${uuid.toString}'""").as(owcOfferingParser.singleOpt)
  }

  /**
    * takes a list of operations (e.g. for createOfferring) and tests that non of these exist,
    * and if so creates them in the database and returns success of the overall create
    *
    * @param operations
    * @return
    */
  private def preCreateCheckOperations(operations: List[OwcOperation])(implicit connection: Connection): Boolean = {
    if (operations.nonEmpty) {
      val uuidString = operations.map(_.uuid.toString).mkString(":")
      val exists = findByPropertiesUUID[OwcOperation](Some(uuidString))(OwcOperationEvidence, connection).toList.nonEmpty
      if (exists) {
        logger.error(s"(createOffering/operations) OwcOperation with UUID one of: ${uuidString} exists already, recommend abort")
        false
      } else {
        val insert = operations.map(OwcOperationDAO.createOwcOperation(_)).filter(_.isDefined)
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
  private def preCreateCheckContents(contents: List[OwcContent])(implicit connection: Connection): Boolean = {
    if (contents.nonEmpty) {
      val uuidString = contents.map(_.uuid.toString).mkString(":")
      val exists = findByPropertiesUUID[OwcContent](Some(uuidString))(OwcContentEvidence, connection).toList.nonEmpty
      if (exists) {
        logger.error(s"(createOffering/contents) OwcContent with UUID one of: ${uuidString} exists already, recommend abort")
        false
      } else {
        val insert = contents.map(OwcContentDAO.createOwcContent(_)).filter(_.isDefined)
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
  private def preCreateCheckStyleSets(styles: List[OwcStyleSet])(implicit connection: Connection): Boolean = {
    if (styles.nonEmpty) {
      val uuidString = styles.map(_.uuid.toString).mkString(":")
      val exists = findByPropertiesUUID[OwcStyleSet](Some(uuidString))(OwcStyleSetEvidence, connection).toList.nonEmpty
      if (exists) {
        logger.error(s"(createOffering/styles) OwcStyleSet with UUID one of: ${uuidString} exists already, recommend abort")
        false
      } else {
        val insert = styles.map(OwcStyleSetDAO.createOwcStyleSet(_)).filter(_.isDefined)
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
  def createOwcOffering(owcOffering: OwcOffering)(implicit connection: Connection): Option[OwcOffering] = {

    if (preCreateCheckOperations(owcOffering.operations) &&
      preCreateCheckContents(owcOffering.contents) &&
      preCreateCheckStyleSets(owcOffering.styles)) {

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
          logger.error("OwcOperation couldn't be created")
          None
      }
    } else {
      logger.error("Precondition failed, won't create OwcOffering")
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
  private def preUpdateCheckOperationsForOffering(owcOffering: OwcOffering)(implicit connection: Connection): Boolean = {

    // get current list,
    val current: List[UUID] = owcOffering.operations.map(_.uuid)

    // get old list,
    val old: List[UUID] = findOwcOfferingByUuid(owcOffering.uuid)
      .map(o => o.operations.map(_.uuid)).getOrElse(List())

    // in old but not current -> delete
    val toBeDeleted = old.diff(current)
    val deleted = owcOffering.operations.filter(o => toBeDeleted.contains(o.uuid))
      .map(OwcOperationDAO.deleteOwcOperation(_))
      .count(_ == true) == toBeDeleted.length

    // in both lists -> update
    val toBeUpdated = current.intersect(old)
    val updated = owcOffering.operations.filter(o => toBeUpdated.contains(o.uuid))
      .map(OwcOperationDAO.updateOwcOperation(_))
      .count(_.isDefined) == toBeUpdated.length

    // in current but not in old -> insert
    val toBeInserted = current.diff(old)
    val inserted = owcOffering.operations.filter(o => toBeInserted.contains(o.uuid))
      .map(OwcOperationDAO.createOwcOperation(_))
      .count(_.isDefined) == toBeInserted.length

    if (deleted && updated && inserted) {
      true
    } else {
      logger.error(s"(updateOffering/operation) one of deleted: $deleted , updated: $updated , inserted: $inserted not complete, recommend abort")
      false
    }
  }

  /**
    * pre Check for OwcContents Update For an OwcOffering, will delete, update and insert
    * required contents in order to make OwcOffering Update consistent
    *
    * @param owcOffering
    * @return
    */
  private def preUpdateCheckContentsForOffering(owcOffering: OwcOffering)(implicit connection: Connection): Boolean = {

    // get current list,
    val current: List[UUID] = owcOffering.contents.map(_.uuid)

    // get old list,
    val old: List[UUID] = findOwcOfferingByUuid(owcOffering.uuid)
      .map(o => o.contents.map(_.uuid)).getOrElse(List())

    // in old but not current -> delete
    val toBeDeleted = old.diff(current)
    val deleted = owcOffering.contents.filter(o => toBeDeleted.contains(o.uuid))
      .map(OwcContentDAO.deleteOwcContent(_))
      .count(_ == true) == toBeDeleted.length

    // in both lists -> update
    val toBeUpdated = current.intersect(old)
    val updated = owcOffering.contents.filter(o => toBeUpdated.contains(o.uuid))
      .map(OwcContentDAO.updateOwcContent(_))
      .count(_.isDefined) == toBeUpdated.length

    // in current but not in old -> insert
    val toBeInserted = current.diff(old)
    val inserted = owcOffering.contents.filter(o => toBeInserted.contains(o.uuid))
      .map(OwcContentDAO.createOwcContent(_))
      .count(_.isDefined) == toBeInserted.length

    if (deleted && updated && inserted) {
      true
    } else {
      logger.error(s"(updateOffering/contents) one of deleted: $deleted , updated: $updated , inserted: $inserted not complete, recommend abort")
      false
    }
  }

  /**
    * pre Check for OwcStyleSets Update For an OwcOffering, will delete, update and insert
    * required StyleSets in order to make OwcOffering Update consistent
    *
    * @param owcOffering
    * @return
    */
  private def preUpdateCheckStyleSetsForOffering(owcOffering: OwcOffering)(implicit connection: Connection): Boolean = {

    // get current list,
    val current: List[UUID] = owcOffering.styles.map(_.uuid)

    // get old list,
    val old: List[UUID] = findOwcOfferingByUuid(owcOffering.uuid)
      .map(o => o.styles.map(_.uuid)).getOrElse(List())

    // in old but not current -> delete
    val toBeDeleted = old.diff(current)
    val deleted = owcOffering.styles.filter(o => toBeDeleted.contains(o.uuid))
      .map(OwcStyleSetDAO.deleteOwcStyleSet(_))
      .count(_ == true) == toBeDeleted.length

    // in both lists -> update
    val toBeUpdated = current.intersect(old)
    val updated = owcOffering.styles.filter(o => toBeUpdated.contains(o.uuid))
      .map(OwcStyleSetDAO.updateOwcStyleSet(_))
      .count(_.isDefined) == toBeUpdated.length

    // in current but not in old -> insert
    val toBeInserted = current.diff(old)
    val inserted = owcOffering.styles.filter(o => toBeInserted.contains(o.uuid))
      .map(OwcStyleSetDAO.createOwcStyleSet(_))
      .count(_.isDefined) == toBeInserted.length

    if (deleted && updated && inserted) {
      true
    } else {
      logger.error(s"(updateOffering/styles) one of deleted: $deleted , updated: $updated , inserted: $inserted not complete, recommend abort")
      false
    }
  }

  /**
    * update OwcOffering and dependent operations, contents, styles
    *
    * @param owcOffering
    * @return
    */
  def updateOwcOffering(owcOffering: OwcOffering)(implicit connection: Connection): Option[OwcOffering] = {

    // for each operations, contents and styles
    val preUpdateCheckOperations = preUpdateCheckOperationsForOffering(owcOffering)
    val preUpdateCheckContents = preUpdateCheckContentsForOffering(owcOffering)
    val preUpdateCheckStyleSets = preUpdateCheckStyleSetsForOffering(owcOffering)

    if (preUpdateCheckOperations &&
      preUpdateCheckContents &&
      preUpdateCheckStyleSets) {

      val rowCount = SQL(
        s"""
          update $tableOwcOfferings set
          code = {code},
          operations = {operations},
          contents = {contents},
          styles = {styles}
          where uuid = {uuid}
        """).on(
        'uuid -> owcOffering.uuid.toString,
        'code -> owcOffering.code.toString,
        'operations -> owcOffering.operations.map(_.uuid.toString).mkString(":"),
        'contents -> owcOffering.contents.map(_.uuid.toString).mkString(":"),
        'styles -> owcOffering.styles.map(_.uuid.toString).mkString(":")
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcOffering)
        case _ => logger.error("OwcOffering couldn't be updated")
          None
      }
    } else {
      logger.error("Precondition failed, won't update OwcOffering")
      None
    }
  }

  /**
    * deletes an offering and its corresponding operations, tries to eliminate orphaned operations
    *
    * @param owcOffering
    * @return
    */
  def deleteOwcOffering(owcOffering: OwcOffering)(implicit connection: Connection): Boolean = {

    val preDeleteCheckOperation = if (owcOffering.operations.nonEmpty) {
      owcOffering.operations.map(
        o => OwcOperationDAO.deleteOwcOperation(o)).count(_ == true) == owcOffering.operations.size
    } else {
      true
    }

    val preDeleteCheckContents = if (owcOffering.contents.nonEmpty) {
      owcOffering.contents.map(
        o => OwcContentDAO.deleteOwcContent(o)).count(_ == true) == owcOffering.contents.size
    } else {
      true
    }

    val preDeleteCheckStyleSets = if (owcOffering.styles.nonEmpty) {
      owcOffering.styles.map (
        o => OwcStyleSetDAO.deleteOwcStyleSet(o)).count(_ == true) == owcOffering.styles.size
    } else {
      true
    }

    if (preDeleteCheckOperation && preDeleteCheckContents && preDeleteCheckStyleSets) {
      val rowCount = SQL(s"delete from $tableOwcOfferings where uuid = {uuid}").on(
        'uuid -> owcOffering.uuid.toString
      ).executeUpdate()

      rowCount match {
        case 1 => true
        case _ =>
          logger.error("OwcOffering couldn't be deleted")
          false
      }
    } else {
      logger.error("Precondition failed, won't delete OwcOffering")
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
  def findByPropertiesUUID[A](propertiesUuid: Option[String])(implicit a: A, connection: Connection): Seq[A] = {

    import utils.StringUtils._

    val values = propertiesUuid.map(_.split(":").toSeq).map(
      potUuids => {
        val uuids = potUuids.map(_.toUuidOption).filter(_.isDefined).map(_.get)
        uuids.map { uid =>
          a match {
            case a: OwcAuthor => OwcAuthorDAO.findOwcAuthorByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcCategory => OwcCategoryDAO.findOwcCategoryByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcLink => OwcLinkDAO.findOwcLinkByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcContent => OwcContentDAO.findOwcContentByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcCreatorApplication => OwcCreatorApplicationDAO.findOwcCreatorApplicationByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcCreatorDisplay => OwcCreatorDisplayDAO.findOwcCreatorDisplayByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcStyleSet => OwcStyleSetDAO.findOwcStyleSetByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcOperation => OwcOperationDAO.findOwcOperationByUuid(uid).asInstanceOf[Option[A]]
            case a: OwcOffering => findOwcOfferingByUuid(uid).asInstanceOf[Option[A]]
            case _ => throw new InvalidClassException(s"The evidence parameter type ${a.getClass.getCanonicalName} not supported here")
          }

        }.filter(_.isDefined).map(_.get)
      }
    )
    values.getOrElse(Seq())
  }
}
