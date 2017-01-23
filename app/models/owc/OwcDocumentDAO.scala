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
import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID
import javax.inject.{Inject, Singleton}

import anorm.SqlParser.{get, str}
import anorm.{RowParser, SQL, SqlParser, ~}
import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.io.ShapeIO
import org.locationtech.spatial4j.shape.Rectangle
import play.api.db.Database
import utils.ClassnameLogger

/**
  * OwcDocumentDAO - store and retrieve OWS Context Documents
  * An OWC document is an extended FeatureCollection, where the features (aka entries) hold a variety of metadata
  * about the things they provide the context for (i.e. other data sets, services, metadata records)
  * OWC documents do not duplicate a CSW MD_Metadata record, but a collection of referenced resources;
  *
  * @param db
  */
@Singleton
class OwcDocumentDAO @Inject()(db: Database,
                               owcOfferingDAO: OwcOfferingDAO,
                               owcPropertiesDAO: OwcPropertiesDAO
                              ) extends ClassnameLogger {

  private lazy val ctx = SpatialContext.GEO
  private lazy val wktReader = ctx.getFormats().getReader(ShapeIO.WKT)

  /**
    * quick and dirty bbox from wkt stored in DB
    *
    * @param bboxAsWkt
    * @return
    */
  def createOptionalBbox(bboxAsWkt: Option[String]): Option[Rectangle] = {
    bboxAsWkt.map {
      bboxString => {
        wktReader.read(bboxAsWkt.getOrElse(ctx.getWorldBounds)).asInstanceOf[Rectangle]
      }
    }
  }

  /**
    * quick and dirty string from a bbox to store wkt in DB
    *
    * @param rect
    * @return
    */
  def rectToWkt(rect: Rectangle): String = {
    val shpWriter = ctx.getFormats().getWriter(ShapeIO.WKT)
    shpWriter.toString(rect)
  }


  /**
    * instantiate an OwcFeatureType either OwcEntry, or OwcDocument
    *
    * @param id
    * @param featureType
    * @param bboxAsWkt
    * @return
    */
  def instantiateOwcFeatureType(id: String, featureType: String, bboxAsWkt: Option[String]): OwcFeatureType = {

    val emptyProfile = OwcLink(UUID.randomUUID(), "profile", None, "http://www.opengis.net/spec/owc-atom/1.0/req/core", None)
    val emptySelf = OwcLink(UUID.randomUUID(), "self", Some("application/json"), "http://example.com/empty", None)
    val now = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())
    logger.debug(s"instatiate for $featureType - $id")

    val emptyDefaultProps = OwcProperties(
      UUID.randomUUID(),
      "en",
      "empty properties",
      Some("no property information was found"),
      Some(now),
      Some("automated system response"),
      None,
      List(),
      List(),
      None,
      None,
      List(),
      List(emptyProfile, emptySelf)
    )

    val rectOpt = createOptionalBbox(bboxAsWkt)
    val props = owcPropertiesDAO.findOwcPropertiesForOwcFeatureType(id).getOrElse(emptyDefaultProps)

    featureType match {
      case "OwcEntry" => {
        logger.debug(s"found OwcEntry ${id}")
        val offerings = owcOfferingDAO.findOwcOfferingsForOwcEntry(id).toList
        OwcEntry(id, rectOpt, props, offerings)
      }
      case "OwcDocument" => {
        logger.debug(s"found OwcDocument ${id}")
        val entries = findOwcEntriesForOwcDocument(id).toList
        OwcDocument(id, rectOpt, props, entries)
      }
      case _ => throw new InvalidClassException(featureType, s"Unknown feature type $featureType")
    }
  }

  /** *********
    * OwcEntry
    * **********/

  /**
    * Parse an OwcEntry from a ResultSet
    */
  val owcEntryParser: RowParser[OwcEntry] = {
    str("id") ~
      str("feature_type") ~
      get[Option[String]]("bbox") map {
      case id ~ featureType ~ bboxAsWkt => {
        logger.debug(s"owcEntryParser $id, $featureType, $bboxAsWkt")
        instantiateOwcFeatureType(id, featureType, bboxAsWkt).asInstanceOf[OwcEntry]
      }
    }
  }

  /**
    * Parse an OwcDocument from a ResultSet
    */
  val owcDocumentParser: RowParser[OwcDocument] = {
    str("id") ~
      str("feature_type") ~
      get[Option[String]]("bbox") map {
      case id ~ featureType ~ bboxAsWkt =>
        instantiateOwcFeatureType(id, featureType, bboxAsWkt).asInstanceOf[OwcDocument]
    }
  }

  /**
    * get all OwcEntries
    *
    * @return
    */
  def getAllOwcEntries: Seq[OwcEntry] = {
    db.withConnection { implicit connection =>
      val sql = SQL(s"""select * from $tableOwcFeatureTypes where feature_type = {feature_type}""").on(
        'feature_type -> "OwcEntry")
      logger.debug(s"getAllOwcEntries ${sql.toString}")
      sql.as(owcEntryParser *)
    }
  }

  /**
    * find an OwcEntry by its id
    *
    * @param id
    * @return
    */
  def findOwcEntriesById(id: String): Option[OwcEntry] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""select * from $tableOwcFeatureTypes
           |where id = {id} AND feature_type = {feature_type}""".stripMargin).on(
        'id -> id,
        'feature_type -> "OwcEntry"
      ).as(owcEntryParser.singleOpt)
    }
  }

  /**
    * find an OwcEntries for an OwcDocument (both being FeatureTypes though)
    *
    * @param documentId
    * @return
    */
  def findOwcEntriesForOwcDocument(documentId: String): Seq[OwcEntry] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""SELECT
           |ft.id as id, ft.feature_type as feature_type, ft.bbox as bbox
           |FROM $tableOwcFeatureTypes ft JOIN $tableOwcDocumentsHasOwcEntries doen ON ft.id=doen.owc_feature_types_as_entry_id
           |WHERE doen.owc_feature_types_as_document_id={owc_feature_types_as_document_id}""".stripMargin).on(
        'owc_feature_types_as_document_id -> documentId
      )
        .as(owcEntryParser *)
    }
  }

  /**
    * create an owc entry with dependent offerings and properties
    *
    * @param owcEntry
    * @return
    */
  def createOwcEntry(owcEntry: OwcEntry): Option[OwcEntry] = {

    db.withTransaction {
      implicit connection => {

        val sql = SQL(
          s"""
          insert into $tableOwcFeatureTypes values (
            {id}, {feature_type}, {bbox}
          )
        """).on(
          'id -> owcEntry.id,
          'feature_type -> "OwcEntry",
          'bbox -> owcEntry.bbox.map(rect => rectToWkt(rect))
        )

        val rowCount = sql.executeUpdate()

        logger.debug(s"inserted ${owcEntry.getClass.getSimpleName} $rowCount ${sql.toString}")

        owcEntry.offerings.foreach {
          owcOffering => {
            if (owcOfferingDAO.findOwcOfferingByUuid(owcOffering.uuid).isEmpty) {
              owcOfferingDAO.createOwcOffering(owcOffering)
            }

            SQL(
              s"""insert into $tableOwcEntriesHasOwcOfferings  values (
                 |{owc_feature_types_as_entry_id}, {owc_offerings_uuid}
                 |)
               """.stripMargin).on(
              'owc_feature_types_as_entry_id -> owcEntry.id,
              'owc_offerings_uuid -> owcOffering.uuid.toString
            ).executeUpdate()
          }
        }

        // the single dependent owc properties object
        if (owcPropertiesDAO.findOwcPropertiesByUuid(owcEntry.properties.uuid).isEmpty) {
          owcPropertiesDAO.createOwcProperties(owcEntry.properties)
        }

        SQL(
          s"""insert into $tableOwcFeatureTypesHasOwcProperties  values (
             |{owc_feature_types_id}, {owc_properties_uuid}
             |)
               """.stripMargin).on(
          'owc_feature_types_id -> owcEntry.id,
          'owc_properties_uuid -> owcEntry.properties.uuid.toString
        ).executeUpdate()

        rowCount match {
          case 1 => Some(owcEntry)
          case _ => None
        }
      }
    }
  }

  /**
    * Not yet implemented, update OwcEntry and hierarchical dependents
    *
    * @param owcEntry
    * @return
    */
  def updateOwcEntry(owcEntry: OwcEntry): Option[OwcEntry] = ???

  /**
    * delete an owc featuretype (OwcEntry) with dependnt properties and offerings
    *
    * @param owcEntry
    * @return
    */
  def deleteOwcEntry(owcEntry: OwcEntry): Boolean = {

    val rowCount = db.withTransaction {
      implicit connection => {
        SQL(s"""delete from $tableOwcEntriesHasOwcOfferings where owc_feature_types_as_entry_id = {id}""").on(
          'id -> owcEntry.id
        ).executeUpdate()

        SQL(s"""delete from $tableOwcFeatureTypesHasOwcProperties where owc_feature_types_id = {id}""").on(
          'id -> owcEntry.id
        ).executeUpdate()

        SQL(s"delete from $tableOwcFeatureTypes where id = {id}").on(
          'id -> owcEntry.id
        ).executeUpdate()
      }
    }

    db.withConnection(
      implicit connection => {

        owcEntry.offerings.filter {
          offering => {
            SQL(s"""select owc_offerings_uuid from $tableOwcEntriesHasOwcOfferings where owc_offerings_uuid = {uuid}""").on(
              'uuid -> offering.uuid.toString
            ).as(SqlParser.str("owc_offerings_uuid") *).isEmpty
          }
        }.foreach(owcOfferingDAO.deleteOwcOffering(_))

        if (SQL(s"""select owc_properties_uuid from $tableOwcFeatureTypesHasOwcProperties where owc_properties_uuid = {uuid}""").on(
          'uuid -> owcEntry.properties.uuid.toString
        ).as(SqlParser.str("owc_properties_uuid") *).isEmpty) {
          owcPropertiesDAO.deleteOwcProperties(owcEntry.properties)
        }
      }
    )

    rowCount match {
      case 1 => true
      case _ => false
    }
  }

  /** *********
    * OwcDocument
    * **********/

  /**
    * get all OwcDocuments
    *
    * @return
    */
  def getAllOwcDocuments: Seq[OwcDocument] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcFeatureTypes where feature_type = {feature_type}""").on(
        'feature_type -> "OwcDocument"
      ).as(owcDocumentParser *)
    }
  }

  /**
    * get all publicly visible OwcDocuments
    *
    * @return
    */
  def getAllPublicOwcDocuments: Seq[OwcDocument] = {
    findOwcDocumentsByVisibility(2)
  }

  /**
    * find all OwcDocumenst by visibility
    *
    * @param visibility 0: user-owned/private, 1: organisation, 2: public
    * @return
    */
  def findOwcDocumentsByVisibility(visibility: Int): Seq[OwcDocument] = {
    db.withConnection { implicit connection =>

      SQL(
        s"""select ft.id as id, ft.feature_type as feature_type, ft.bbox as bbox
           |FROM $tableOwcFeatureTypes ft JOIN $tableUserHasOwcDocuments u ON ft.id=u.owc_feature_types_as_document_id
           |where feature_type = {feature_type} AND u.visibility >= {visibility}""".stripMargin).on(
        'feature_type -> "OwcDocument",
        'visibility -> visibility
      ).as(owcDocumentParser *)
    }
  }

  /**
    * find OwcDocumenst by user and type
    *
    * @param email
    * @param collectionType DEFAULT: user personal default, CUSTOM: general purpose
    * @return
    */
  def findOwcDocumentsByUserAndType(email: String, collectionType: String): Seq[OwcDocument] = {
    db.withConnection { implicit connection =>

      SQL(
        s"""select ft.id as id, ft.feature_type as feature_type, ft.bbox as bbox
           | FROM $tableOwcFeatureTypes ft JOIN $tableUserHasOwcDocuments u ON ft.id=u.owc_feature_types_as_document_id
           | where feature_type = {feature_type}
           | AND u.users_email = {email}
           | AND u.collection_type = {collection_type} """.stripMargin).on(
        'feature_type -> "OwcDocument",
        'email -> email,
        'collection_type -> collectionType
      ).as(owcDocumentParser *)
    }
  }

  def findUserDefaultOwcDocument(email: String): Option[OwcDocument] = {
    findOwcDocumentsByUserAndType(email, "DEFAULT").headOption
  }

  /**
    * find an OwcDocument by its id
    *
    * @param id
    * @return
    */
  def findOwcDocumentsById(id: String): Option[OwcDocument] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""select * from $tableOwcFeatureTypes
           |where id = {id} AND feature_type = {feature_type}""".stripMargin).on(
        'id -> id,
        'feature_type -> "OwcDocument"
      ).as(owcDocumentParser.singleOpt)
    }
  }

  /**
    * find an OwcDocument by its id
    *
    * @param id
    * @return
    */
  def findPublicOwcDocumentsById(id: String): Option[OwcDocument] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""select ft.id as id, ft.feature_type as feature_type, ft.bbox as bbox
           |FROM $tableOwcFeatureTypes ft JOIN $tableUserHasOwcDocuments u ON ft.id=u.owc_feature_types_as_document_id
           |where ft.feature_type = {feature_type} AND ft.id = {id} AND u.visibility >= {visibility}""".stripMargin).on(
        'feature_type -> "OwcDocument",
        'id -> id,
        'visibility -> 2
      ).as(owcDocumentParser.singleOpt)
    }
  }

  /**
    * find an OwcDocument by its id and user
    *
    * @param id
    * @param email
    * @return
    */
  def findOwcDocumentByIdAndUser(id: String, email: String): Option[OwcDocument] = {
    db.withConnection { implicit connection =>

      SQL(
        s"""select ft.id as id, ft.feature_type as feature_type, ft.bbox as bbox
           |FROM $tableOwcFeatureTypes ft JOIN $tableUserHasOwcDocuments u ON ft.id=u.owc_feature_types_as_document_id
           |where ft = {id} AND feature_type = {feature_type} AND u.users_email = {email}""".stripMargin).on(
        'id -> id,
        'feature_type -> "OwcDocument",
        'email -> email
      ).as(owcDocumentParser.singleOpt)
    }
  }

  /**
    * find an OwcDocument by user
    *
    * @param email
    * @return
    */
  def findOwcDocumentByUser(email: String): Seq[OwcDocument] = {
    db.withConnection { implicit connection =>

      SQL(
        s"""select ft.id as id, ft.feature_type as feature_type, ft.bbox as bbox
           |FROM $tableOwcFeatureTypes ft JOIN $tableUserHasOwcDocuments u ON ft.id=u.owc_feature_types_as_document_id
           |where feature_type = {feature_type} AND u.users_email = {email}""".stripMargin).on(
        'feature_type -> "OwcDocument",
        'email -> email
      ).as(owcDocumentParser *)
    }
  }

  /**
    * create Users Default collection aka Owc Document, visibility 0 > private,
    * collectionType "DEFAULT"
    *
    * @param owcDocument
    * @return
    */
  def createUsersDefaultOwcDocument(owcDocument: OwcDocument, email: String): Option[OwcDocument] = {
    createOwcDocument(owcDocument, email, 0, "DEFAULT")
  }

  /**
    * create Users Default collection aka Owc Document, visibility 0 > private,
    * collectionType "CUSTOM"
    *
    * @param owcDocument
    * @return
    */
  def createCustomOwcDocument(owcDocument: OwcDocument, email: String): Option[OwcDocument] = {
    createOwcDocument(owcDocument, email, 0, "CUSTOM")
  }

  /**
    * create/insert into db Owc Document
    *
    * @param owcDocument
    * @return
    */
  def createOwcDocument(owcDocument: OwcDocument, email: String, visibility: Int, collectionType: String): Option[OwcDocument] = {
    db.withTransaction {
      implicit connection => {

        val rowCount = SQL(
          s"""
          insert into $tableOwcFeatureTypes values (
            {id}, {feature_type}, {bbox}
          )
        """).on(
          'id -> owcDocument.id,
          'feature_type -> "OwcDocument",
          'bbox -> owcDocument.bbox.map(rect => rectToWkt(rect))
        ).executeUpdate()

        owcDocument.features.foreach {
          owcEntry => {
            if (findOwcEntriesById(owcEntry.id).isEmpty) {
              createOwcEntry(owcEntry)
            }

            SQL(
              s"""insert into $tableOwcDocumentsHasOwcEntries  values (
                 |{owc_feature_types_as_document_id}, {owc_feature_types_as_entry_id}
                 |)
               """.stripMargin).on(
              'owc_feature_types_as_document_id -> owcDocument.id,
              'owc_feature_types_as_entry_id -> owcEntry.id
            ).executeUpdate()
          }
        }

        // the single dependent owc properties object
        if (owcPropertiesDAO.findOwcPropertiesByUuid(owcDocument.properties.uuid).isEmpty) {
          owcPropertiesDAO.createOwcProperties(owcDocument.properties)
        }

        SQL(
          s"""insert into $tableOwcFeatureTypesHasOwcProperties  values (
             |{owc_feature_types_id}, {owc_properties_uuid}
             |)
               """.stripMargin).on(
          'owc_feature_types_id -> owcDocument.id,
          'owc_properties_uuid -> owcDocument.properties.uuid.toString
        ).executeUpdate()

        SQL(
          s"""insert into $tableUserHasOwcDocuments values (
             |{email}, {owcDocumentId}, {collection_type}, {visibility}
             |)""".stripMargin).on(
          'email -> email,
          'owcDocumentId -> owcDocument.id,
          'collection_type -> collectionType,
          'visibility -> visibility
        ).executeUpdate()

        rowCount match {
          case 1 => Some(owcDocument)
          case _ => None
        }
      }
    }
  }

  /**
    * Not yet implemented, update OwcDocument and hierarchical dependents
    *
    * @param owcDocument
    * @return
    */
  def updateOwcDocumentTypeAndVisibility(owcDocument: OwcDocument,
                                         email: String,
                                         visibility: Int,
                                         collectionType: String): Option[OwcDocument] = {

    db.withTransaction {
      implicit connection => {
        val rowCount = SQL(
          s"""UPDATE $tableUserHasOwcDocuments SET
             |collection_type = {collection_type},
             |visibility = {visibility}
             |WHERE email = {email} AND owcDocumentId = {owcDocumentId}
             |""".stripMargin).on(
          'collection_type -> collectionType,
          'visibility -> visibility,
          'email -> email,
          'owcDocumentId -> owcDocument.id
        ).executeUpdate()

        rowCount match {
          case 1 => Some(owcDocument)
          case _ => None
        }
      }
    }

  }

  /**
    *
    * @param owcDocument
    * @param email
    * @return
    */
  def updateOwcDocument(owcDocument: OwcDocument, email: String) : Option[OwcDocument] = {
    None
  }

  /**
    * delete a full owc document hierarchically
    *
    * @param owcDocument
    * @return
    */
  def deleteOwcDocument(owcDocument: OwcDocument): Boolean = {

    val rowCount = db.withTransaction {
      implicit connection => {
        SQL(s"""delete from $tableOwcDocumentsHasOwcEntries where owc_feature_types_as_document_id = {id}""").on(
          'id -> owcDocument.id
        ).executeUpdate()

        SQL(s"""delete from $tableOwcFeatureTypesHasOwcProperties where owc_feature_types_id = {id}""").on(
          'id -> owcDocument.id
        ).executeUpdate()

        SQL(s"""delete from $tableUserHasOwcDocuments where owc_feature_types_as_document_id = {id}""").on(
          'id -> owcDocument.id
        ).executeUpdate()

        SQL(s"delete from $tableOwcFeatureTypes where id = {id}").on(
          'id -> owcDocument.id
        ).executeUpdate()
      }
    }

    db.withConnection(
      implicit connection => {

        owcDocument.features.filter {
          owcEntry => {
            SQL(s"""select owc_feature_types_as_entry_id from $tableOwcDocumentsHasOwcEntries where owc_feature_types_as_entry_id = {id}""").on(
              'id -> owcEntry.id
            ).as(SqlParser.str("owc_feature_types_as_entry_id") *).isEmpty
          }
        }.foreach(deleteOwcEntry(_))

        if (SQL(s"""select owc_properties_uuid from $tableOwcFeatureTypesHasOwcProperties where owc_properties_uuid = {uuid}""").on(
          'uuid -> owcDocument.properties.uuid.toString
        ).as(SqlParser.str("owc_properties_uuid") *).isEmpty) {
          owcPropertiesDAO.deleteOwcProperties(owcDocument.properties)
        }
      }
    )

    rowCount match {
      case 1 => true
      case _ => false
    }
  }
}
