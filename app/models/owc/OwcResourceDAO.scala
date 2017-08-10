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
import java.time.OffsetDateTime
import java.util.UUID

import anorm.SqlParser.{get, str}
import anorm.{RowParser, SQL, ~}
import info.smart.models.owc100._
import models.owc.OwcOfferingDAO.findByPropertiesUUID
import utils.{ClassnameLogger, GeoDateParserUtils, OwcGeoJsonFixes}

/**
  * OwcResourceDAO - store and retrieve OWS Context Resources
  */
object OwcResourceDAO extends ClassnameLogger {

  /**
    * Parse an OwcResource from a ResultSet
    */
  private def owcResourceParser(implicit connection: Connection): RowParser[OwcResource] = {
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
          author = authorsUuids.map(u => OwcOfferingDAO.findByPropertiesUUID[OwcAuthor](Some(u))(OwcAuthorEvidence, connection).toList).getOrElse(List()),
          publisher = publisher,
          rights = rights,
          geospatialExtent = GeoDateParserUtils.createOptionalBbox(bboxText),
          temporalExtent = GeoDateParserUtils.parseOffsetDateString(temporalText),
          contentDescription = contentDescUuids.map(u =>
            OwcOfferingDAO.findByPropertiesUUID[OwcLink](Some(u))(OwcLinkEvidence, connection).toList).getOrElse(List()), // links.alternates[] and rel=alternate
          preview = previewUuids.map(u =>
            OwcOfferingDAO.findByPropertiesUUID[OwcLink](Some(u))(OwcLinkEvidence, connection).toList).getOrElse(List()), // aka links.previews[] and rel=icon (atom)
          contentByRef = contentByRefUuids.map(u =>
            OwcOfferingDAO.findByPropertiesUUID[OwcLink](Some(u))(OwcLinkEvidence, connection).toList).getOrElse(List()), // aka links.data[] and rel=enclosure (atom)
          offering = offeringUuids.map(u =>
            OwcOfferingDAO.findByPropertiesUUID[OwcOffering](Some(u))(OwcOfferingEvidence, connection).toList).getOrElse(List()),
          active = active,
          resourceMetadata = resMetaUuids.map(u =>
            OwcOfferingDAO.findByPropertiesUUID[OwcLink](Some(u))(OwcLinkEvidence, connection).toList).getOrElse(List()), // aka links.via[] & rel=via
          keyword = keywordUuids.map(u =>
            OwcOfferingDAO.findByPropertiesUUID[OwcCategory](Some(u))(OwcCategoryEvidence, connection).toList).getOrElse(List()),
          minScaleDenominator = minScale,
          maxScaleDenominator = maxScale,
          folder = folder)
      }
    }
  }


  /**
    * get all OwcResources
    *
    * @return
    */
  def getAllOwcResources(implicit connection: Connection): Seq[OwcResource] = {
    SQL(s"select * from $tableOwcResources").as(owcResourceParser *)
  }

  /**
    * find an OwcResource by its id
    *
    * @param owcResourceId
    * @return
    */
  def findOwcResourceById(owcResourceId: String)(implicit connection: Connection): Option[OwcResource] = {
    SQL(
      s"""select * from $tableOwcResources
         |where id = {id}""".stripMargin).on(
      'id -> owcResourceId
    ).as(owcResourceParser.singleOpt)
  }

  /**
    * find an OwcResource by its id of type [[URL]]
    *
    * @param owcResourceId
    * @param connection
    * @return
    */
  def findOwcResourceById(owcResourceId: URL)(implicit connection: Connection): Option[OwcResource] = {
    findOwcResourceById(owcResourceId.toString)
  }

  /**
    * find all OwcResources for an OwcContext by contextid
    *
    * @param owcContextId
    * @return
    */
  def findOwcResourcesForOwcContext(owcContextId: String)(implicit connection: Connection): Seq[OwcResource] = {
    SQL(
      s"""SELECT
         |$tableOwcResources.*
         |FROM $tableOwcResources JOIN $tableOwcContextHasOwcResources ON $tableOwcResources.id=$tableOwcContextHasOwcResources.owc_resource_id
         |WHERE $tableOwcContextHasOwcResources.owc_context_id={owc_context_id}""".stripMargin).on(
      'owc_context_id -> owcContextId
    )
      .as(owcResourceParser *)
  }

  /**
    * takes a list of OwcAuthor (e.g. for createOwcResource) and tests that non of these exist,
    * and if so creates them in the database and returns success of the overall create
    *
    * @param authors
    * @param connection
    * @return
    */
  def preCreateCheckOwcAuthors(authors: List[OwcAuthor])(implicit connection: Connection): Boolean = {
    if (authors.nonEmpty) {
      val uuidString = authors.map(_.uuid.toString).mkString(":")
      val exists = findByPropertiesUUID[OwcAuthor](Some(uuidString))(OwcAuthorEvidence, connection).toList.nonEmpty
      if (exists) {
        logger.error(s"(createResourceOrContext/authors) OwcAuthor with UUID one of: ${uuidString} exists already, recommend abort")
        false
      } else {
        val insert = authors.map(OwcAuthorDAO.createOwcAuthor(_)).filter(_.isDefined)
        insert.length == authors.length
      }
    } else {
      true
    }
  }

  /**
    * takes a list of OwcLink (e.g. for createOwcResource) and tests that non of these exist,
    * and if so creates them in the database and returns success of the overall create
    *
    * @param links
    * @param connection
    * @return
    */
  def preCreateCheckOwcLinks(links: List[OwcLink])(implicit connection: Connection): Boolean = {
    if (links.nonEmpty) {
      val uuidString = links.map(_.uuid.toString).mkString(":")
      val exists = findByPropertiesUUID[OwcLink](Some(uuidString))(OwcLinkEvidence, connection).toList.nonEmpty
      if (exists) {
        logger.error(s"(createResourceOrContext/links) OwcLink with UUID one of: ${uuidString} exists already, recommend abort")
        false
      } else {
        val insert = links.map(OwcLinkDAO.createOwcLink(_)).filter(_.isDefined)
        insert.length == links.length
      }
    } else {
      true
    }
  }

  /**
    * takes a list of OwcOffering (e.g. for createOwcResource) and tests that non of these exist,
    * and if so creates them in the database and returns success of the overall create
    *
    * @param offerings
    * @param connection
    * @return
    */
  def preCreateCheckOwcOfferings(offerings: List[OwcOffering])(implicit connection: Connection): Boolean = {
    if (offerings.nonEmpty) {
      val uuidString = offerings.map(_.uuid.toString).mkString(":")
      val exists = findByPropertiesUUID[OwcOffering](Some(uuidString))(OwcOfferingEvidence, connection).toList.nonEmpty
      if (exists) {
        logger.error(s"(createResource/offerings) OwcOffering with UUID one of: ${uuidString} exists already, recommend abort")
        false
      } else {
        val insert = offerings.map(OwcOfferingDAO.createOwcOffering(_)).filter(_.isDefined)
        insert.length == offerings.length
      }
    } else {
      true
    }
  }

  /**
    * takes a list of OwcCategory (e.g. for createOwcResource) and tests that non of these exist,
    * and if so creates them in the database and returns success of the overall create
    *
    * @param categories
    * @param connection
    * @return
    */
  def preCreateCheckOwcCategories(categories: List[OwcCategory])(implicit connection: Connection): Boolean = {
    if (categories.nonEmpty) {
      val uuidString = categories.map(_.uuid.toString).mkString(":")
      val exists = findByPropertiesUUID[OwcCategory](Some(uuidString))(OwcCategoryEvidence, connection).toList.nonEmpty
      if (exists) {
        logger.error(s"(createResourceOrContext/categories) OwcCategory with UUID one of: ${uuidString} exists already, recommend abort")
        false
      } else {
        val insert = categories.map(OwcCategoryDAO.createOwcCategory(_)).filter(_.isDefined)
        insert.length == categories.length
      }
    } else {
      true
    }
  }

  /**
    * create an owc resource with dependent offerings and properties
    *
    * @param owcResourceUnfixed should make issue on github for owc geojson and portal backend
    *                           and then remove the fix see [[OwcGeoJsonFixes]]
    * @return
    */
  def createOwcResource(owcResourceUnfixed: OwcResource)(implicit connection: Connection): Option[OwcResource] = {

    val owcResource = OwcGeoJsonFixes.fixRelPropertyForOwcLinks(owcResourceUnfixed)

    val preCreateCheckOwcAuthorsForResource = preCreateCheckOwcAuthors(owcResource.author)
    val preCreateCheckOwcLinksForResource = preCreateCheckOwcLinks(owcResource.contentDescription ++ owcResource.preview ++
      owcResource.contentByRef ++ owcResource.resourceMetadata)
    val preCreateCheckOwcOfferingsForResource = preCreateCheckOwcOfferings(owcResource.offering)
    val preCreateCheckOwcCategoriesForResource = preCreateCheckOwcCategories(owcResource.keyword)

    if (preCreateCheckOwcAuthorsForResource && preCreateCheckOwcLinksForResource &&
      preCreateCheckOwcOfferingsForResource && preCreateCheckOwcCategoriesForResource) {

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
        'update_date -> owcResource.updateDate.toLocalDateTime,
        'authors -> owcResource.author.map(_.uuid.toString).mkString(":"),
        'publisher -> owcResource.publisher,
        'rights -> owcResource.rights,
        'geospatial_extent -> owcResource.geospatialExtent.map(r => GeoDateParserUtils.rectToWkt(r)),
        'temporal_extent -> owcResource.temporalExtent.map(dates => GeoDateParserUtils.writeOffsetDatesAsDateString(dates)).getOrElse(None),
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
          logger.error("OwcResource couldn't be created")
          // we need to think where to place rollback most appropriately
          connection.rollback()
          None
      }
    } else {
      logger.error("OwcResource/create: one of " +
        s"preCreateCheckOwcAuthorsForResource: $preCreateCheckOwcAuthorsForResource  " +
        s"preCreateCheckOwcLinksForResource: $preCreateCheckOwcLinksForResource + " +
        s"preCreateCheckOwcOfferingsForResource: $preCreateCheckOwcOfferingsForResource + " +
        s"preCreateCheckOwcCategoriesForResource: $preCreateCheckOwcCategoriesForResource was not successful")
      logger.error("Precondition failed, won't create OwcResource")
      // we need to think where to place rollback most appropriately
      connection.rollback()
      None
    }
  }

  /**
    * pre Check for authors Update For an owcAggregator of type [[OwcContext]] or [[OwcResource]],
    * will delete, update and insert required [[OwcAuthor]] in order to make owcAggregator type Update consistent
    *
    * @param owcAggregator of type [[OwcContext]] or [[OwcResource]]
    * @param connection
    * @tparam A of type [[OwcContext]] or [[OwcResource]]
    * @return
    */
  def preUpdateCheckOwcAuthors[A](owcAggregator: A)(implicit connection: Connection): Boolean = {

    // get current list of full OwcAuthor objects,
    val currentOwcAuthors = owcAggregator match {
      case owcAggregator: OwcResource => owcAggregator.author
      case owcAggregator: OwcContext => owcAggregator.author
      case _ => throw new InvalidClassException(s"The evidence parameter type ${owcAggregator.getClass.getCanonicalName} not supported here")
    }

    // get current list of UUIDs,
    val current: List[UUID] = currentOwcAuthors.map(_.uuid)

    // get old list of OwcAuthor,
    val oldOwcAuthors: List[OwcAuthor] = owcAggregator match {
      case owcAggregator: OwcResource => findOwcResourceById(owcAggregator.id).map(o => o.author).getOrElse(List())
      case owcAggregator: OwcContext => OwcContextDAO.findOwcContextById(owcAggregator.id).map(o => o.author).getOrElse(List())
      case _ => throw new InvalidClassException(s"The evidence parameter type ${owcAggregator.getClass.getCanonicalName} not supported here")
    }

    // get old list of UUIDs,
    val old: List[UUID] = oldOwcAuthors.map(_.uuid)

    // in old but not current -> delete
    val toBeDeleted = old.diff(current)
    val deleted = oldOwcAuthors.filter(o => toBeDeleted.contains(o.uuid))
      .map(OwcAuthorDAO.deleteOwcAuthor(_))
      .count(_ == true) == toBeDeleted.length

    // in both lists -> update
    val toBeUpdated = current.intersect(old)
    val updated = currentOwcAuthors.filter(o => toBeUpdated.contains(o.uuid))
      .map(OwcAuthorDAO.updateOwcAuthor(_))
      .count(_.isDefined) == toBeUpdated.length

    // in current but not in old -> insert
    val toBeInserted = current.diff(old)
    val inserted = currentOwcAuthors.filter(o => toBeInserted.contains(o.uuid))
      .map(OwcAuthorDAO.createOwcAuthor(_))
      .count(_.isDefined) == toBeInserted.length

    if (deleted && updated && inserted) {
      true
    } else {
      logger.error(s"(updateAggregator/authors) one of deleted: $deleted , updated: $updated , inserted: $inserted not complete, recommend abort")
      false
    }
  }

  /**
    * pre Check for OwcLinks Update For an owcAggregator of type [[OwcContext]] or [[OwcResource]],
    * will delete, update and insert required [[OwcLink]] in order to make owcAggregator type Update consistent
    *
    * @param owcAggregator of type [[OwcContext]] or [[OwcResource]]
    * @param connection
    * @tparam A of type [[OwcContext]] or [[OwcResource]]
    * @return
    */
  def preUpdateCheckOwcLinks[A](owcAggregator: A)(implicit connection: Connection): Boolean = {

    // get current list of full OwcLink objects,
    val currentOwcLinks = owcAggregator match {
      case owcAggregator: OwcResource =>
        owcAggregator.contentDescription ++ owcAggregator.preview ++ owcAggregator.contentByRef ++ owcAggregator.resourceMetadata
      case owcAggregator: OwcContext => owcAggregator.specReference ++ owcAggregator.contextMetadata
      case _ => throw new InvalidClassException(s"The evidence parameter type ${owcAggregator.getClass.getCanonicalName} not supported here")
    }

    // get old list of OwcLinks,
    val oldOwcLinks: List[OwcLink] = owcAggregator match {
      case owcAggregator: OwcResource =>
        findOwcResourceById(owcAggregator.id).map(o => o.contentDescription ++ o.preview ++
          o.contentByRef ++ o.resourceMetadata).getOrElse(List())
      case owcAggregator: OwcContext =>
        OwcContextDAO.findOwcContextById(owcAggregator.id).map(o => o.specReference ++ o.contextMetadata).getOrElse(List())
      case _ => throw new InvalidClassException(s"The evidence parameter type ${owcAggregator.getClass.getCanonicalName} not supported here")
    }

    // get current list of UUIDs,
    val current: List[UUID] = currentOwcLinks.map(_.uuid)

    // get old list of UUIDs,
    val old: List[UUID] = oldOwcLinks.map(_.uuid)

    // in old but not current -> delete
    val toBeDeleted = old.diff(current)
    val deleted = oldOwcLinks.filter(o => toBeDeleted.contains(o.uuid))
      .map(OwcLinkDAO.deleteOwcLink(_))
      .count(_ == true) == toBeDeleted.length

    // in both lists -> update
    val toBeUpdated = current.intersect(old)
    val updated = currentOwcLinks.filter(o => toBeUpdated.contains(o.uuid))
      .map(OwcLinkDAO.updateOwcLink(_))
      .count(_.isDefined) == toBeUpdated.length

    // in current but not in old -> insert
    val toBeInserted = current.diff(old)
    val inserted = currentOwcLinks.filter(o => toBeInserted.contains(o.uuid))
      .map(OwcLinkDAO.createOwcLink(_))
      .count(_.isDefined) == toBeInserted.length

    if (deleted && updated && inserted) {
      true
    } else {
      logger.error(s"(updateAggregator/links) one of deleted: $deleted , updated: $updated , inserted: $inserted not complete, recommend abort")
      false
    }
  }

  /**
    * pre Check for offerings Update For an [[OwcResource]],
    * will delete, update and insert required [[OwcOffering]] in order to make OwcResource Update consistent
    *
    * @param owcResource
    * @param connection
    * @return
    */
  def preUpdateCheckOwcOfferings(owcResource: OwcResource)(implicit connection: Connection): Boolean = {

    // get current list,
    val current: List[UUID] = owcResource.offering.map(_.uuid)

    // get old list,
    val oldOwcResource = findOwcResourceById(owcResource.id)
    val old: List[UUID] = oldOwcResource.map(o => o.offering.map(_.uuid)).getOrElse(List())

    // in old but not current -> delete
    val toBeDeleted = old.diff(current)
    val deleted = oldOwcResource.map { owcResource =>
      owcResource.offering.filter(o => toBeDeleted.contains(o.uuid))
        .map(OwcOfferingDAO.deleteOwcOffering(_))
    }.count(_ == true) == toBeDeleted.length

    // in both lists -> update
    val toBeUpdated = current.intersect(old)
    val updated = owcResource.offering.filter(o => toBeUpdated.contains(o.uuid))
      .map(OwcOfferingDAO.updateOwcOffering(_))
      .count(_.isDefined) == toBeUpdated.length

    // in current but not in old -> insert
    val toBeInserted = current.diff(old)
    val inserted = owcResource.offering.filter(o => toBeInserted.contains(o.uuid))
      .map(OwcOfferingDAO.createOwcOffering(_))
      .count(_.isDefined) == toBeInserted.length

    if (deleted && updated && inserted) {
      true
    } else {
      logger.error(s"(updateResource/offerings) one of deleted: $deleted , updated: $updated , inserted: $inserted not complete, recommend abort")
      false
    }
  }

  /**
    * pre Check for categories Update For an owcAggregator of type [[OwcContext]] or [[OwcResource]],
    * will delete, update and insert required [[OwcCategory]] in order to make owcAggregator type Update consistent
    *
    * @param owcAggregator
    * @param connection
    * @tparam A
    * @return
    */
  def preUpdateCheckOwcCategories[A](owcAggregator: A)(implicit connection: Connection): Boolean = {

    // get current list of full OwcCategory objects,
    val currentOwcCategories = owcAggregator match {
      case owcAggregator: OwcResource => owcAggregator.keyword
      case owcAggregator: OwcContext => owcAggregator.keyword
      case _ => throw new InvalidClassException(s"The evidence parameter type ${owcAggregator.getClass.getCanonicalName} not supported here")
    }

    // get current list of UUIDs,
    val current: List[UUID] = currentOwcCategories.map(_.uuid)

    // get old list of OwcCategory,
    val oldOwcCategories: List[OwcCategory] = owcAggregator match {
      case owcAggregator: OwcResource => findOwcResourceById(owcAggregator.id).map(o => o.keyword).getOrElse(List())
      case owcAggregator: OwcContext => OwcContextDAO.findOwcContextById(owcAggregator.id).map(o => o.keyword).getOrElse(List())
      case _ => throw new InvalidClassException(s"The evidence parameter type ${owcAggregator.getClass.getCanonicalName} not supported here")
    }

    // get old list of UUIDs,
    val old: List[UUID] = oldOwcCategories.map(_.uuid)

    // in old but not current -> delete
    val toBeDeleted = old.diff(current)
    val deleted = oldOwcCategories.filter(o => toBeDeleted.contains(o.uuid))
      .map(OwcCategoryDAO.deleteOwcCategory(_))
      .count(_ == true) == toBeDeleted.length

    // in both lists -> update
    val toBeUpdated = current.intersect(old)
    val updated = currentOwcCategories.filter(o => toBeUpdated.contains(o.uuid))
      .map(OwcCategoryDAO.updateOwcCategory(_))
      .count(_.isDefined) == toBeUpdated.length

    // in current but not in old -> insert
    val toBeInserted = current.diff(old)
    val inserted = currentOwcCategories.filter(o => toBeInserted.contains(o.uuid))
      .map(OwcCategoryDAO.createOwcCategory(_))
      .count(_.isDefined) == toBeInserted.length

    if (deleted && updated && inserted) {
      true
    } else {
      logger.error(s"(updateAggregator/categories) one of deleted: $deleted , updated: $updated , inserted: $inserted not complete, recommend abort")
      false
    }
  }

  /**
    * update OwcResource and hierarchical dependents
    *
    * @param owcResourceUnfixed should make issue on github for owc geojson and portal backend
    *                           and then remove the fix see [[OwcGeoJsonFixes]]
    * @return
    */
  def updateOwcResource(owcResourceUnfixed: OwcResource)(implicit connection: Connection): Option[OwcResource] = {

    val owcResource = OwcGeoJsonFixes.fixRelPropertyForOwcLinks(owcResourceUnfixed)

    if (preUpdateCheckOwcAuthors(owcResource) && preUpdateCheckOwcLinks(owcResource) &&
      preUpdateCheckOwcOfferings(owcResource) && preUpdateCheckOwcCategories(owcResource)) {

      val rowCount = SQL(
        s"""
          update $tableOwcResources set
          title = {title},
          subtitle = {subtitle},
          update_date = {update_date},
          authors = {authors},
          publisher = {publisher},
          rights = {rights},
          geospatial_extent = {geospatial_extent},
          temporal_extent = {temporal_extent},
          content_description = {content_description},
          preview = {preview},
          content_by_ref = {content_by_ref},
          offering = {offering},
          active = {active},
          resource_metadata = {resource_metadata},
          keyword = {keyword},
          min_scale_denominator = {min_scale_denominator},
          max_scale_denominator = {max_scale_denominator},
          folder = {folder}
          where id = {id}
        """).on(
        'id -> owcResource.id.toString,
        'title -> owcResource.title,
        'subtitle -> owcResource.subtitle,
        'update_date -> owcResource.updateDate.toLocalDateTime,
        'authors -> owcResource.author.map(_.uuid.toString).mkString(":"),
        'publisher -> owcResource.publisher,
        'rights -> owcResource.rights,
        'geospatial_extent -> owcResource.geospatialExtent.map(r => GeoDateParserUtils.rectToWkt(r)),
        'temporal_extent -> owcResource.temporalExtent.map(dates => GeoDateParserUtils.writeOffsetDatesAsDateString(dates)).getOrElse(None),
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
        case _ => logger.error("OwcResource couldn't be updated")
          // we need to think where to place rollback most appropriately
          connection.rollback()
          None
      }
    } else {
      logger.error("Precondition failed, won't update OwcResource")
      // we need to think where to place rollback most appropriately
      connection.rollback()
      None
    }
  }

  /**
    * delete an OwcResource with dependent offerings and properties links etc hierarchically
    *
    * @param owcResource
    * @return
    */
  def deleteOwcResource(owcResource: OwcResource)(implicit connection: Connection): Boolean = {

    val preDeleteCheckOwcAuthors = if (owcResource.author.nonEmpty) {
      owcResource.author.map(
        o => OwcAuthorDAO.deleteOwcAuthor(o)).count(_ == true) == owcResource.author.size
    } else {
      true
    }

    val allOwcLinks = owcResource.contentDescription ++ owcResource.preview ++
      owcResource.contentByRef ++ owcResource.resourceMetadata

    val preDeleteCheckOwcLinks = if (allOwcLinks.nonEmpty) {
      allOwcLinks.map(
        o => OwcLinkDAO.deleteOwcLink(o)).count(_ == true) == allOwcLinks.size
    } else {
      true
    }

    val preDeleteCheckOwcOfferings = if (owcResource.offering.nonEmpty) {
      owcResource.offering.map(
        o => OwcOfferingDAO.deleteOwcOffering(o)).count(_ == true) == owcResource.offering.size
    } else {
      true
    }

    val preDeleteCheckOwcCategories = if (owcResource.keyword.nonEmpty) {
      owcResource.keyword.map(
        o => OwcCategoryDAO.deleteOwcCategory(o)).count(_ == true) == owcResource.keyword.size
    } else {
      true
    }

    if (preDeleteCheckOwcAuthors && preDeleteCheckOwcLinks &&
      preDeleteCheckOwcOfferings && preDeleteCheckOwcCategories) {

      val rowCount = SQL(s"delete from $tableOwcResources where id = {id}").on(
        'id -> owcResource.id.toString
      ).executeUpdate()

      rowCount match {
        case 1 => true
        case _ =>
          logger.error("OwcResource couldn't be deleted")
          // we need to think where to place rollback most appropriately
          connection.rollback()
          false
      }
    } else {
      logger.error("Precondition failed, won't delete OwcResource")
      // we need to think where to place rollback most appropriately
      connection.rollback()
      false
    }
  }
}
