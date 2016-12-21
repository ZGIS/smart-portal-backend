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
import java.util.UUID
import javax.inject.{Inject, Singleton}

import anorm.SqlParser.{get, str}
import anorm.{RowParser, SQL, SqlParser, ~}
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
class OwcOfferingDAO @Inject()(db: Database) extends ClassnameLogger {

  /** *********
    * OwcOperation
    * **********/

  /**
    * Parse a OwcOperation from a ResultSet
    */
  val owcOperationParser = {
    str("uuid") ~
      str("code") ~
      str("method") ~
      str("content_type") ~
      str("href") ~
      get[Option[String]]("request_content_type") ~
      get[Option[String]]("request_post_data") ~
      get[Option[String]]("result_content_type") ~
      get[Option[String]]("result_data") map {
      case uuid ~ code ~ method ~ contentType ~ href ~ requestContentType ~ requestPostData ~ resultContentType ~ resultData =>
        OwcOperation(UUID.fromString(uuid), code, method, contentType, href, requestConfigParser(requestContentType, requestPostData), requestResultParser(resultContentType, resultData))
    }
  }

  /**
    * build request config if both fields are defined
    *
    * @param contentTypeOpt
    * @param postDataOpt
    * @return
    */
  def requestConfigParser(
                           contentTypeOpt: Option[String],
                           postDataOpt: Option[String]
                         ): Option[OwcPostRequestConfig] = {
    (contentTypeOpt, postDataOpt) match {
      case (contentType, postData) if contentType.isDefined && postData.isDefined => {
        Some(OwcPostRequestConfig(contentType, postData))
      }
      case _ => None
    }
  }

