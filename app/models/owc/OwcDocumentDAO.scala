/*
 * Copyright (C) 2011-2017 Interfaculty Department of Geoinformatics, University of
 * Salzburg (Z_GIS) & Institute of Geological and Nuclear Sciences Limited (GNS Science)
 * in the SMART Aquifer Characterisation (SAC) programme funded by the New Zealand
 * Ministry of Business, Innovation and Employment (MBIE)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
  private lazy val minLon = ctx.getWorldBounds.getMinX
  private lazy val maxLon = ctx.getWorldBounds.getMaxX
  private lazy val minLat = ctx.getWorldBounds.getMinY
  private lazy val maxLat = ctx.getWorldBounds.getMaxY

  /**
    * quick and dirty bbox from wkt stored in DB
    *
    * @param bboxAsWkt
    * @return
    */
  def createOptionalBbox(bboxAsWkt: Option[String]) : Option[Rectangle] = {
    bboxAsWkt.map {
      bboxString => {
        wktReader.read(bboxAsWkt).asInstanceOf[Rectangle]
      }
    }
  }

  /**
    * quick and dirty string from a bbox to store wkt in DB
    *
    * @param rect
    * @return
    */
  def rectToWkt(rect: Rectangle) : String = {
    val shpWriter = ctx.getFormats().getWriter(ShapeIO.WKT)
    shpWriter.toString(rect)
  }


  /**
    * instantiate an OwcFeatureType either OwcEntry, or OwcDocument
    * @param id
    * @param featureType
    * @param bboxAsWkt
    * @return
    */
  def instantiateOwcFeatureType(id: String, featureType: String, bboxAsWkt: Option[String]): OwcFeatureType = {

    val emptyProfile = OwcLink(UUID.randomUUID(), "profile", None, "http://www.opengis.net/spec/owc-atom/1.0/req/core", None)
    val emptySelf = OwcLink(UUID.randomUUID(), "self", Some("application/json"), "http://example.com/empty", None)
    val now = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())

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
        val offerings = owcOfferingDAO.findOwcOfferingsForOwcEntry(id).toList
        OwcEntry(id, rectOpt, props, offerings)
      }
      case "OwcDocument" => {
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
  val owcEntryParser : RowParser[OwcEntry] = {
    str("id") ~
      str("feature_type") ~
      get[Option[String]]("bbox") map {
      case id ~ featureType ~ bboxAsWkt =>
        instantiateOwcFeatureType(id, featureType, bboxAsWkt).asInstanceOf[OwcEntry]
    }
  }

  /**
    * Parse an OwcDocument from a ResultSet
    */
  val owcDocumentParser : RowParser[OwcDocument] = {
    str("id") ~
      str("feature_type") ~
      get[Option[String]]("bbox") map {
      case id ~ featureType ~ bboxAsWkt =>
        instantiateOwcFeatureType(id, featureType, bboxAsWkt).asInstanceOf[OwcDocument]
    }
  }

  /**
    * get all OwcEntries
    * @return
    */
  def getAllOwcEntries: Seq[OwcEntry] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcFeatureTypes where feature_type = {feature_type}""").on(
      'feature_type -> OwcEntry.getClass.getSimpleName
    ).as(owcEntryParser *)
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
        'feature_type -> OwcEntry.getClass.getSimpleName
      ).as(owcEntryParser.singleOpt)
    }
  }

  def findOwcEntriesForOwcDocument(documentId: String): Seq[OwcEntry] = ???

  /**
    * create an owc entry with dependent offerings and properties
    *
    * @param owcEntry
    * @return
    */
  def createOwcEntry(owcEntry: OwcEntry): Option[OwcEntry] = {

    db.withTransaction {
      implicit connection => {

        val rowCount = SQL(
          s"""
          insert into $tableOwcFeatureTypes values (
            {id}, {feature_type}, {bbox}
          )
        """).on(
          'id -> owcEntry.id,
          'feature_type -> owcEntry.getClass.getSimpleName,
          'bbox -> owcEntry.bbox.map( rect => rectToWkt(rect) )
        ).executeUpdate()

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

  def updateOwcEntry(owcEntry: OwcEntry): Option[OwcEntry] = ???

  def deleteOwcEntry(owcEntry: OwcEntry): Option[OwcEntry] = ???

  /** *********
    * OwcDocument
    * **********/

  /**
    *  get all OwcDocuments
    * @return
    */
  def getAllOwcDocuments: Seq[OwcDocument] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcFeatureTypes where feature_type = {feature_type}""").on(
        'feature_type -> OwcDocument.getClass.getSimpleName
      ).as(owcDocumentParser *)
    }
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
        'feature_type -> OwcEntry.getClass.getSimpleName
      ).as(owcDocumentParser.singleOpt)
    }
  }

  def findOwcDocumentsByBbox(bbox: Rectangle, operation: String = "Intersect") = ???

  def createOwcDocument(owcDocument: OwcDocument): Option[OwcDocument] =  {
    db.withTransaction {
      implicit connection => {

        val rowCount = SQL(
          s"""
          insert into $tableOwcFeatureTypes values (
            {id}, {feature_type}, {bbox}
          )
        """).on(
          'id -> owcDocument.id,
          'feature_type -> owcDocument.getClass.getSimpleName,
          'bbox -> owcDocument.bbox.map( rect => rectToWkt(rect) )
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

        rowCount match {
          case 1 => Some(owcDocument)
          case _ => None
        }
      }
    }
  }

  def updateOwcDocument(owcDocument: OwcDocument): Option[OwcDocument] = ???

  def deleteOwcDocument(owcDocument: OwcDocument): Option[OwcDocument] = ???
}
