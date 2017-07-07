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
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}

import anorm.SqlParser.{get, str}
import anorm.{RowParser, SQL, ~}
import info.smart.models.owc100._
import models.users.User
import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.io.ShapeIO
import org.locationtech.spatial4j.shape.Rectangle
import play.api.db.Database
import utils.ClassnameLogger

import scala.util.Try

//{ OwcContext, OwcResource, UploadedFileProperties}

/**
  * OwcContextDAO - store and retrieve OWS Context Documents
  * An OWC document is an extended FeatureCollection, where the features (aka resources) hold a variety of metadata
  * about the things they provide the context for (i.e. other data sets, services, metadata records)
  * OWC documents do not duplicate a CSW MD_Metadata record, but a collection of referenced resources;
  *
  * @param db
  * @param owcOfferingDAO
  * @param owcPropertiesDAO
  */
@Singleton
class OwcContextDAO @Inject()(db: Database,
                              owcOfferingDAO: OwcOfferingDAO,
                              owcPropertiesDAO: OwcPropertiesDAO
                             ) extends ClassnameLogger {

  private lazy val ctx = SpatialContext.GEO
  private lazy val wktReader = ctx.getFormats().getReader(ShapeIO.WKT)

  /**
    * Parse an OwcResource from a ResultSet
    */
  private val owcResourceParser: RowParser[OwcResource] = {
    str("id") ~
      str("title") ~
      get[Option[String]]("subtitle") ~
      get[OffsetDateTime]("update_date") ~
      get[Option[String]]("authors") ~
      get[Option[String]]("publisher") ~
      get[Option[String]]("rights") ~
      get[Option[String]]("geospatial_extent") ~
      get[Option[String]]("temporal_extent") ~
      get[Option[String]]("content_description") ~
      get[Option[String]]("preview") ~
      get[Option[String]]("content_by_ref") ~
      get[Option[String]]("offering") ~
      get[Option[Boolean]]("active") ~
      get[Option[String]]("resource_metadata") ~
      get[Option[String]]("keyword") ~
      get[Option[Double]]("min_scale_denominator") ~
      get[Option[Double]]("max_scale_denominator") ~
      get[Option[String]]("folder") map {
      case
        id ~
          title ~
          subtitle ~
          updateDate ~
          authorsUuids ~
          publisher ~
          rights ~
          bboxText ~
          temporalText ~
          contentDescUuids ~
          previewUuids ~
          contentByRefUuids ~
          offeringUuids ~
          active ~
          resMetaUuids ~
          keywordUuids ~
          minScale ~
          maxScale ~
          folder
      => {
        OwcResource(
          id = new URL(id),
          title = title,
          subtitle = subtitle,
          updateDate = updateDate,
          author = authorsUuids.map(u => owcPropertiesDAO.findByPropertiesUUID[OwcAuthor](Some(u))(OwcAuthorEvidence).toList).getOrElse(List()),
          publisher = publisher,
          rights = rights,
          geospatialExtent = createOptionalBbox(bboxText),
          temporalExtent = parseOffsetDateString(temporalText),
          contentDescription = contentDescUuids.map(u =>
            owcPropertiesDAO.findByPropertiesUUID[OwcLink](Some(u))(OwcLinkEvidence).toList).getOrElse(List()), // links.alternates[] and rel=alternate
          preview = previewUuids.map(u =>
            owcPropertiesDAO.findByPropertiesUUID[OwcLink](Some(u))(OwcLinkEvidence).toList).getOrElse(List()), // aka links.previews[] and rel=icon (atom)
          contentByRef = contentByRefUuids.map(u =>
            owcPropertiesDAO.findByPropertiesUUID[OwcLink](Some(u))(OwcLinkEvidence).toList).getOrElse(List()), // aka links.data[] and rel=enclosure (atom)
          offering = offeringUuids.map(u =>
            owcOfferingDAO.findByPropertiesUUID[OwcOffering](Some(u))(OwcOfferingEvidence).toList).getOrElse(List()),
          active = active,
          resourceMetadata = resMetaUuids.map(u =>
            owcPropertiesDAO.findByPropertiesUUID[OwcLink](Some(u))(OwcLinkEvidence).toList).getOrElse(List()), // aka links.via[] & rel=via
          keyword = keywordUuids.map(u =>
            owcPropertiesDAO.findByPropertiesUUID[OwcCategory](Some(u))(OwcCategoryEvidence).toList).getOrElse(List()),
          minScaleDenominator = minScale,
          maxScaleDenominator = maxScale,
          folder = folder)
      }
    }
  }

  //  /**
  //    * instantiate an OwcFeatureType either OwcResource, or OwcContext
  //    *
  //    * @param id
  //    * @param featureType
  //    * @param bboxAsWkt
  //    * @return
  //    */
  //  def instantiateOwcFeatureType(id: String, featureType: String, bboxAsWkt: Option[String]): OwcFeatureType = {
  //
  //    val emptyProfile = OwcLink(UUID.randomUUID(), "profile", None, "http://www.opengis.net/spec/owc-atom/1.0/req/core", None)
  //    val emptySelf = OwcLink(UUID.randomUUID(), "self", Some("application/json"), "http://example.com/empty", None)
  //    val now = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())
  //    logger.debug(s"instatiate for $featureType - $id")
  //
  //    val emptyDefaultProps = OwcProperties(
  //      UUID.randomUUID(),
  //      "en",
  //      "empty properties",
  //      Some("no property information was found"),
  //      Some(now),
  //      Some("automated system response"),
  //      None,
  //      List(),
  //      List(),
  //      None,
  //      None,
  //      List(),
  //      List(emptyProfile, emptySelf)
  //    )
  //
  //    val rectOpt = createOptionalBbox(bboxAsWkt)
  //    val props = owcPropertiesDAO.findOwcPropertiesForOwcFeatureType(id).getOrElse(emptyDefaultProps)
  //
  //    featureType match {
  //      case "OwcResource" => {
  //        logger.debug(s"found OwcResource ${id}")
  //        val offerings = owcOfferingDAO.findOwcOfferingsForOwcResource(id).toList
  //        OwcResource(id, rectOpt, props, offerings)
  //      }
  //      case "OwcContext" => {
  //        logger.debug(s"found OwcContext ${id}")
  //        val resources = findOwcResourcesForOwcContext(id).toList
  //        OwcContext(id, rectOpt, props, resources)
  //      }
  //      case _ => throw new InvalidClassException(featureType, s"Unknown feature type $featureType")
  //    }
  //  }


  //  /**
  //    * Returns all OwcProperties from user's default collection (OwcContext) that contain uploaded files.
  //    *
  //    * @param email
  //    * @return Seq[OwcProperties] that may be empty
  //    */
  //  def findOwcPropertiesForOwcAuthorOwnFiles(email: String): Seq[UploadedFileProperties] = {
  //    val defaultContext = this.findUserDefaultOwcContext(email);
  //    if (defaultContext.isEmpty) {
  //      logger.warn(s"Could not find default collection (OwcContext) for user $email");
  //      Nil
  //    }
  //    else {
  //      db.withConnection { implicit connection =>
  //        SQL(
  //          s"""
  //             |SELECT DISTINCT
  //             |  prop.*,
  //             |  oper.*
  //             |FROM $tableOwcContexts AS ftyp
  //             |  -- get all OwcResources under default collection (OwcContext)
  //             |  INNER JOIN $tableOwcContextsAsContextHasOwcResources AS ftyp2entr
  //             |    ON "ftyp"."id" = "ftyp2entr"."owc_feature_types_as_resource_id"
  //             |  -- get all Properties for that OwcResource
  //             |  INNER JOIN $tableOwcContextsHasOwcProperties AS ftyp2prop
  //             |    ON "ftyp"."id" = "ftyp2prop"."owc_feature_types_id"
  //             |  INNER JOIN $tableOwcProperties AS prop
  //             |    ON "ftyp2prop"."owc_properties_uuid" = "prop"."uuid"
  //             |  -- get all offerings in that Resource
  //             |  INNER JOIN $tableOwcContextsAsResourceHasOwcOfferings AS ftyp2offe
  //             |    ON "ftyp"."id" = "ftyp2offe"."owc_feature_types_as_resource_id"
  //             |  INNER JOIN $tableOwcOfferings AS offe
  //             |    ON "ftyp2offe"."owc_offerings_uuid" = "offe"."uuid"
  //             |  -- find all operations to that offering
  //             |  INNER JOIN $tableOwcOfferingsHasOwcOperations AS offe2oper
  //             |    ON "offe"."uuid" = "offe2oper"."owc_offerings_uuid"
  //             |  INNER JOIN $tableOwcOperations AS oper
  //             |    ON "offe2oper"."owc_operations_uuid" = "oper"."uuid"
  //             |WHERE 1 = 1
  //             |      -- default collection (OwcContext) ID
  //             |      AND ftyp2entr."owc_feature_types_as_context_id" = {defaultContextId}
  //             |      AND offe."offering_type" = 'HttpLinkOffering'
  //             |      AND oper."code" = 'GetFile';""".stripMargin)
  //          .on('defaultContextId -> defaultContext.get.id)
  //          .as(owcPropertiesDAO.uploadedFilePropertiesParser *)
  //      }
  //    }
  //  }


  /** *********
    * OwcResource
    * **********/

  /**
    * get all OwcResources
    *
    * @return
    */
  def getAllOwcResources: Seq[OwcResource] = {
    db.withConnection { implicit connection =>
      SQL(s"select * from $tableOwcResources").as(owcResourceParser *)
    }
  }

  /**
    * find an OwcResource by its id
    *
    * @param owcResourceId
    * @return
    */
  def findOwcResourcesById(owcResourceId: String): Option[OwcResource] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""select * from $tableOwcResources
           |where id = {id}""".stripMargin).on(
        'id -> owcResourceId
      ).as(owcResourceParser.singleOpt)
    }
  }

  /**
    * find all OwcResources for an OwcContext by contextid
    *
    * @param owcContextId
    * @return
    */
  def findOwcResourcesForOwcContext(owcContextId: String): Seq[OwcResource] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""SELECT
           |res.*
           |FROM $tableOwcResources res JOIN $tableOwcContextHasOwcResources ocor ON res.id=ocor.owc_resource_id
           |WHERE ocor.owc_context_id={owc_context_id}""".stripMargin).on(
        'owc_context_id -> owcContextId
      )
        .as(owcResourceParser *)
    }
  }


  /**
    * create an owc resource with dependent offerings and properties
    *
    * @param owcResource
    * @return
    */
  def createOwcResource(owcResource: OwcResource): Option[OwcResource] = {

    // FIXME: not finished

    val preCreateCheckOwcAuthors = false
    val preCreateCheckOwcLinks = false
    val preCreateCheckOwcOfferings = false
    val preCreateCheckOwcCategories = false

    if (preCreateCheckOwcAuthors && preCreateCheckOwcLinks &&
      preCreateCheckOwcOfferings && preCreateCheckOwcCategories) {

      db.withTransaction {
        implicit connection => {

          val rowCount = SQL(
            s"""insert into $tableOwcResources values (
               |{id}, {title},
               |{subtitle}, {update_date},
               |{authors}, {publisher},
               |{rights}, {geospatial_extent},
               |{temporal_extent}, {content_description},
               |{preview}, {content_by_ref},
               |{offering}, {active},
               |{resource_metadata}, {keyword},
               |{min_scale_denominator}, {max_scale_denominator},
               |{folder} )""".stripMargin).on(
            'id -> owcResource.id.toString,
            'title -> owcResource.title,
            'subtitle -> owcResource.subtitle,
            'update_date -> owcResource.updateDate.toZonedDateTime,
            'authors -> owcResource.author.map(_.uuid.toString).mkString(":"),
            'publisher -> owcResource.publisher,
            'rights -> owcResource.rights,
            'geospatial_extent -> owcResource.geospatialExtent.map(r => rectToWkt(r)),
            'temporal_extent -> owcResource.temporalExtent.map(dates => writeOffsetDatesAsDateString(dates)).getOrElse(None),
            'content_description -> owcResource.contentDescription.map(_.uuid.toString).mkString(":"), // links.alternates[] and rel=alternate
            'preview -> owcResource.preview.map(_.uuid.toString).mkString(":"), // aka links.previews[] and rel=icon (atom)
            'content_by_ref -> owcResource.contentByRef.map(_.uuid.toString).mkString(":"), // aka links.data[] and rel=enclosure (atom)
            'offering -> owcResource.offering.map(_.uuid.toString).mkString(":"),
            'active -> owcResource.active,
            'resource_metadata -> owcResource.resourceMetadata.map(_.uuid.toString).mkString(":"), // aka links.via[] & rel=via
            'keyword -> owcResource.keyword.map(_.uuid.toString).mkString(":"),
            'min_scale_denominator -> owcResource.minScaleDenominator,
            'max_scale_denominator -> owcResource.maxScaleDenominator,
            'folder -> owcResource.folder
          ).executeUpdate()

          rowCount match {
            case 1 => Some(owcResource)
            case _ =>
              logger.error(s"OwcResource couldn't be created")
              None
          }
        }
      }
    } else {
      logger.error(s"Precondition failed, won't create OwcResource")
      None
    }
  }

  /**
    * Not yet implemented, update OwcResource and hierarchical dependents
    *
    * @param owcResource
    * @return
    */
  def updateOwcResource(owcResource: OwcResource): Option[OwcResource] = {

    // FIXME: not finished

    val preUpdateCheckOwcAuthors = false
    val preUpdateCheckOwcLinks = false
    val preUpdateCheckOwcOfferings = false
    val preUpdateCheckOwcCategories = false

    if (preUpdateCheckOwcAuthors && preUpdateCheckOwcLinks &&
      preUpdateCheckOwcOfferings && preUpdateCheckOwcCategories) {

      db.withTransaction {
        implicit connection => {

          val rowCount = 0

          rowCount match {
            case 1 => Some(owcResource)
            case _ => logger.error(s"OwcResource couldn't be updated")
              None
          }
        }
      }
    } else {
      logger.error(s"Precondition failed, won't update OwcResource")
      None
    }
  }

  /**
    * delete an OwcResource with dependent offerings and properties links etc
    *
    * @param owcResource
    * @return
    */
  def deleteOwcResource(owcResource: OwcResource): Boolean = {

    // FIXME: not finished

    val preDeleteCheckOwcAuthors = false
    val preDeleteCheckOwcLinks = false
    val preDeleteCheckOwcOfferings = false
    val preDeleteCheckOwcCategories = false

    if (preDeleteCheckOwcAuthors && preDeleteCheckOwcLinks &&
      preDeleteCheckOwcOfferings && preDeleteCheckOwcCategories) {

      db.withTransaction {
        implicit connection => {

          val rowCount = SQL(s"delete from $tableOwcResources where id = {id}").on(
            'id -> owcResource.id.toString
          ).executeUpdate()

          rowCount match {
            case 1 => true
            case _ =>
              logger.error(s"OwcResource couldn't be deleted")
              false
          }

        }
      }
    } else {
      logger.error(s"Precondition failed, won't delete OwcResource")
      false
    }
  }


  /** *********
    * OwcContext
    * **********/

  /**
    * Parse an OwcContext from a ResultSet
    */
  private val owcContextParser: RowParser[OwcContext] = {
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
          areaOfInterest = createOptionalBbox(bboxText),
          specReference = specReferenceUuids.map(u =>
            owcPropertiesDAO.findByPropertiesUUID[OwcLink](Some(u))(OwcLinkEvidence).toList).getOrElse(List()), // aka links.profiles[] & rel=profile
          contextMetadata = contextMetaUuids.map(u =>
            owcPropertiesDAO.findByPropertiesUUID[OwcLink](Some(u))(OwcLinkEvidence).toList).getOrElse(List()), // aka links.via[] & rel=via
          language = language,
          title = title,
          subtitle = subtitle,
          updateDate = updateDate,
          author = authorsUuids.map(u => owcPropertiesDAO.findByPropertiesUUID[OwcAuthor](Some(u))(OwcAuthorEvidence).toList).getOrElse(List()),
          publisher = publisher,
          creatorApplication = None,
          creatorDisplay = None,
          rights = rights,
          timeIntervalOfInterest = parseOffsetDateString(temporalText),
          keyword = keywordUuids.map(u =>
            owcPropertiesDAO.findByPropertiesUUID[OwcCategory](Some(u))(OwcCategoryEvidence).toList).getOrElse(List()),
          resource = findOwcResourcesForOwcContext(id.toString).toList)
      }
    }
  }

  /**
    * get all OwcContexts
    *
    * @return
    */
  def getAllOwcContexts: Seq[OwcContext] = {
    db.withConnection { implicit connection =>
      SQL(s"select * from $tableOwcContexts").as(owcContextParser *)
    }
  }

  /**
    * find an OwcContext by its id
    *
    * @param owcContextId
    * @return
    */
  def findOwcContextsById(owcContextId: String): Option[OwcContext] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""select * from $tableOwcContexts
           |where id = {id}""".stripMargin).on(
        'id -> owcContextId
      ).as(owcContextParser.singleOpt)
    }
  }

  /**
    * find an OwcContext by its id and user
    *
    * @param owcContextId
    * @param user
    * @return
    */
  def findOwcContextByIdAndUser(owcContextId: String, user: User): Option[OwcContext] = {
    db.withConnection { implicit connection =>

      SQL(
        s"""select con.*
           |FROM $tableOwcContexts con JOIN $tableUserHasOwcContextRights u ON con.id=u.owc_context_id
           |where con.id = {id} AND AND u.users_accountsubject = {account_subject}""".stripMargin).on(
        'id -> owcContextId,
        'account_subject -> user.accountSubject
      ).as(owcContextParser.singleOpt)
    }
  }

  /**
    * find an OwcContext by user
    *
    * @param user
    * @return
    */
  def findOwcContextsByUser(user: User): Seq[OwcContext] = {
    db.withConnection { implicit connection =>

      SQL(
        s"""select con.*
           |FROM $tableOwcContexts con JOIN $tableUserHasOwcContextRights u ON con.id=u.owc_context_id
           |where u.users_accountsubject = {account_subject}""".stripMargin).on(
        'account_subject -> user.accountSubject
      ).as(owcContextParser *)
    }
  }

  /**
    * find all OwcContexts by visibility
    *
    * @param visibility 0: user-owned/private, 1: organisation, 2: public
    * @return
    */
  def findOwcContextsByVisibility(visibility: Int): Seq[OwcContext] = {
    db.withConnection { implicit connection =>

      SQL(
        s"""select con.*
           |FROM $tableOwcContexts con JOIN $tableUserHasOwcContextRights u ON con.id=u.owc_context_id
           |where u.visibility >= {visibility}""".stripMargin).on(
        'visibility -> visibility
      ).as(owcContextParser *)
    }
  }

  /**
    * get all publicly visible OwcContexts
    *
    * @return
    */
  def getAllPublicOwcContexts: Seq[OwcContext] = {
    findOwcContextsByVisibility(2)
  }

  /**
    * find an OwcContext by its id
    *
    * @param owcContextId
    * @return
    */
  def findPublicOwcContextsById(owcContextId: String): Option[OwcContext] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""select con.*
           |FROM $tableOwcContexts con JOIN $tableUserHasOwcContextRights u ON con.id=u.owc_context_id
           |where con.id = {id} AND u.visibility >= {visibility}""".stripMargin).on(
        'id -> owcContextId,
        'visibility -> 2
      ).as(owcContextParser.singleOpt)
    }
  }

  /**
    * find OwcContexts by user and type
    *
    * @param user
    * @param rightsRelationType DEFAULT: user personal default, CUSTOM: general purpose
    * @return
    */
  def findOwcContextsByUserAndType(user: User, rightsRelationType: String): Seq[OwcContext] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""select con.*
           | FROM $tableOwcContexts con JOIN $tableUserHasOwcContextRights u ON con.id=u.owc_context_id
           | WHERE u.users_accountsubject = {account_subject}
           | AND u.rights_relation_type = {rights_relation_type} """.stripMargin).on(
        'account_subject -> user.accountSubject,
        'rights_relation_type -> rightsRelationType
      ).as(owcContextParser *)
    }
  }

  def findUserDefaultOwcContext(user: User): Option[OwcContext] = {
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
  def createOwcContext(owcContext: OwcContext, user: User, visibility: Int, rightsRelationType: String): Option[OwcContext] = {

    val accountSubject = user.accountSubject

    // FIXME: not finished

    val preCreateRightsCheck = false

    val preCreateCheckOwcAuthors = false
    val preCreateCheckOwcLinks = false
    val preCreateCheckOwcResources = false
    val preCreateCheckOwcCategories = false

    if (preCreateRightsCheck && preCreateCheckOwcAuthors && preCreateCheckOwcLinks &&
      preCreateCheckOwcResources && preCreateCheckOwcCategories) {

      db.withTransaction {
        implicit connection => {

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
        }
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
  def createCustomOwcContext(owcContext: OwcContext, user: User): Option[OwcContext] = {
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
  def createUsersDefaultOwcContext(owcContext: OwcContext, user: User): Option[OwcContext] = {
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
                                          rightsRelationType: String): Option[OwcContext] = {

    val accountSubject = user.accountSubject
    // FIXME: not finished

    val preUpdateRightsCheck = false

    if (preUpdateRightsCheck) {

      db.withConnection {
        implicit connection => {

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
        }
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
  def updateOwcContext(owcContext: OwcContext, user: User): Option[OwcContext] = {

    val accountSubject = user.accountSubject
    // FIXME: not finished

    val preUpdateRightsCheck = false

    val preUpdateCheckOwcAuthors = false
    val preUpdateCheckOwcLinks = false
    val preUpdateCheckOwcResources = false
    val preUpdateCheckOwcCategories = false

    if (preUpdateRightsCheck && preUpdateCheckOwcAuthors && preUpdateCheckOwcLinks &&
      preUpdateCheckOwcResources && preUpdateCheckOwcCategories) {

      db.withTransaction {
        implicit connection => {

          val rowCount = 0

          rowCount match {
            case 1 => Some(owcContext)
            case _ => logger.error(s"OwcContext couldn't be updated")
              None
          }
        }
      }
    } else {
      logger.error(s"Precondition failed, won't update OwcContext")
      None
    }

  }

  //  /**
  //    *
  //    * @param owcContext
  //    * @param accountSubject
  //    * @return
  //    */
  //  def addOwcResourceToOwcContext(owcContext: OwcContext, owcResource: OwcResource, accountSubject: String): Option[OwcContext] = {
  //
  //    val rowCount = db.withTransaction {
  //      implicit connection => {
  //
  //        // if uuid of provided resource is not found in DB create new resource (should be most likely usually?)
  //        if (findOwcResourcesById(owcResource.id).isEmpty) {
  //          createOwcResource(owcResource)
  //
  //        } else {
  //          logger.warn(s"owcResource scheduled for addition to owcdoc ${owcContext.id} with resource id ${owcResource.id} already exists")
  //          None
  //        }
  //
  //        // update the features/resources list of the collection (OwcContext)
  //        SQL(
  //          s"""insert into $tableOwcContextsHasOwcResources  values (
  //             |{owc_feature_types_as_context_id}, {owc_feature_types_as_resource_id}
  //             |)
  //               """.stripMargin).on(
  //          'owc_feature_types_as_context_id -> owcContext.id,
  //          'owc_feature_types_as_resource_id -> owcResource.id
  //        ).executeUpdate()
  //      }
  //    }
  //
  //    rowCount match {
  //      case 1 => Some(owcContext)
  //      case _ => None
  //    }
  //  }

  //  /**
  //    *
  //    * @param owcContext
  //    * @param accountSubject
  //    * @return
  //    */
  //  def replaceResourceInOwcContext(owcContext: OwcContext, owcResource: OwcResource, accountSubject: String): Option[OwcContext] = {
  //
  //    val updateCount = db.withTransaction {
  //      implicit connection => {
  //
  //        // update / replace the resource, due to SQL foreign key reference, we can only update if noone else has a ref on it
  //        val resourceToReplace = findOwcResourcesById(owcResource.id)
  //
  //        // if uuid of provided resource is not found in DB create new resource (upsert)
  //        if (resourceToReplace.isDefined) {
  //
  //          // and now delete our own ref from specified relation to owc doc
  //          SQL(
  //            s"""delete from $tableOwcContextsHasOwcResources where owc_feature_types_as_context_id = {doc_id}
  //               |AND owc_feature_types_as_resource_id = {resource_id}
  //             """.stripMargin).on(
  //            'doc_id -> owcContext.id,
  //            'resource_id -> owcResource.id
  //          ).executeUpdate()
  //
  //        }
  //
  //        val isItOrphan = SQL(
  //          s"""select owc_feature_types_as_resource_id from
  //             |$tableOwcContextsHasOwcResources where owc_feature_types_as_resource_id = {id}""".stripMargin).on(
  //          'id -> owcResource.id
  //        ).as(SqlParser.str("owc_feature_types_as_resource_id") *).isEmpty
  //
  //        val replacementStep = if (isItOrphan) {
  //          if (resourceToReplace.isDefined) {
  //            if (deleteOwcResource(owcResource)) {
  //              createOwcResource(owcResource).isDefined
  //            } else {
  //              logger.error(s"owcResource with resource id ${owcResource.id} scheduled for replacement in owcdoc ${owcContext.id} " +
  //                "could not be deleted/replaced properly")
  //              false
  //            }
  //          } else {
  //            logger.warn(s"owcResource with resource id ${owcResource.id} scheduled for replacement in owcdoc ${owcContext.id} " +
  //              "did not exist before (doing upsert")
  //            createOwcResource(owcResource).isDefined
  //          }
  //        } else {
  //          logger.error(s"owcResource with resource id ${owcResource.id} scheduled for replacement in owcdoc ${owcContext.id} " +
  //            "is referenced in other collection (OwcContext) and cannot be touched.")
  //          false
  //        }
  //
  //        // add relation to owc doc
  //        val reAddRelations = SQL(
  //          s"""insert into $tableOwcContextsHasOwcResources  values (
  //             |{owc_feature_types_as_context_id}, {owc_feature_types_as_resource_id}
  //             |)
  //               """.stripMargin).on(
  //          'owc_feature_types_as_context_id -> owcContext.id,
  //          'owc_feature_types_as_resource_id -> owcResource.id
  //        ).executeUpdate()
  //
  //        val yesOrNo = if (replacementStep) 1 else 0
  //        reAddRelations + yesOrNo
  //      }
  //    }
  //
  //    updateCount match {
  //      case 2 => Some(owcContext) // the chained new context
  //      case 1 => findOwcContextByIdAndUser(owcContext.id, email) // the old context
  //      case _ => None
  //    }
  //  }

  //  /**
  //    *
  //    * @param owcContext
  //    * @param accountSubject
  //    * @return
  //    */
  //  def deleteOwcResourceFromOwcContext(owcContext: OwcContext, owcResourceId: String, accountSubject: String): Option[OwcContext] = {
  //
  //    val rowCount = db.withTransaction {
  //      implicit connection => {
  //
  //        val resourceToRemove = findOwcResourcesById(owcResourceId)
  //        // if uuid of provided resource is not found in DB can't delete then
  //        if (resourceToRemove.isDefined) {
  //
  //          // and delete relation to owc doc
  //          val deleteResourceRelationForDoc = SQL(
  //            s"""delete from $tableOwcContextsHasOwcResources where owc_feature_types_as_context_id = {doc_id}
  //               |AND owc_feature_types_as_resource_id = {resource_id}
  //             """.stripMargin).on(
  //            'doc_id -> owcContext.id,
  //            'resource_id -> owcResourceId
  //          ).executeUpdate()
  //
  //          val shouldRemoveOrphan = SQL(
  //            s"""select owc_feature_types_as_resource_id from
  //               |$tableOwcContextsHasOwcResources where owc_feature_types_as_resource_id = {id}""".stripMargin).on(
  //            'id -> owcResourceId
  //          ).as(SqlParser.str("owc_feature_types_as_resource_id") *).isEmpty
  //
  //          if (shouldRemoveOrphan) {
  //            deleteOwcResource(resourceToRemove.get)
  //          }
  //
  //          deleteResourceRelationForDoc
  //        }
  //      }
  //    }
  //
  //    rowCount match {
  //      case 1 => Some(owcContext)
  //      case _ => None
  //    }
  //  }

  /**
    * delete a full owc context hierarchically
    *
    * @param owcContext
    * @return
    */
  def deleteOwcContext(owcContext: OwcContext, user: User): Boolean = {

    val accountSubject = user.accountSubject
    // FIXME: not finished

    val preDeleteRightsCheck = false
    val preDeleteCheckOwcAuthors = false
    val preDeleteCheckOwcLinks = false
    val preDeleteCheckOwcResources = false
    val preDeleteCheckOwcCategories = false

    if (preDeleteRightsCheck && preDeleteCheckOwcAuthors && preDeleteCheckOwcLinks &&
      preDeleteCheckOwcResources && preDeleteCheckOwcCategories) {

      db.withTransaction {
        implicit connection => {

          val rowCount = SQL(s"delete from $tableOwcContexts where id = {id}").on(
            'id -> owcContext.id.toString
          ).executeUpdate()

          rowCount match {
            case 1 => true
            case _ =>
              logger.error(s"OwcContext couldn't be deleted")
              false
          }

        }
      }
    } else {
      logger.error(s"Precondition failed, won't delete OwcContext")
      false
    }
  }

  /**
    * custom utils
    */

  /**
    * quick and dirty bbox from wkt stored in DB
    *
    * @param bboxAsWkt
    * @return
    */
  private def createOptionalBbox(bboxAsWkt: Option[String]): Option[Rectangle] = {
    if (bboxAsWkt.isDefined) {
      Try {
        wktReader.read(bboxAsWkt.get).asInstanceOf[Rectangle]
      }.toOption
    } else {
      None
    }
  }

  /**
    * quick and dirty string from a bbox to store wkt in DB
    *
    * @param rect
    * @return
    */
  private def rectToWkt(rect: Rectangle): String = {
    val shpWriter = ctx.getFormats().getWriter(ShapeIO.WKT)
    shpWriter.toString(rect)
  }

  /**
    *
    * @param dateStringOption
    * @return
    */
  def parseOffsetDateString(dateStringOption: Option[String]): Option[List[OffsetDateTime]] = {
    if (dateStringOption.isDefined) {
      val isoTemporalString = dateStringOption.get
      if (isoTemporalString.contains("/")) {
        parseDateStringAsOffsetInterval(isoTemporalString).toOption
      } else {
        parseDateStringAsOffsetDateTime(isoTemporalString).toOption
      }
    } else {
      None
    }
  }

  /**
    *
    * @param isoTemporalString
    * @return
    */
  private def parseDateStringAsOffsetDateTime(isoTemporalString: String): Try[List[OffsetDateTime]] = {
    Try {
      val date = OffsetDateTime.parse(isoTemporalString)
      List(date)
    }
  }

  /**
    *
    * @param isoTemporalString
    * @return
    */
  private def parseDateStringAsOffsetInterval(isoTemporalString: String): Try[List[OffsetDateTime]] = {
    Try {
      val dateStrings = isoTemporalString.replace("\"", "").trim.split("/").toList
      val date1 = OffsetDateTime.parse(dateStrings.head)
      val date2 = OffsetDateTime.parse(dateStrings.last)
      List(date1, date2)
    }
  }

  /**
    * String serialiser to store either single offset date or the interval in DB
    *
    * @param dateRange
    * @return
    */
  def writeOffsetDatesAsDateString(dateRange: List[OffsetDateTime]): Option[String] = {
    lazy val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    val dates = dateRange.sortWith((a, b) => a.isBefore(b))
    if (dates.head == dates.last) {
      Try(dates.head.format(formatter)).toOption
    } else {
      Try {
        val d1 = dates.head.format(formatter)
        val d2 = dates.last.format(formatter)
        d1 + "/" + d2
      }.toOption
    }
  }

  // FIXME: not finished OwcCreatorApplication

  // FIXME: not finished OwcCreatorDisplay
}
