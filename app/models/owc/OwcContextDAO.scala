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
import java.util.UUID

import anorm.SqlParser.{get, str}
import anorm.{RowParser, SQL, ~}
import info.smart.models.owc100._
import models.users.User
import utils.{ClassnameLogger, GeoDateParserUtils, OwcGeoJsonFixes}

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
          creatorApplication = creatorAppUuid.map(u => OwcCreatorApplicationDAO.findOwcCreatorApplicationByUuid(UUID.fromString(u))).getOrElse(None),
          creatorDisplay = creatorDispUuid.map(u => OwcCreatorDisplayDAO.findOwcCreatorDisplayByUuid(UUID.fromString(u))).getOrElse(None),
          rights = rights,
          timeIntervalOfInterest = GeoDateParserUtils.parseOffsetDateString(temporalText),
          keyword = keywordUuids.map(u =>
            OwcOfferingDAO.findByPropertiesUUID[OwcCategory](Some(u))(OwcCategoryEvidence, connection).toList).getOrElse(List()),
          resource = OwcResourceDAO.findOwcResourcesForOwcContext(id.toString).toList)
      }
    }
  }

  /**
    * sort of overriding OwcContext.newOf to provide better styled ID URLs
    *
    * @param owcContext
    * @param newId
    * @param refreshedResources
    * @return
    */
  def refreshedCopy(owcContext: OwcContext, newId: URL, refreshedResources: Option[List[OwcResource]]): OwcContext =
    owcContext.copy(id = newId,
      specReference = owcContext.specReference.map(o => o.newOf),
      contextMetadata = owcContext.contextMetadata.map(o => o.newOf),
      creatorApplication = owcContext.creatorApplication.map(o => o.newOf),
      creatorDisplay = owcContext.creatorDisplay.map(o => o.newOf),
      author = owcContext.author.map(o => o.newOf),
      keyword = owcContext.keyword.map(o => o.newOf),
      resource = refreshedResources.getOrElse(owcContext.resource.map(o => o.newOf())))

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
  def findOwcContextById(owcContextId: URL)(implicit connection: Connection): Option[OwcContext] = {
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
      s"""select $tableOwcContexts.*
         |FROM $tableOwcContexts JOIN $tableUserHasOwcContextRights ON $tableOwcContexts.id=$tableUserHasOwcContextRights.owc_context_id
         |where $tableOwcContexts.id = {id} AND $tableUserHasOwcContextRights.users_accountsubject = {account_subject}""".stripMargin).on(
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
      s"""select $tableOwcContexts.*
         |FROM $tableOwcContexts JOIN $tableUserHasOwcContextRights ON $tableOwcContexts.id=$tableUserHasOwcContextRights.owc_context_id
         |where $tableUserHasOwcContextRights.users_accountsubject = {account_subject}""".stripMargin).on(
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
      s"""select $tableOwcContexts.*
         |FROM $tableOwcContexts JOIN $tableUserHasOwcContextRights ON $tableOwcContexts.id=$tableUserHasOwcContextRights.owc_context_id
         |where $tableUserHasOwcContextRights.visibility >= {visibility}""".stripMargin).on(
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
    * find a publicly visible OwcContext by its id
    *
    * @param owcContextId
    * @return
    */
  def findPublicOwcContextsById(owcContextId: String)(implicit connection: Connection): Option[OwcContext] = {
    SQL(
      s"""select $tableOwcContexts.*
         |FROM $tableOwcContexts JOIN $tableUserHasOwcContextRights ON $tableOwcContexts.id=$tableUserHasOwcContextRights.owc_context_id
         |where $tableOwcContexts.id = {id} AND $tableUserHasOwcContextRights.visibility >= {visibility}""".stripMargin).on(
      'id -> owcContextId,
      'visibility -> 2
    ).as(owcContextParser.singleOpt)
  }

  /**
    * find a publicly visible OwcContext by its id [[URL]]
    *
    * @param owcContextId
    * @param connection
    * @return
    */
  def findPublicOwcContextsById(owcContextId: URL)(implicit connection: Connection): Option[OwcContext] = {
    findPublicOwcContextsById(owcContextId.toString)
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
      s"""select $tableOwcContexts.*
         | FROM $tableOwcContexts JOIN $tableUserHasOwcContextRights ON $tableOwcContexts.id=$tableUserHasOwcContextRights.owc_context_id
         | WHERE $tableUserHasOwcContextRights.users_accountsubject = {account_subject}
         | AND $tableUserHasOwcContextRights.rights_relation_type = {rights_relation_type} """.stripMargin).on(
      'account_subject -> user.accountSubject,
      'rights_relation_type -> rightsRelationType
    ).as(owcContextParser *)
  }

  def findUserDefaultOwcContext(user: User)(implicit connection: Connection): Option[OwcContext] = {
    findOwcContextsByUserAndType(user, "DEFAULT").headOption
  }

  /**
    * takes a list of [[OwcResource]] and tests that non of these exist,
    * and if so creates them in the database and returns success of the overall create
    *
    * @param resources
    * @param connection
    * @return
    */
  def preCreateCheckOwcResources(resources: List[OwcResource])(implicit connection: Connection): Boolean = {
    if (resources.nonEmpty) {
      val idList = resources.map(o => o.id.toString)
      val exists = idList.exists(id => OwcResourceDAO.findOwcResourceById(id).isDefined)
      if (exists) {
        logger.error(s"(createContext/resources) OwcResource with ID one of: ${idList.mkString(" ++ ")} exists already, recommend abort")
        false
      } else {
        val insert = resources.map(OwcResourceDAO.createOwcResource(_)).filter(_.isDefined)
        insert.length == resources.length
      }
    } else {
      true
    }
  }

  /**
    * checks for [[OwcCreatorApplication]] and tests that non of these exist,
    * and if so creates them in the database and returns success of the overall create
    *
    * @param owcContext
    * @param connection
    * @return
    */
  def preCreateCheckOwcCreatorApplication(owcContext: OwcContext)(implicit connection: Connection): Boolean = {
    if (owcContext.creatorApplication.isDefined) {
      val exists = OwcCreatorApplicationDAO.findOwcCreatorApplicationByUuid(owcContext.creatorApplication.get.uuid).isDefined
      if (exists) {
        logger.error("(createOwcContext/creatorApplication) OwcCreatorApplication with UUID: " +
          s"${owcContext.creatorApplication.get.uuid} exists already, won't create OwcCreatorApplication")
        false
      } else {
        OwcCreatorApplicationDAO.createOwcCreatorApplication(owcContext.creatorApplication.get).isDefined
      }
    } else {
      true
    }
  }

  /**
    * checks for [[OwcCreatorDisplay]] and tests that non of these exist,
    * and if so creates them in the database and returns success of the overall create
    *
    * @param owcContext
    * @param connection
    * @return
    */
  def preCreateCheckOwcCreatorDisplay(owcContext: OwcContext)(implicit connection: Connection): Boolean = {
    if (owcContext.creatorDisplay.isDefined) {
      val exists = OwcCreatorDisplayDAO.findOwcCreatorDisplayByUuid(owcContext.creatorDisplay.get.uuid).isDefined
      if (exists) {
        logger.error("(createOwcContext/creatorDisplay) OwcCreatorDisplay with UUID: " +
          s"${owcContext.creatorDisplay.get.uuid} exists already, won't create OwcCreatorDisplay")
        false
      } else {
        OwcCreatorDisplayDAO.createOwcCreatorDisplay(owcContext.creatorDisplay.get).isDefined
      }
    } else {
      true
    }
  }

  /**
    * create/insert into db Owc Context
    *
    * @param owcContextUnfixed should make issue on github for owc geojson and portal backend
    *                          and then remove the fix see [[OwcGeoJsonFixes]]
    * @param user
    * @param visibility
    * @param rightsRelationType
    * @return
    */
  def createOwcContext(owcContextUnfixed: OwcContext, user: User, visibility: Int, rightsRelationType: String)
                      (implicit connection: Connection): Option[OwcContext] = {

    val owcContext = OwcGeoJsonFixes.fixRelPropertyForOwcLinks(owcContextUnfixed)

    val preCreateCheckOwcAuthorsForContext = OwcResourceDAO.preCreateCheckOwcAuthors(owcContext.author)
    val preCreateCheckOwcLinksForContext = OwcResourceDAO.preCreateCheckOwcLinks(owcContext.specReference ++ owcContext.contextMetadata)
    val preCreateCheckOwcResourcesForContext = preCreateCheckOwcResources(owcContext.resource)
    val preCreateCheckOwcCategoriesForContext = OwcResourceDAO.preCreateCheckOwcCategories(owcContext.keyword)
    val preCreateCheckOwcCreatorApplicationForContext = preCreateCheckOwcCreatorApplication(owcContext)
    val preCreateCheckOwcCreatorDisplayForContext = preCreateCheckOwcCreatorDisplay(owcContext)

    if (user.isActive && preCreateCheckOwcAuthorsForContext && preCreateCheckOwcLinksForContext &&
      preCreateCheckOwcResourcesForContext && preCreateCheckOwcCategoriesForContext &&
      preCreateCheckOwcCreatorApplicationForContext && preCreateCheckOwcCreatorDisplayForContext) {

      val owcContextInsert = SQL(
        s"""insert into $tableOwcContexts values (
           |{id},
           |{area_of_interest},
           |{spec_reference},
           |{context_metadata},
           |{language},
           |{title},
           |{subtitle},
           |{update_date},
           |{authors},
           |{publisher},
           |{creator_application},
           |{creator_display},
           |{rights},
           |{time_interval_of_interest},
           |{keyword})""".stripMargin).on(
        'id -> owcContext.id.toString,
        'area_of_interest -> owcContext.areaOfInterest.map(r => GeoDateParserUtils.rectToWkt(r)),
        'spec_reference -> owcContext.specReference.map(_.uuid.toString).mkString(":"), // links.profiles[] and rel=profile
        'context_metadata -> owcContext.contextMetadata.map(_.uuid.toString).mkString(":"), // aka links.via[] & rel=via
        'language -> owcContext.language,
        'title -> owcContext.title,
        'subtitle -> owcContext.subtitle,
        'update_date -> owcContext.updateDate.toLocalDateTime,
        'authors -> owcContext.author.map(_.uuid.toString).mkString(":"),
        'publisher -> owcContext.publisher,
        'creator_application -> owcContext.creatorApplication.map(_.uuid.toString),
        'creator_display -> owcContext.creatorDisplay.map(_.uuid.toString),
        'rights -> owcContext.rights,
        'time_interval_of_interest -> owcContext.timeIntervalOfInterest.map(dates => GeoDateParserUtils.writeOffsetDatesAsDateString(dates)).getOrElse(None),
        'keyword -> owcContext.keyword.map(_.uuid.toString).mkString(":")
      ).executeUpdate()

      // this references an existing owc_context_id (the newly created one from above)
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

      // this references an existing owc_context_id (the newly created one from above)
      val rightsDepsInsert = SQL(
        s"""insert into $tableUserHasOwcContextRights values (
           |{accountsubject}, {owcContextId}, {rights_relation_type}, {visibility}
           |)""".stripMargin).on(
        'accountsubject -> user.accountSubject,
        'owcContextId -> owcContext.id.toString,
        'rights_relation_type -> rightsRelationType,
        'visibility -> visibility
      ).executeUpdate()

      val allInserts = resourceDepsInsertCount + rightsDepsInsert + owcContextInsert
      val testValue = owcContext.resource.size + 1 + 1

      if (allInserts == testValue) {
        Some(owcContext)
      } else {
        logger.error("OwcContext/create: one of " +
          s"resourceDepsInsertCount: $resourceDepsInsertCount (should be ${owcContext.resource.size}) + " +
          s"rightsDepsInsert: $rightsDepsInsert (1) + owcContextInsert: $owcContextInsert (1) failed")
        logger.error("OwcContext couldn't be created")
        // we need to think where to place rollback most appropriately
        connection.rollback()
        None
      }
    } else {
      logger.error("OwcContext/create: one of " +
        s"user.isActive: ${user.isActive} + preCreateCheckOwcAuthorsForContext: $preCreateCheckOwcAuthorsForContext  " +
        s"preCreateCheckOwcResourcesForContext: $preCreateCheckOwcResourcesForContext + " +
        s"preCreateCheckOwcCategoriesForContext: $preCreateCheckOwcCategoriesForContext + " +
        s"preCreateCheckOwcCreatorApplicationForContext: $preCreateCheckOwcCreatorApplicationForContext + " +
        s"preCreateCheckOwcCreatorDisplayForContext: $preCreateCheckOwcCreatorDisplayForContext was not successful")
      logger.error("Precondition failed, won't create OwcContext")
      // we need to think where to place rollback most appropriately
      connection.rollback()
      None
    }
  }

  /**
    * create Users Default collection (OwcContext) aka Owc Context, visibility 0 = private,
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
    * create Users Default collection aka Owc Context, visibility 0 = private,
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
    * update an OwcContext rightsRelationType and visibility in tableUserHasOwcContextRights
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

    /*
    TODO:
    - a) user owns the OwcContext
    - b) if he doesn't own the context, the context should have either visibility to him via same organisation
    - c) user cannot change the rightsRelationType or visibility of own DEFAULT collection
    ... anything else?
     */
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
        'account_subject -> user.accountSubject,
        'owcContextId -> owcContext.id.toString
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcContext)
        case _ => {
          logger.error("OwcContext Rights And Visibility couldn't be updated")
          // we need to think where to place rollback most appropriately
          connection.rollback()
          None
        }
      }
    } else {
      logger.error("Precondition failed, won't update OwcContext Rights And Visibility")
      // we need to think where to place rollback most appropriately
      connection.rollback()
      None
    }
  }

  /**
    * pre Check for resources Update For an [[OwcContext]],
    * will delete, update and insert required [[OwcResource]] in order to make OwcContext Update consistent
    *
    * @param owcContext
    * @param connection
    * @return
    */
  def preUpdateCheckOwcResources(owcContext: OwcContext)(implicit connection: Connection): Boolean = {
    // get current list,
    val current: List[URL] = owcContext.resource.map(_.id)

    logger.trace(s"preUpdateCheckOwcResources: current: List[URL] ${current.map(_.toString).mkString}")

    // get old list,
    val oldOwcContext = findOwcContextById(owcContext.id)
    val old: List[URL] = oldOwcContext.map(o => o.resource.map(_.id)).getOrElse(List())

    logger.trace(s"preUpdateCheckOwcResources: old: List[URL] ${current.map(_.toString).mkString}")

    // in old but not current -> delete
    val toBeDeleted = old.diff(current)

    logger.trace(s"preUpdateCheckOwcResources: toBeDeleted: List[URL] ${current.map(_.toString).mkString}")

    val deleted: Boolean = oldOwcContext.exists { owcContext =>
      owcContext.resource.filter(o => toBeDeleted.contains(o.id))
        .map { owcResource =>
          // delete relation from tableOwcContextHasOwcResources table before finally deleting referenced OwcResource
          val deletedOwcResourceRelation = SQL(
            s"""Delete from $tableOwcContextHasOwcResources where
owc_context_id = {owc_context_id} and owc_resource_id = {owc_resource_id}""".stripMargin).on(
            'owc_context_id -> owcContext.id.toString,
            'owc_resource_id -> owcResource.id.toString
          ).executeUpdate().equals(1)

          val deletedOwcResource = OwcResourceDAO.deleteOwcResource(owcResource)

          deletedOwcResource && deletedOwcResourceRelation
        }.count(_ == true) == toBeDeleted.length
    }

    // in both lists -> update
    val toBeUpdated = current.intersect(old)

    logger.trace(s"preUpdateCheckOwcResources: toBeUpdated: List[URL] ${current.map(_.toString).mkString}")

    val updated = owcContext.resource.filter(o => toBeUpdated.contains(o.id))
      .map(OwcResourceDAO.updateOwcResource(_))
      .count(_.isDefined) == toBeUpdated.length

    // in current but not in old -> insert
    val toBeInserted = current.diff(old)

    logger.trace(s"preUpdateCheckOwcResources: toBeInserted: List[URL] ${current.map(_.toString).mkString}")

    val inserted = owcContext.resource.filter(o => toBeInserted.contains(o.id))
      .map { owcResource =>
        val createdOwcResource = OwcResourceDAO.createOwcResource(owcResource)

        // Now also insert new relation into tableOwcContextHasOwcResources table for the created and to be referenced OwcResource
        val createdOwcResourceRelation = SQL(
          s"""Insert into $tableOwcContextHasOwcResources values ({owc_context_id}, {owc_resource_id})""".stripMargin).on(
          'owc_context_id -> owcContext.id.toString,
          'owc_resource_id -> owcResource.id.toString
        ).executeUpdate().equals(1)

        if (createdOwcResourceRelation) createdOwcResource else None
      }
      .count(_.isDefined) == toBeInserted.length

    if (deleted && updated && inserted) {
      true
    } else {
      logger.error(s"(updateContext/resources) one of deleted: $deleted , updated: $updated , inserted: $inserted not complete, recommend abort")
      false
    }
  }

  /**
    * update check for [[OwcCreatorApplication]] when updating an [[OwcContext]]
    *
    * @param owcContext
    * @param connection
    * @return
    */
  def preUpdateCheckOwcCreatorApplication(owcContext: OwcContext)(implicit connection: Connection): Boolean = {
    if (owcContext.creatorApplication.isDefined) {
      val exists = owcContext.creatorApplication.exists(c => OwcCreatorApplicationDAO.findOwcCreatorApplicationByUuid(c.uuid).isDefined)
      if (exists) {
        val update = owcContext.creatorApplication.map(OwcCreatorApplicationDAO.updateOwcCreatorApplication(_))
        update.isDefined
      } else {
        val insert = owcContext.creatorApplication.map(OwcCreatorApplicationDAO.createOwcCreatorApplication(_))
        insert.isDefined
      }
    } else {
      val toBeDeleted = findOwcContextById(owcContext.id).map(_.creatorApplication).getOrElse(None)
      if (toBeDeleted.isDefined) {
        toBeDeleted.exists(OwcCreatorApplicationDAO.deleteOwcCreatorApplication(_))
      } else {
        true
      }
    }
  }

  /**
    * update check for [[OwcCreatorDisplay]] when updating an [[OwcContext]]
    *
    * @param owcContext
    * @param connection
    * @return
    */
  def preUpdateCheckOwcCreatorDisplay(owcContext: OwcContext)(implicit connection: Connection): Boolean = {
    if (owcContext.creatorDisplay.isDefined) {
      val exists = owcContext.creatorDisplay.map(c => OwcCreatorDisplayDAO.findOwcCreatorDisplayByUuid(c.uuid)).isDefined
      if (exists) {
        val update = owcContext.creatorDisplay.map(OwcCreatorDisplayDAO.updateOwcCreatorDisplay(_))
        update.isDefined
      } else {
        val insert = owcContext.creatorDisplay.map(OwcCreatorDisplayDAO.createOwcCreatorDisplay(_))
        insert.isDefined
      }
    } else {
      val toBeDeleted = findOwcContextById(owcContext.id).map(_.creatorDisplay).getOrElse(None)
      if (toBeDeleted.isDefined) {
        toBeDeleted.exists(OwcCreatorDisplayDAO.deleteOwcCreatorDisplay(_))
      } else {
        true
      }
    }
  }

  /**
    * update an OwcContext with dependent resources and properties links etc hierarchically
    *
    * @param owcContextUnfixed should make issue on github for owc geojson and portal backend
    *                          and then remove the fix see [[OwcGeoJsonFixes]]
    * @param user
    * @return
    */
  def updateOwcContext(owcContextUnfixed: OwcContext, user: User)(implicit connection: Connection): Option[OwcContext] = {

    val owcContext = OwcGeoJsonFixes.fixRelPropertyForOwcLinks(owcContextUnfixed)

    /*
    TODO:
    - a) user owns the OwcContext
    - b) if he doesn't own the context, the context should have either visibility to him via same organisation
    ... anything else?
     */
    val preUpdateRightsCheck = findOwcContextByIdAndUser(owcContext.id, user).isDefined

    val preUpdateCheckOwcAuthorsForOwcContext = OwcResourceDAO.preUpdateCheckOwcAuthors(owcContext)
    val preUpdateCheckOwcLinksForOwcContext = OwcResourceDAO.preUpdateCheckOwcLinks(owcContext)
    val preUpdateCheckOwcResourcesForOwcContext = preUpdateCheckOwcResources(owcContext)
    val preUpdateCheckOwcCategoriesForOwcContext = OwcResourceDAO.preUpdateCheckOwcCategories(owcContext)
    val preUpdateCheckOwcCreatorApplicationForOwcContext = preUpdateCheckOwcCreatorApplication(owcContext)
    val preUpdateCheckOwcCreatorDisplayForOwcContext = preUpdateCheckOwcCreatorDisplay(owcContext)

    if (preUpdateRightsCheck && preUpdateCheckOwcAuthorsForOwcContext && preUpdateCheckOwcLinksForOwcContext &&
      preUpdateCheckOwcResourcesForOwcContext && preUpdateCheckOwcCategoriesForOwcContext &&
      preUpdateCheckOwcCreatorApplicationForOwcContext && preUpdateCheckOwcCreatorDisplayForOwcContext) {

      val rowCount = SQL(
        s"""update $tableOwcContexts set
           |area_of_interest = {area_of_interest},
           |spec_reference = {spec_reference},
           |context_metadata = {context_metadata},
           |language = {language},
           |title = {title},
           |subtitle = {subtitle},
           |update_date = {update_date},
           |authors = {authors},
           |publisher = {publisher},
           |creator_application = {creator_application},
           |creator_display = {creator_display},
           |rights = {rights},
           |time_interval_of_interest = {time_interval_of_interest},
           |keyword = {keyword}
           |where id = {id}
        """.stripMargin).on(
        'id -> owcContext.id.toString,
        'area_of_interest -> owcContext.areaOfInterest.map(r => GeoDateParserUtils.rectToWkt(r)),
        'spec_reference -> owcContext.specReference.map(_.uuid.toString).mkString(":"), // links.profiles[] and rel=profile
        'context_metadata -> owcContext.contextMetadata.map(_.uuid.toString).mkString(":"), // aka links.via[] & rel=via
        'language -> owcContext.language,
        'title -> owcContext.title,
        'subtitle -> owcContext.subtitle,
        'update_date -> owcContext.updateDate.toLocalDateTime,
        'authors -> owcContext.author.map(_.uuid.toString).mkString(":"),
        'publisher -> owcContext.publisher,
        'creator_application -> owcContext.creatorApplication.map(_.uuid.toString),
        'creator_display -> owcContext.creatorDisplay.map(_.uuid.toString),
        'rights -> owcContext.rights,
        'time_interval_of_interest -> owcContext.timeIntervalOfInterest.map(dates => GeoDateParserUtils.writeOffsetDatesAsDateString(dates)).getOrElse(None),
        'keyword -> owcContext.keyword.map(_.uuid.toString).mkString(":")
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcContext)
        case _ => logger.error("OwcContext couldn't be updated")
          // we need to think where to place rollback most appropriately
          connection.rollback()
          None
      }
    } else {
      logger.error("Precondition failed, won't update OwcContext")
      // we need to think where to place rollback most appropriately
      connection.rollback()
      None
    }
  }

  /**
    * update an OwcContext with dependent resources and properties links etc hierarchically
    *
    * @param owcContext
    * @return
    */
  def deleteOwcContext(owcContext: OwcContext, user: User)(implicit connection: Connection): Boolean = {

    /*
    TODO: depends on Extended rights and roles model tbd with GNS
    - a) user owns the OwcContext
    - b) ... if he doesn't own the context, should he be able to delete on behalf of same organisation when shared visibility?
     */
    val preDeleteRightsCheck = findOwcContextByIdAndUser(owcContext.id, user).isDefined

    val preDeleteCheckOwcAuthors = if (owcContext.author.nonEmpty) {
      owcContext.author.map(
        o => OwcAuthorDAO.deleteOwcAuthor(o)).count(_ == true) == owcContext.author.size
    } else {
      true
    }
    val allOwcLinks = owcContext.specReference ++ owcContext.contextMetadata

    val preDeleteCheckOwcLinks = if (allOwcLinks.nonEmpty) {
      allOwcLinks.map(
        o => OwcLinkDAO.deleteOwcLink(o)).count(_ == true) == allOwcLinks.size
    } else {
      true
    }

    val preDeleteCheckOwcCategories = if (owcContext.keyword.nonEmpty) {
      owcContext.keyword.map(
        o => OwcCategoryDAO.deleteOwcCategory(o)).count(_ == true) == owcContext.keyword.size
    } else {
      true
    }

    val preDeleteCheckCreatorApplication = if (owcContext.creatorApplication.isDefined) {
      owcContext.creatorApplication.exists(OwcCreatorApplicationDAO.deleteOwcCreatorApplication(_))
    } else {
      true
    }

    val preDeleteCheckCreatorDisplay = if (owcContext.creatorDisplay.isDefined) {
      owcContext.creatorDisplay.exists(OwcCreatorDisplayDAO.deleteOwcCreatorDisplay(_))
    } else {
      true
    }

    if (preDeleteRightsCheck && preDeleteCheckOwcAuthors && preDeleteCheckOwcLinks &&
      preDeleteCheckOwcCategories && preDeleteCheckCreatorApplication && preDeleteCheckCreatorDisplay) {

      // delete from tableUserHasOwcContextRights table before finally deleting referenced OwcContext
      val rightsDepsDelete = SQL(
        s"""Delete from $tableUserHasOwcContextRights
           |WHERE owc_context_id = {owcContextId}
           |""".stripMargin).on(
        'owcContextId -> owcContext.id.toString
      ).executeUpdate()

      // Now also delete relations from tableOwcContextHasOwcResources table and the Owcresources before finally deleting referenced OwcContext
      // either one by one to be able to measure count, possible alternative just batch where owcContext.id
      val resourceRelationsDeleteCount = owcContext.resource.map {
        owcResource =>
          val rowCount = SQL(
            s"""Delete $tableOwcContextHasOwcResources
               |where owc_context_id = {owc_context_id} and owc_resource_id = {owc_resource_id}
           """.stripMargin).on(
            'owc_context_id -> owcContext.id.toString,
            'owc_resource_id -> owcResource.id.toString
          ).executeUpdate()

          val childDelete = OwcResourceDAO.deleteOwcResource(owcResource)
          if (childDelete) rowCount else 0
      }.sum

      val owcContextDelete = SQL(s"delete from $tableOwcContexts where id = {id}").on(
        'id -> owcContext.id.toString
      ).executeUpdate()

      val allDeletes = resourceRelationsDeleteCount + rightsDepsDelete + owcContextDelete
      val testValue = owcContext.resource.size + 1 + 1

      if (allDeletes == testValue) {
        true
      } else {
        logger.error("OwcContext/delete: one of " +
          s"resourceRelationsDeleteCount: $resourceRelationsDeleteCount (should be ${owcContext.resource.size}) + " +
          s"rightsDepsDelete: $rightsDepsDelete (1) + owcContextDelete: $owcContextDelete (1) failed")
        logger.error("OwcContext couldn't be deleted")
        // we need to think where to place rollback most appropriately
        connection.rollback()
        false
      }
    } else {
      logger.error("Precondition failed, won't delete OwcContext")
      // we need to think where to place rollback most appropriately
      connection.rollback()
      false
    }
  }
}
