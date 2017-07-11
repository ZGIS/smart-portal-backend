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
import java.time.OffsetDateTime

import anorm.SqlParser.{get, str}
import anorm.{RowParser, SQL, ~}
import info.smart.models.owc100._
import models.users.User
import utils.{ClassnameLogger, GeoDateParserUtils}

/**
  * OwcContextDAO - store and retrieve OWS Context Documents [[OwcContext]]
  * An OWC document is an extended FeatureCollection, where the features (aka resources) hold a variety of metadata
  * about the things they provide the context for (i.e. other data sets, services, metadata records)
  * OWC documents do not duplicate a CSW MD_Metadata record, but a collection of referenced resources;
  */

object OwcContextDAO extends ClassnameLogger {

  /**
    * Parse an OwcContext from a ResultSet
    */
  private def owcContextParser(implicit connection: Connection): RowParser[OwcContext] = {
    str("id") ~
      get[Option[String]]("area_of_interest") ~
      get[Option[String]]("spec_reference") ~
      get[Option[String]]("context_metadata") ~
      str("language") ~
      str("title") ~
      get[Option[String]]("subtitle") ~
      get[OffsetDateTime]("update_date") ~
      get[Option[String]]("authors") ~
      get[Option[String]]("publisher") ~
      get[Option[String]]("creator_application") ~
      get[Option[String]]("creator_display") ~
      get[Option[String]]("rights") ~
      get[Option[String]]("time_interval_of_interest") ~
      get[Option[String]]("keyword") map {
      case
        id ~
          bboxText ~
          specReferenceUuids ~
          contextMetaUuids ~
          language ~
          title ~
          subtitle ~
          updateDate ~
          authorsUuids ~
          publisher ~
          creatorAppUuid ~
          creatorDispUuid ~
          rights ~
          temporalText ~
          keywordUuids
      => {
        OwcContext(
          id = new URL(id),
          areaOfInterest = GeoDateParserUtils.createOptionalBbox(bboxText),
          specReference = specReferenceUuids.map(u =>
            OwcOfferingDAO.findByPropertiesUUID[OwcLink](Some(u))(OwcLinkEvidence, connection).toList).getOrElse(List()), // aka links.profiles[] & rel=profile
          contextMetadata = contextMetaUuids.map(u =>
            OwcOfferingDAO.findByPropertiesUUID[OwcLink](Some(u))(OwcLinkEvidence, connection).toList).getOrElse(List()), // aka links.via[] & rel=via
          language = language,
          title = title,
          subtitle = subtitle,
          updateDate = updateDate,
          author = authorsUuids.map(u => OwcOfferingDAO.findByPropertiesUUID[OwcAuthor](Some(u))(OwcAuthorEvidence, connection).toList).getOrElse(List()),
          publisher = publisher,
          creatorApplication = None,
          creatorDisplay = None,
          rights = rights,
          timeIntervalOfInterest = GeoDateParserUtils.parseOffsetDateString(temporalText),
          keyword = keywordUuids.map(u =>
            OwcOfferingDAO.findByPropertiesUUID[OwcCategory](Some(u))(OwcCategoryEvidence, connection).toList).getOrElse(List()),
          resource = OwcResourceDAO.findOwcResourcesForOwcContext(id.toString).toList)
      }
    }
  }

  /**
    * get all OwcContexts
    *
    * @return
    */
  def getAllOwcContexts(implicit connection: Connection): Seq[OwcContext] = {
    SQL(s"select * from $tableOwcContexts").as(owcContextParser *)
  }

  /**
    * find an OwcContext by its id
    *
    * @param owcContextId
    * @return
    */
  def findOwcContextsById(owcContextId: String)(implicit connection: Connection): Option[OwcContext] = {
    SQL(
      s"""select * from $tableOwcContexts
         |where id = {id}""".stripMargin).on(
      'id -> owcContextId
    ).as(owcContextParser.singleOpt)
  }

  /**
    * find an OwcContext by its id as [[URL]]
    *
    * @param owcContextId
    * @param connection
    * @return
    */
  def findOwcContextsById(owcContextId: URL)(implicit connection: Connection): Option[OwcContext] = {
    findOwcContextsById(owcContextId.toString)
  }

  /**
    * find an OwcContext by its id and user
    *
    * @param owcContextId
    * @param user
    * @return
    */
  def findOwcContextByIdAndUser(owcContextId: String, user: User)(implicit connection: Connection): Option[OwcContext] = {
    SQL(
      s"""select con.*
         |FROM $tableOwcContexts con JOIN $tableUserHasOwcContextRights u ON con.id=u.owc_context_id
         |where con.id = {id} AND AND u.users_accountsubject = {account_subject}""".stripMargin).on(
      'id -> owcContextId,
      'account_subject -> user.accountSubject
    ).as(owcContextParser.singleOpt)
  }