  /**
    * build request config if both fields are defined
    *
    * @param contentTypeOpt
    * @param resultDataOpt
    * @return
    */
  def requestResultParser(
                           contentTypeOpt: Option[String],
                           resultDataOpt: Option[String]
                         ): Option[OwcRequestResult] = {
    (contentTypeOpt, resultDataOpt) match {
      case (contentType, resultData) if resultData.isDefined => {
        Some(OwcRequestResult(contentType, resultData))
      }
      case _ => None
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
    * finds owc operations from the offerings relation
    *
    * @param offeringUuid
    * @return
    */
  def findOwcOperationsByOfferingUUID(offeringUuid: UUID): Seq[OwcOperation] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""SELECT op.uuid as uuid, op.code as code, op.method as method, op.content_type as content_type,
           | op.href as href, op.request_content_type as request_content_type, op.request_post_data as request_post_data,
           | op.result_content_type as result_content_type, op.result_data as result_data
           | FROM $tableOwcOperations op JOIN $tableOwcOfferingsHasOwcOperations ofop ON op.uuid=ofop.owc_operations_uuid
           | WHERE ofop.owc_offerings_uuid={uuid}""".stripMargin).on(
        'uuid -> offeringUuid.toString
      )
        .as(owcOperationParser *)
    }
  }

  /**
    * Create an owcOperation.
    *
    * @param owcOperation
    * @return
    */
  def createOwcOperation(owcOperation: OwcOperation): Option[OwcOperation] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        s"""
          insert into $tableOwcOperations values (
            {uuid}, {code}, {method}, {content_type}, {href}, {request_content_type}, {request_post_data}, {result_content_type}, {result_data}
          )
        """).on(
        'uuid -> owcOperation.uuid.toString,
        'code -> owcOperation.code,
        'method -> owcOperation.method,
        'content_type -> owcOperation.contentType,
        'href -> owcOperation.href,
        'request_content_type -> owcOperation.request.map(_.contentType.orNull),
        'request_post_data -> owcOperation.request.map(_.postData.orNull),
        'result_content_type -> owcOperation.result.map(_.contentType.orNull),
        'result_data -> owcOperation.result.map(_.resultData.orNull)
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcOperation)
        case _ => None
      }
    }
  }

  /**
    * Update single OwcOperation
    *
    * @param owcOperation
    * @return
    */
  def updateOwcOperation(owcOperation: OwcOperation): Option[OwcOperation] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        s"""
           |update $tableOwcOperations set
           |code = {code},
           |method = {method},
           |content_type = {content_type},
           |href = {href},
           |request_content_type = {request_content_type},
           |request_post_data = {request_post_data},
           |result_content_type = {request_content_type},
           |result_data = {result_data} where uuid = {uuid}
        """.stripMargin).on(
        'code -> owcOperation.code,
        'method -> owcOperation.method,
        'content_type -> owcOperation.contentType,
        'href -> owcOperation.href,
        'request_content_type -> owcOperation.request.map(_.contentType.orNull),
        'request_post_data -> owcOperation.request.map(_.postData.orNull),
        'result_content_type -> owcOperation.result.map(_.contentType.orNull),
        'result_data -> owcOperation.result.map(_.resultData.orNull),
        'uuid -> owcOperation.uuid.toString
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcOperation)
        case _ => None
      }
    }

  }

  /**
    * delete an OwcOperation
    *
    * @param owcOperation
    * @return
    */
  def deleteOwcOperation(owcOperation: OwcOperation): Boolean = {
    val rowCount = db.withConnection { implicit connection =>
      SQL(s"delete from $tableOwcOperations where uuid = {uuid}").on(
        'uuid -> owcOperation.uuid.toString
      ).executeUpdate()
    }

    rowCount match {
      case 1 => true
      case _ => false
    }
  }

  /** *********
    * OwcOffering
    * **********/

  /**
    * instantiates the concrete type of the offering,
    * I believe there is possibly also something from Ratiopharm, like Class.forName, reflection etc,
    * I am not even sure right now if we need the type
    *
    * @param uuid
    * @param offeringType
    * @param code
    * @param content
    * @return
    */
  def instantiateOwcOffering(uuid: String, offeringType: String, code: String, content: Option[String]): OwcOffering = {
    val uuidObj = UUID.fromString(uuid)
    val ops = findOwcOperationsByOfferingUUID(UUID.fromString(uuid)).toList
    val contentList = content.map(text => List(text)).getOrElse(List())

    offeringType match {
      case "WmsOffering" => WmsOffering(uuidObj, code, ops, contentList)
      case "WmtsOffering" => WmtsOffering(uuidObj, code, ops, contentList)
      case "WfsOffering" => WfsOffering(uuidObj, code, ops, contentList)
      case "WcsOffering" => WcsOffering(uuidObj, code, ops, contentList)
      case "CswOffering" => CswOffering(uuidObj, code, ops, contentList)
      case "WpsOffering" => WpsOffering(uuidObj, code, ops, contentList)
      case "GmlOffering" => GmlOffering(uuidObj, code, ops, contentList)
      case "KmlOffering" => KmlOffering(uuidObj, code, ops, contentList)
      case "GeoTiffOffering" => GeoTiffOffering(uuidObj, code, ops, contentList)
      case "SosOffering" => SosOffering(uuidObj, code, ops, contentList)
      case "NetCdfOffering" => NetCdfOffering(uuidObj, code, ops, contentList)
      case "HttpLinkOffering" => HttpLinkOffering(uuidObj, code, ops, contentList)
      case _ => throw new InvalidClassException(offeringType, s"Unknown Offering type $offeringType")
    }
  }

  /**
    * Parse a OwcOperation from a ResultSet
    */
  val owcOfferingParser: RowParser[OwcOffering] = {
    str("uuid") ~
      str("offering_type") ~
      str("code") ~
      get[Option[String]]("content") map {
      case uuid ~ offeringType ~ code ~ content =>
        instantiateOwcOffering(uuid, offeringType, code, content)
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
    * get OwcOfferings for a featuretype (OwcEntry or OwcDocument)
    * @param featureTypeId
    * @return
    */
  def findOwcOfferingsForOwcEntry(featureTypeId: String) : Seq[OwcOffering] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""SELECT
           |o.uuid as uuid, o.offering_type as offering_type, o.code as code, o.content as content
           |FROM $tableOwcOfferings o JOIN $tableOwcEntriesHasOwcOfferings eof ON o.uuid=eof.owc_offerings_uuid
           |WHERE eof.owc_feature_types_as_entry_id={owc_feature_types_as_entry_id}""".stripMargin).on(
        'owc_feature_types_as_entry_id -> featureTypeId
      )
        .as(owcOfferingParser *)
    }
  }



  /**
    * creates an offering and its corresponding child operations
    *
    * @param owcOffering
    * @return
    */
  def createOwcOffering(owcOffering: OwcOffering): Option[OwcOffering] = {
    db.withTransaction {
      implicit connection => {

        val rowCount = SQL(
          s"""
          insert into $tableOwcOfferings values (
            {uuid}, {offering_type}, {code}, {content}
          )
        """).on(
          'uuid -> owcOffering.uuid.toString,
          'offering_type -> owcOffering.getClass.getSimpleName,
          'code -> owcOffering.code,
          'content -> owcOffering.content.headOption
        ).executeUpdate()

        owcOffering.operations.foreach {
          owcOperation => {
            if (findOwcOperationByUuid(owcOperation.uuid).isEmpty) {
              createOwcOperation(owcOperation)
            }

            SQL(
              s"""insert into $tableOwcOfferingsHasOwcOperations  values (
                 |{owc_offerings_uuid}, {owc_operations_uuid}
                 |)
               """.stripMargin).on(
              'owc_offerings_uuid -> owcOffering.uuid.toString,
              'owc_operations_uuid -> owcOperation.uuid.toString
            ).executeUpdate()
          }
        }

        rowCount match {
          case 1 => Some(owcOffering)
          case _ => None
        }
      }
    }
  }

  /**
    * Not yet implemented, update OwcOffering and hierarchical dependents
    *
    * @param owcOffering
    * @return
    */
  def updateOwcOffering(owcOffering: OwcOffering): Option[OwcOffering] = ???

  /**
    * deletes an offering and its corresponding operations, tries to eliminate orphaned operations
    *
    * @param owcOffering
    * @return
    */
  def deleteOwcOffering(owcOffering: OwcOffering): Boolean = {
    val rowCount = db.withTransaction {
      implicit connection => {

        SQL(s"""delete from $tableOwcOfferingsHasOwcOperations where owc_offerings_uuid = {uuid}""").on(
          'uuid -> owcOffering.uuid.toString
        ).executeUpdate()

        SQL(s"delete from $tableOwcOfferings where uuid = {uuid}").on(
          'uuid -> owcOffering.uuid.toString
        ).executeUpdate()

      }
    }

    db.withConnection(
      implicit connection => {
        owcOffering.operations.filter {
          operation => {
            SQL(s"""select owc_operations_uuid from $tableOwcOfferingsHasOwcOperations where owc_operations_uuid = {uuid}""").on(
              'uuid -> operation.uuid.toString
            ).as(SqlParser.str("owc_operations_uuid") *).isEmpty
          }
        }.foreach(deleteOwcOperation(_))
      }
    )

    rowCount match {
      case 1 => true
      case _ => false
    }
  }
}