  /**
    * find an OwcContext by its id [[URL]] and user [[User]]
    *
    * @param owcContextId
    * @param user
    * @param connection
    * @return
    */
  def findOwcContextByIdAndUser(owcContextId: URL, user: User)(implicit connection: Connection): Option[OwcContext] = {
    findOwcContextByIdAndUser(owcContextId.toString, user)
  }

  /**
    * find an OwcContext by user
    *
    * @param user
    * @return
    */
  def findOwcContextsByUser(user: User)(implicit connection: Connection): Seq[OwcContext] = {
    SQL(
      s"""select con.*
         |FROM $tableOwcContexts con JOIN $tableUserHasOwcContextRights u ON con.id=u.owc_context_id
         |where u.users_accountsubject = {account_subject}""".stripMargin).on(
      'account_subject -> user.accountSubject
    ).as(owcContextParser *)
  }

  /**
    * find all OwcContexts by visibility
    *
    * @param visibility 0: user-owned/private, 1: organisation, 2: public
    * @return
    */
  def findOwcContextsByVisibility(visibility: Int)(implicit connection: Connection): Seq[OwcContext] = {
    SQL(
      s"""select con.*
         |FROM $tableOwcContexts con JOIN $tableUserHasOwcContextRights u ON con.id=u.owc_context_id
         |where u.visibility >= {visibility}""".stripMargin).on(
      'visibility -> visibility
    ).as(owcContextParser *)
  }

  /**
    * get all publicly visible OwcContexts
    *
    * @return
    */
  def getAllPublicOwcContexts(implicit connection: Connection): Seq[OwcContext] = {
    findOwcContextsByVisibility(2)
  }

  /**
    * find an OwcContext by its id
    *
    * @param owcContextId
    * @return
    */
  def findPublicOwcContextsById(owcContextId: String)(implicit connection: Connection): Option[OwcContext] = {
    SQL(
      s"""select con.*
         |FROM $tableOwcContexts con JOIN $tableUserHasOwcContextRights u ON con.id=u.owc_context_id
         |where con.id = {id} AND u.visibility >= {visibility}""".stripMargin).on(
      'id -> owcContextId,
      'visibility -> 2
    ).as(owcContextParser.singleOpt)
  }

  /**
    * find OwcContexts by user and type
    *
    * @param user
    * @param rightsRelationType DEFAULT: user personal default, CUSTOM: general purpose
    * @return
    */
  def findOwcContextsByUserAndType(user: User, rightsRelationType: String)(implicit connection: Connection): Seq[OwcContext] = {
    SQL(
      s"""select con.*
         | FROM $tableOwcContexts con JOIN $tableUserHasOwcContextRights u ON con.id=u.owc_context_id
         | WHERE u.users_accountsubject = {account_subject}
         | AND u.rights_relation_type = {rights_relation_type} """.stripMargin).on(
      'account_subject -> user.accountSubject,
      'rights_relation_type -> rightsRelationType
    ).as(owcContextParser *)
  }

  def findUserDefaultOwcContext(user: User)(implicit connection: Connection): Option[OwcContext] = {
    findOwcContextsByUserAndType(user, "DEFAULT").headOption
  }

  /**
    * create/insert into db Owc Context
    *
    * @param owcContext
    * @param user
    * @param visibility
    * @param rightsRelationType
    * @return
    */
  def createOwcContext(owcContext: OwcContext, user: User, visibility: Int, rightsRelationType: String)(implicit connection: Connection): Option[OwcContext] = {

    val accountSubject = user.accountSubject

    // FIXME: not finished

    val preCreateRightsCheck = false

    val preCreateCheckOwcAuthors = false
    val preCreateCheckOwcLinks = false
    val preCreateCheckOwcResources = false
    val preCreateCheckOwcCategories = false

    if (preCreateRightsCheck && preCreateCheckOwcAuthors && preCreateCheckOwcLinks &&
      preCreateCheckOwcResources && preCreateCheckOwcCategories) {

      val resourceDepsInsertCount = owcContext.resource.map {
        owcResource =>
          SQL(
            s"""insert into $tableOwcContextHasOwcResources  values (
               |{owc_context_id}, {owc_resource_id}
               |)
                 """.stripMargin).on(
            'owc_context_id -> owcContext.id.toString,
            'owc_resource_id -> owcResource.id.toString
          ).executeUpdate()
      }.sum

      val rightsInsert = SQL(
        s"""insert into $tableUserHasOwcContextRights values (
           |{accountsubject}, {owcContextId}, {rights_relation_type}, {visibility}
           |)""".stripMargin).on(
        'accountsubject -> accountSubject,
        'owcContextId -> owcContext.id.toString,
        'rights_relation_type -> rightsRelationType,
        'visibility -> visibility
      ).executeUpdate()

      rightsInsert match {
        case 1 => Some(owcContext)
        case _ =>
          logger.error(s"OwcContext couldn't be created")
          None
      }
    } else {
      logger.error(s"Precondition failed, won't create OwcContext")
      None
    }
  }

  /**
    * create Users Default collection (OwcContext) aka Owc Context, visibility 0 > private,
    * rightsRelationType "CUSTOM"
    *
    * @param owcContext
    * @param user
    * @return
    */
  def createCustomOwcContext(owcContext: OwcContext, user: User)(implicit connection: Connection): Option[OwcContext] = {
    createOwcContext(owcContext, user, 0, "CUSTOM")
  }


  /**
    * create Users Default collection aka Owc Context, visibility 0 > private,
    * rightsRelationType "DEFAULT"
    *
    * @param owcContext
    * @param user
    * @return
    */
  def createUsersDefaultOwcContext(owcContext: OwcContext, user: User)(implicit connection: Connection): Option[OwcContext] = {
    createOwcContext(owcContext, user, 0, "DEFAULT")
  }

  /**
    *
    * @param owcContext
    * @param user
    * @param visibility
    * @param rightsRelationType
    * @return
    */
  def updateOwcContextRightsAndVisibility(owcContext: OwcContext,
                                          user: User,
                                          visibility: Int,
                                          rightsRelationType: String)(implicit connection: Connection): Option[OwcContext] = {

    val accountSubject = user.accountSubject
    // FIXME: not finished

    val preUpdateRightsCheck = false

    if (preUpdateRightsCheck) {
      val rowCount = SQL(
        s"""UPDATE $tableUserHasOwcContextRights SET
           |rights_relation_type = {rights_relation_type},
           |visibility = {visibility}
           |WHERE users_accountsubject = {account_subject} AND owcContextId = {owcContextId}
           |""".stripMargin).on(
        'rights_relation_type -> rightsRelationType,
        'visibility -> visibility,
        'account_subject -> accountSubject,
        'owcContextId -> owcContext.id.toString
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcContext)
        case _ => None
      }
    } else {
      None
    }
  }

  /**
    *
    * @param owcContext
    * @param user
    * @return
    */
  def updateOwcContext(owcContext: OwcContext, user: User)(implicit connection: Connection): Option[OwcContext] = {

    val accountSubject = user.accountSubject
    // FIXME: not finished

    val preUpdateRightsCheck = false

    val preUpdateCheckOwcAuthors = false
    val preUpdateCheckOwcLinks = false
    val preUpdateCheckOwcResources = false
    val preUpdateCheckOwcCategories = false

    if (preUpdateRightsCheck && preUpdateCheckOwcAuthors && preUpdateCheckOwcLinks &&
      preUpdateCheckOwcResources && preUpdateCheckOwcCategories) {

      val rowCount = 0

      rowCount match {
        case 1 => Some(owcContext)
        case _ => logger.error(s"OwcContext couldn't be updated")
          None
      }
    } else {
      logger.error(s"Precondition failed, won't update OwcContext")
      None
    }

  }

  /**
    * delete a full owc context hierarchically
    *
    * @param owcContext
    * @return
    */
  def deleteOwcContext(owcContext: OwcContext, user: User)(implicit connection: Connection): Boolean = {

    val accountSubject = user.accountSubject
    // FIXME: not finished

    val preDeleteRightsCheck = false
    val preDeleteCheckOwcAuthors = false
    val preDeleteCheckOwcLinks = false
    val preDeleteCheckOwcResources = false
    val preDeleteCheckOwcCategories = false

    if (preDeleteRightsCheck && preDeleteCheckOwcAuthors && preDeleteCheckOwcLinks &&
      preDeleteCheckOwcResources && preDeleteCheckOwcCategories) {

      val rowCount = SQL(s"delete from $tableOwcContexts where id = {id}").on(
        'id -> owcContext.id.toString
      ).executeUpdate()

      rowCount match {
        case 1 => true
        case _ =>
          logger.error(s"OwcContext couldn't be deleted")
          false
      }
    } else {
      logger.error(s"Precondition failed, won't delete OwcContext")
      false
    }
  }
}
