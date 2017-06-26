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
import java.util.UUID
import javax.inject.{Inject, Singleton}

import anorm.SqlParser._
import anorm._
import info.smart.models.owc100._
import play.api.db._
import uk.gov.hmrc.emailaddress.EmailAddress
import utils.ClassnameLogger
import utils.StringUtils.OptionUuidConverters

import anorm.Column

/**
  * OwcPropertiesDAO - store and retrieve OWS Context Documents
  * An OWC document is an extended FeatureCollection, where the features (aka entries) hold a variety of metadata
  * about the things they provide the context for (i.e. other data sets, services, metadata records)
  * OWC documents do not duplicate a CSW MD_Metadata record, but a collection of referenced resources;
  *
  * @param db
  */
@Singleton
class OwcPropertiesDAO @Inject()(db: Database) extends ClassnameLogger {

  /** *********
    * OwcAuthor
    * **********/

  /**
    * Parse a OwcAuthor from a ResultSet
    */
  val owcAuthorParser = {
    get[Option[String]]("name") ~
      get[Option[String]]("email") ~
      get[Option[String]]("uri") ~
      str("uuid") map {
      case name ~ email ~ uri ~ uuid =>
        OwcAuthor(name, email.map(EmailAddress(_)), uri.map(new URL(_)), UUID.fromString(uuid))
    }
  }

  /**
    * Create an owcAuthor.
    *
    * @param owcAuthor
    * @return
    */
  def createOwcAuthor(owcAuthor: OwcAuthor): Option[OwcAuthor] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        s"""
          insert into $tableOwcAuthors values (
            {uuid}, {name}, {email}, {uri}
          )
        """).on(
        'uuid -> owcAuthor.uuid.toString,
        'name -> owcAuthor.name,
        'email -> owcAuthor.email.map(_.toString()),
        'uri -> owcAuthor.uri.map(_.toString)
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcAuthor)
        case _ => None
      }
    }
  }

  /**
    * Retrieve all OwcAuthors.
    *
    * @return
    */
  def getAllOwcAuthors: Seq[OwcAuthor] = {
    db.withConnection { implicit connection =>
      SQL(s"select * from $tableOwcAuthors").as(owcAuthorParser *)
    }
  }

  /**
    * Find specific OwcAuthor.
    *
    * @param uuid
    * @return
    */
  def findOwcAuthorByUuid(uuid: UUID): Option[OwcAuthor] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcAuthors where uuid = '${uuid.toString}'""").as(owcAuthorParser.singleOpt)
    }
  }

  /**
    * Find specific OwcAuthor.
    *
    * @param name
    * @return
    */
  def findOwcAuthorByName(name: String): Option[OwcAuthor] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcAuthors where name like '${name}'""").as(owcAuthorParser.singleOpt)
    }
  }

  /**
    * finds owc authors from the properties relation
    *
    * @param propertiesUuid
    * @return
    */
  def findOwcAuthorsByPropertiesUUID(propertiesUuid: Option[String]): Seq[OwcAuthor] = {
    val values = propertiesUuid.map(_.split(":").toSeq).map(
      potUuids => {
        val uuids = potUuids.map(_.toUuidOption).filter(_.isDefined).map(_.get)
        uuids.map(findOwcAuthorByUuid(_)).filter(_.isDefined).map(_.get)
      }
    )
    values.getOrElse(Seq())
  }

  /**
    * Update single OwcAuthor
    *
    * @param owcAuthor
    * @return
    */
  def updateOwcAuthor(owcAuthor: OwcAuthor): Option[OwcAuthor] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        s"""
          update $tableOwcAuthors set
            name = {name},
            email = {email},
            uri = {uri} where uuid = {uuid}
        """).on(
        'name -> owcAuthor.name,
        'email -> owcAuthor.email.map(_.toString()),
        'uri -> owcAuthor.uri.map(_.toString()),
        'uuid -> owcAuthor.uuid.toString
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcAuthor)
        case _ => None
      }
    }

  }


  /**
    * delete an OwcAuthor
    *
    * @param owcAuthor
    * @return
    */
  def deleteOwcAuthor(owcAuthor: OwcAuthor): Boolean = {
    val rowCount = db.withConnection { implicit connection =>
      SQL(s"delete from $tableOwcAuthors where uuid = {uuid}").on(
        'uuid -> owcAuthor.uuid.toString
      ).executeUpdate()
    }

    rowCount match {
      case 1 => true
      case _ => false
    }
  }

  /** ************
    * OwcCategory
    * ************/

  /**
    * Parse a OwcCategory from a ResultSet
    */
  val owcCategoryParser = {
    str("uuid") ~
      get[Option[String]]("scheme") ~
      str("term") ~
      get[Option[String]]("label") map {
      case uuid ~ scheme ~ term ~ label =>
        OwcCategory(term, scheme, label, UUID.fromString(uuid))
    }
  }

  /**
    * Retrieve all OwcCategory.
    *
    * @return
    */
  def getAllOwcCategories: Seq[OwcCategory] = {
    db.withConnection { implicit connection =>
      SQL(s"select * from $tableOwcCategories").as(owcCategoryParser *)
    }
  }

  /**
    * Find OwcCategories by uuid
    *
    * @param uuid
    * @return
    */
  def findOwcCategoriesByUuid(uuid: UUID): Option[OwcCategory] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcCategories where uuid like '${uuid.toString()}'""").as(owcCategoryParser.singleOpt)
    }
  }

  /**
    * Create an OwcCategory.
    *
    * @param owcCategory
    * @return
    */
  def createOwcCategory(owcCategory: OwcCategory): Option[OwcCategory] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        s"""
          insert into $tableOwcCategories values (
            {uuid}, {scheme}, {term}, {label}
          )
        """).on(
        'uuid -> owcCategory.uuid.toString,
        'scheme -> owcCategory.scheme,
        'term -> owcCategory.term,
        'label -> owcCategory.label
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcCategory)
        case _ => None
      }
    }
  }

  /**
    * Find specific OwcCategory by term (could be in multiple schemes)
    *
    * @param term
    * @return
    */
  def findOwcCategoriesByTerm(term: String): Seq[OwcCategory] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcCategories where term like '${term}'""").as(owcCategoryParser *)
    }
  }

  /**
    * Find OwcCategories with same scheme
    *
    * @param scheme
    * @return
    */
  def findOwcCategoriesByScheme(scheme: String): Seq[OwcCategory] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcCategories where scheme like '${scheme}'""").as(owcCategoryParser *)
    }
  }

  /**
    * Find OwcCategory by term and scheme
    *
    * @param term
    * @param scheme
    * @return
    */
  def findOwcCategoriesBySchemeAndTerm(scheme: String, term: String): Seq[OwcCategory] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""select * from $tableOwcCategories
           |where scheme like '${scheme}'
           |AND term like '${term}'
           |""".stripMargin).as(owcCategoryParser *)
    }
  }

  /**
    * finds owc categories from the owc properties relation
    *
    * @param propertiesUuid
    * @return
    */
  def findOwcCategoriesByPropertiesUUID(propertiesUuid: Option[String]): Seq[OwcCategory] = {
    val values = propertiesUuid.map(_.split(":").toSeq).map(
      potUuids => {
        val uuids = potUuids.map(_.toUuidOption).filter(_.isDefined).map(_.get)
        uuids.map(findOwcCategoriesByUuid(_)).filter(_.isDefined).map(_.get)
      }
    )
    values.getOrElse(Seq())
  }

  /**
    * Update single OwcCategory (update the label basically only)
    *
    * @param owcCategory
    * @return
    */
  def updateOwcCategory(owcCategory: OwcCategory): Option[OwcCategory] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        s"""
          update $tableOwcCategories set
            term = {term},
            scheme = {scheme},
            label = {label} where uuid = {uuid}
        """).on(
        'term -> owcCategory.term,
        'scheme -> owcCategory.scheme,
        'label -> owcCategory.label,
        'uuid -> owcCategory.uuid.toString
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcCategory)
        case _ => None
      }
    }

  }

  /**
    * delete an OwcCategory
    *
    * @param owcCategory
    * @return
    */
  def deleteOwcCategory(owcCategory: OwcCategory): Boolean = {
    val rowCount = db.withConnection { implicit connection =>
      SQL(s"delete from $tableOwcCategories where uuid = {uuid}").on(
        'uuid -> owcCategory.uuid.toString
      ).executeUpdate()
    }

    rowCount match {
      case 1 => true
      case _ => false
    }
  }

  /** ************
    * OwcLink
    * ************/

  /**
    * Parse a OwcLink from a ResultSet
    */
  val owcLinkParser = {
    str("uuid") ~
      str("href") ~
      get[Option[String]]("mime_type") ~
      get[Option[String]]("lang") ~
      get[Option[String]]("title") ~
      get[Option[Int]]("length") ~
      str("rel") map {
      case uuid ~ href ~ mimeType ~ lang ~ title ~ length ~ rel =>
        OwcLink(new URL(href), mimeType, lang, title, length, rel, UUID.fromString(uuid))
    }
  }

  /**
    * Retrieve all OwcLink.
    *
    * @return
    */
  def getAllOwcLinks: Seq[OwcLink] = {
    db.withConnection { implicit connection =>
      SQL(s"select * from $tableOwcLinks").as(owcLinkParser *)
    }
  }

  /**
    * Find OwcLinks by href
    *
    * @param href
    * @return
    */
  def findOwcLinksByHref(href: URL): Seq[OwcLink] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcLinks where href like '${href.toString}'""").as(owcLinkParser *)
    }
  }

  /**
    * finds owc links via the properties links relation
    *
    * @param propertiesUuid
    * @return
    */
  def findOwcLinksByPropertiesUUID(propertiesUuid: Option[String]): Seq[OwcLink] = {
    val values = propertiesUuid.map(_.split(":").toSeq).map(
      potUuids => {
        val uuids = potUuids.map(_.toUuidOption).filter(_.isDefined).map(_.get)
        uuids.map(findOwcLinksByUuid(_)).filter(_.isDefined).map(_.get)
      }
    )
    values.getOrElse(Seq())
  }


  /**
    * Update single OwcLink
    *
    * @param owcLink
    * @return
    */
  def updateOwcLink(owcLink: OwcLink): Option[OwcLink] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        s"""
          update $tableOwcLinks set
            href = {href},
            mime_type = {mimeType},
            lang = {lang},
            title = {title},
            length = {length},
            rel = {rel} where uuid = {uuid}
        """).on(
        'href -> owcLink.href.toString,
        'mimeType -> owcLink.mimeType,
        'lang -> owcLink.lang,
        'title -> owcLink.title,
        'length -> owcLink.length,
        'rel -> owcLink.rel,
        'uuid -> owcLink.uuid.toString
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcLink)
        case _ => None
      }
    }

  }

  /**
    * Find OwcLinks by uuid
    *
    * @param uuid
    * @return
    */
  def findOwcLinksByUuid(uuid: UUID): Option[OwcLink] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcLinks where uuid = '${uuid.toString}'""").as(owcLinkParser.singleOpt)
    }
  }

  /**
    * Create an OwcLink.
    *
    * @param owcLink
    * @return
    */
  def createOwcLink(owcLink: OwcLink): Option[OwcLink] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        s"""
          insert into $tableOwcLinks values (
            {uuid}, {href}, {mimeType}, {lang}, {title}, {length}, {rel}
          )
        """).on(
        'uuid -> owcLink.uuid.toString,
        'href -> owcLink.href.toString,
        'mimeType -> owcLink.mimeType,
        'lang -> owcLink.lang,
        'title -> owcLink.title,
        'length -> owcLink.length,
        'rel -> owcLink.rel
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcLink)
        case _ => None
      }
    }
  }

  /**
    * delete an OwcLink by uuid
    *
    * @param owcLink
    * @return
    */
  def deleteOwcLink(owcLink: OwcLink): Boolean = {
    val rowCount = db.withTransaction { implicit connection =>

      SQL(s"delete from $tableOwcLinks where uuid = {uuid}").on(
        'uuid -> owcLink.uuid.toString
      ).executeUpdate()
    }

    rowCount match {
      case 1 => true
      case _ => false
    }
  }

  /** ************
    * OwcContent
    * ************/

  /**
    * Parse a OwcContent from a ResultSet
    *
    */
  val owcContentParser = {
    str("uuid") ~
      str("mime_type") ~
      get[Option[String]]("url") ~
      get[Option[String]]("title") ~
      get[Option[String]]("content") map {
      case uuid ~ mimeType ~ url ~ title ~ content =>
        OwcContent(mimeType = mimeType, url = url.map(new URL(_)), title = title, content = content, uuid = UUID.fromString(uuid))
    }
  }

  /**
    * Retrieve all OwcContent.
    *
    * @return
    */
  def getAllOwcContents: Seq[OwcContent] = {
    db.withConnection { implicit connection =>
      SQL(s"select * from $tableOwcContents").as(owcContentParser *)
    }
  }

  /**
    * Update single OwcContent
    *
    * @param owcContent
    * @return
    */
  def updateOwcContent(owcContent: OwcContent): Option[OwcContent] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        s"""
          update $tableOwcContents set
            mime_type = {mimeType},
            url = {url},
            title = {title},
            content = {content} where uuid = {uuid}
        """).on(
        'mimeType -> owcContent.mimeType,
        'url -> owcContent.url.map(_.toString),
        'title -> owcContent.title,
        'content -> owcContent.content,
        'uuid -> owcContent.uuid.toString
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcContent)
        case _ => None
      }
    }

  }

  /**
    * Find OwcContents by uuid
    *
    * @param uuid
    * @return
    */
  def findOwcContentsByUuid(uuid: UUID): Option[OwcContent] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcContents where uuid = '${uuid.toString}'""").as(owcContentParser.singleOpt)
    }
  }

  /**
    * finds owc stylesets via the properties links relation
    *
    * @param propertiesUuid
    * @return
    */
  def findOwcContentsByPropertiesUUID(propertiesUuid: Option[String]): Seq[OwcContent] = {
    val values = propertiesUuid.map(_.split(":").toSeq).map(
      potUuids => {
        val uuids = potUuids.map(_.toUuidOption).filter(_.isDefined).map(_.get)
        uuids.map(findOwcContentsByUuid(_)).filter(_.isDefined).map(_.get)
      }
    )
    values.getOrElse(Seq())
  }

  /**
    * Create an OwcContent.
    *
    * @param owcContent
    * @return
    */
  def createOwcContent(owcContent: OwcContent): Option[OwcContent] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        s"""
          insert into $tableOwcContents values (
            {uuid}, {mimeType}, {url}, {title}, {content}
          )
        """).on(
        'uuid -> owcContent.uuid.toString,
        'mimeType -> owcContent.mimeType,
        'url -> owcContent.url.map(_.toString),
        'title -> owcContent.title,
        'content -> owcContent.content
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcContent)
        case _ => None
      }
    }
  }

  /**
    * delete an OwcContent by uuid
    *
    * @param owcContent
    * @return
    */
  def deleteOwcContent(owcContent: OwcContent): Boolean = {
    val rowCount = db.withTransaction { implicit connection =>

      SQL(s"delete from $tableOwcContents where uuid = {uuid}").on(
        'uuid -> owcContent.uuid.toString
      ).executeUpdate()
    }

    rowCount match {
      case 1 => true
      case _ => false
    }
  }


  /** ************
    * OwcStyleSet
    * ************/

  /**
    * Parse a OwcStyleSet from a ResultSet
    *
    */
  val owcStyleSetParser = {
    str("name") ~
      str("title") ~
      get[Option[String]]("abstrakt") ~
      get[Option[Boolean]]("is_default") ~
      get[Option[String]]("legend_url") ~
      get[Option[String]]("content_uuid") ~
      str("uuid") map {
      case name ~ title ~ abstrakt ~ isDefault ~ legendUrl ~ content_uuid ~ uuid =>
        OwcStyleSet(name = name,
          legendUrl = legendUrl.map(new URL(_)),
          title = title,
          abstrakt = abstrakt,
          default = isDefault,
          content = content_uuid.map(u => u.toUuidOption.map(findOwcContentsByUuid(_)).getOrElse(None)).getOrElse(None),
          uuid = UUID.fromString(uuid))
    }
  }

  /**
    * Retrieve all OwcStyleSet.
    *
    * @return
    */
  def getAllOwcStyleSets: Seq[OwcStyleSet] = {
    db.withConnection { implicit connection =>
      SQL(s"select * from $tableOwcStyleSets").as(owcStyleSetParser *)
    }
  }

  /**
    * finds owc stylesets via the properties links relation
    *
    * @param propertiesUuid
    * @return
    */
  def findOwcStyleSetsByPropertiesUUID(propertiesUuid: Option[String]): Seq[OwcStyleSet] = {
    val values = propertiesUuid.map(_.split(":").toSeq).map(
      potUuids => {
        val uuids = potUuids.map(_.toUuidOption).filter(_.isDefined).map(_.get)
        uuids.map(findOwcStyleSetsByUuid(_)).filter(_.isDefined).map(_.get)
      }
    )
    values.getOrElse(Seq())
  }


  /**
    * Update single OwcStyleSet
    *
    * @param owcStyleSet
    * @return
    */
  def updateOwcStyleSet(owcStyleSet: OwcStyleSet): Option[OwcStyleSet] = {

    db.withConnection { implicit connection =>

      val pre: Boolean = if (owcStyleSet.content.isDefined) {
        val exists = owcStyleSet.content.map(c => findOwcContentsByUuid(c.uuid)).isDefined
        if (exists) {
          val update = owcStyleSet.content.map(updateOwcContent(_))
          update.isDefined
        } else {
          val insert = owcStyleSet.content.map(createOwcContent(_))
          insert.isDefined
        }
      } else {
        true
      }

      if (pre) {
        val rowCount1 = SQL(
          s"""
          update $tableOwcStyleSets set
            name = {name},
            title = {title},
            abstrakt = {abstrakt},
            is_default = {isDefault},
            legend_url = {legendUrl},
            content = {content} where uuid = {uuid}
        """).on(
          'name -> owcStyleSet.name,
          'title -> owcStyleSet.title,
          'abstrakt -> owcStyleSet.abstrakt,
          'isDefault -> owcStyleSet.default,
          'legendUrl -> owcStyleSet.legendUrl.map(_.toString),
          'content -> owcStyleSet.content.map(_.uuid.toString),
          'uuid -> owcStyleSet.uuid.toString
        ).executeUpdate()

        rowCount1 match {
          case 1 => Some(owcStyleSet)
          case _ => None
        }
      } else {
        logger.error(s"Precondition failed, won't update OwcStyleSet")
      }
        None
      }

    }

    /**
      * Find OwcStyleSets by uuid
      *
      * @param uuid
      * @return
      */
    def findOwcStyleSetsByUuid(uuid: UUID): Option[OwcStyleSet] = {
      db.withConnection { implicit connection =>
        SQL(s"""select * from $tableOwcStyleSets where uuid = '${uuid.toString}'""").as(owcStyleSetParser.singleOpt)
      }
    }

    /**
      * Create an OwcStyleSet.
      *
      * @param owcStyleSet
      * @return
      */
    def createOwcStyleSet(owcStyleSet: OwcStyleSet): Option[OwcStyleSet] = {

      db.withConnection { implicit connection =>

        val pre: Boolean = if (owcStyleSet.content.isDefined) {
          val exists = owcStyleSet.content.map(c => findOwcContentsByUuid(c.uuid)).isDefined
          if (exists) {
            logger.error(s"OwcContent with UUID: ${owcStyleSet.content.get.uuid} exists already, won't create OwcStyleSet")
            false
          } else {
            val insert = owcStyleSet.content.map(createOwcContent(_))
            insert.isDefined
          }
        } else {
          true
        }

        if (pre) {

          val rowCount = SQL(
            s"""
          insert into $tableOwcStyleSets values (
            {uuid}, {name}, {title}, {abstrakt}, {isDefault}, {legendUrl}, {content}
          )
        """).on(
            'uuid -> owcStyleSet.uuid.toString,
            'name -> owcStyleSet.name,
            'title -> owcStyleSet.title,
            'abstrakt -> owcStyleSet.abstrakt,
            'isDefault -> owcStyleSet.default,
            'legendUrl -> owcStyleSet.legendUrl.map(_.toString),
            'content -> owcStyleSet.content.map(_.uuid.toString)
          ).executeUpdate()

          rowCount match {
            case 1 => Some(owcStyleSet)
            case _ => {
              logger.error(s"OwcStyleSet couldn't be created")
              None
            }
          }

        } else {
          None
        }
      }
    }

    /**
      * delete an OwcStyleSet by uuid
      *
      * @param owcStyleSet
      * @return
      */
    def deleteOwcStyleSet(owcStyleSet: OwcStyleSet): Boolean = {
      val rowCount = db.withTransaction { implicit connection =>

        val pre: Boolean = if (owcStyleSet.content.isDefined) {
          val exists = owcStyleSet.content.map(c => findOwcContentsByUuid(c.uuid)).isDefined
          if (exists) {
            val delete = owcStyleSet.content.exists(deleteOwcContent(_))
            delete
          } else {
            true
          }
        } else {
          true
        }

        SQL(s"delete from $tableOwcStyleSets where uuid = {uuid}").on(
          'uuid -> owcStyleSet.uuid.toString
        ).executeUpdate()
      }

      rowCount match {
        case 1 => true
        case _ => false
      }
    }

    //  /** ************
    //    * OwcProperties
    //    * ************/
    //  /**
    //    * get all the owc properties objects
    //    *
    //    * @return
    //    */
    //  def getAllOwcProperties: Seq[OwcProperties] = {
    //    db.withConnection { implicit connection =>
    //      SQL(s"""select * from $tableOwcProperties""").as(owcPropertiesParser *)
    //    }
    //  }
    //
    //  /**
    //    * find a distinct owc properties by its uuid
    //    *
    //    * @param uuid
    //    * @return
    //    */
    //  def findOwcPropertiesByUuid(uuid: UUID): Option[OwcProperties] = {
    //    db.withConnection { implicit connection =>
    //      SQL(s"""select * from $tableOwcProperties where uuid = '${uuid.toString}'""").as(owcPropertiesParser.singleOpt)
    //    }
    //  }
    //
    //  /**
    //    * find owc properties by title, title has unique constraint, that's why wild card search is intended
    //    *
    //    * @param title
    //    * @return
    //    */
    //  def findOwcPropertiesByTitle(title: String): Seq[OwcProperties] = {
    //    db.withConnection { implicit connection =>
    //      SQL(s"""select * from $tableOwcProperties where title like '%${title}%'""").as(owcPropertiesParser *)
    //    }
    //  }
    //
    //  /**
    //    * get properties for a featuretype (OwcEntry or OwcDocument)
    //    *
    //    * @param featureTypeId
    //    * @return
    //    */
    //  def findOwcPropertiesForOwcFeatureType(featureTypeId: String): Option[OwcProperties] = {
    //    db.withConnection { implicit connection =>
    //      SQL(
    //        s"""SELECT
    //           |p.uuid as uuid, p.language as language, p.title as title, p.subtitle as subtitle, p.updated as updated,
    //           |p.generator as generator, p.rights as rights, p.creator as creator, p.publisher as publisher
    //           |FROM $tableOwcProperties p JOIN $tableOwcFeatureTypesHasOwcProperties ftp ON p.uuid=ftp.owc_properties_uuid
    //           |WHERE ftp.owc_feature_types_id={owc_feature_types_id}""".stripMargin).on(
    //        'owc_feature_types_id -> featureTypeId
    //      )
    //        .as(owcPropertiesParser.singleOpt)
    //    }
    //  }
    //
    //  /**
    //    * create owc properties in the database, title has additionally unique constraint, uuid will be unique and primary key
    //    *
    //    * @param owcProperties
    //    * @return
    //    */
    //  def createOwcProperties(owcProperties: OwcProperties): Option[OwcProperties] = {
    //
    //    db.withTransaction {
    //      implicit connection => {
    //
    //        val rowCount = SQL(
    //          s"""
    //          insert into $tableOwcProperties values (
    //            {uuid}, {language}, {title}, {subtitle}, {updated}, {generator}, {rights}, {creator}, {publisher}
    //          )
    //        """).on(
    //          'uuid -> owcProperties.uuid.toString,
    //          'language -> owcProperties.language,
    //          'title -> owcProperties.title,
    //          'subtitle -> owcProperties.subtitle,
    //          'updated -> owcProperties.updated,
    //          'generator -> owcProperties.generator,
    //          'rights -> owcProperties.rights,
    //          'creator -> owcProperties.creator,
    //          'publisher -> owcProperties.publisher
    //        ).executeUpdate()
    //
    //        owcProperties.authors.foreach {
    //          author => {
    //            if (findOwcAuthorByUuid(author.uuid).isEmpty) {
    //              createOwcAuthor(author)
    //            }
    //
    //            SQL(
    //              s"""insert into $tableOwcPropertiesHasOwcAuthors values (
    //                 |{owc_properties_uuid}, {owc_authors_uuid}
    //                 |)
    //               """.stripMargin).on(
    //              'owc_properties_uuid -> owcProperties.uuid.toString,
    //              'owc_authors_uuid -> author.uuid.toString
    //            ).executeUpdate()
    //          }
    //        }
    //
    //        owcProperties.contributors.foreach {
    //          authorAsContributor => {
    //            if (findOwcAuthorByUuid(authorAsContributor.uuid).isEmpty) {
    //              createOwcAuthor(authorAsContributor)
    //            }
    //
    //            SQL(
    //              s"""insert into $tableOwcPropertiesHasOwcAuthorsAsContributors values (
    //                 |{owc_properties_uuid}, {owc_authors_as_contributors_uuid}
    //                 |)
    //               """.stripMargin).on(
    //              'owc_properties_uuid -> owcProperties.uuid.toString,
    //              'owc_authors_as_contributors_uuid -> authorAsContributor.uuid.toString
    //            ).executeUpdate()
    //          }
    //        }
    //
    //        owcProperties.categories.foreach {
    //          category => {
    //            if (findOwcCategoriesByUuid(category.uuid).isEmpty) {
    //              createOwcCategory(category)
    //            }
    //
    //            SQL(
    //              s"""insert into $tableOwcPropertiesHasOwcCategories  values (
    //                 |{owc_properties_uuid}, {owc_categories_uuid}
    //                 |)
    //               """.stripMargin).on(
    //              'owc_properties_uuid -> owcProperties.uuid.toString,
    //              'owc_categories_uuid -> category.uuid.toString
    //            ).executeUpdate()
    //          }
    //        }
    //
    //        owcProperties.links.foreach {
    //          owcLink => {
    //            if (findOwcLinksByUuid(owcLink.uuid).isEmpty) {
    //              createOwcLink(owcLink)
    //            }
    //
    //            SQL(
    //              s"""insert into $tableOwcPropertiesHasOwcLinks  values (
    //                 |{owc_properties_uuid}, {owc_links_uuid}
    //                 |)
    //               """.stripMargin).on(
    //              'owc_properties_uuid -> owcProperties.uuid.toString,
    //              'owc_links_uuid -> owcLink.uuid.toString
    //            ).executeUpdate()
    //          }
    //        }
    //
    //        rowCount match {
    //          case 1 => Some(owcProperties)
    //          case _ => None
    //        }
    //      }
    //    }
    //  }
    //
    //
    //
    //
    //
    //
    //  /**
    //    * Not yet implemented, update OwcProperties object and hierarchical dependents
    //    *
    //    * @param owcProperties
    //    * @return
    //    */
    //  def updateOwcProperties(owcProperties: OwcProperties): Option[OwcProperties] = ???
    //
    //  /**
    //    * delete an OwcProperties object and its dependents
    //    *
    //    * @param owcProperties
    //    * @return
    //    */
    //  def deleteOwcProperties(owcProperties: OwcProperties): Boolean = {
    //    val rowCount = db.withTransaction {
    //      implicit connection => {
    //
    //        SQL(s"""delete from $tableOwcPropertiesHasOwcAuthors where owc_properties_uuid = {uuid}""").on(
    //          'uuid -> owcProperties.uuid.toString
    //        ).executeUpdate()
    //
    //        SQL(s"""delete from $tableOwcPropertiesHasOwcAuthorsAsContributors where owc_properties_uuid = {uuid}""").on(
    //          'uuid -> owcProperties.uuid.toString
    //        ).executeUpdate()
    //
    //        SQL(s"""delete from $tableOwcPropertiesHasOwcCategories where owc_properties_uuid = {uuid}""").on(
    //          'uuid -> owcProperties.uuid.toString
    //        ).executeUpdate()
    //
    //        SQL(s"""delete from $tableOwcPropertiesHasOwcLinks where owc_properties_uuid = {uuid}""").on(
    //          'uuid -> owcProperties.uuid.toString
    //        ).executeUpdate()
    //
    //        SQL(s"delete from $tableOwcProperties where uuid = {uuid}").on(
    //          'uuid -> owcProperties.uuid.toString
    //        ).executeUpdate()
    //      }
    //    }
    //
    //    db.withConnection(
    //      implicit connection => {
    //        (owcProperties.authors ++ owcProperties.contributors).distinct.filter {
    //          author => {
    //            val authorsEmpty = SQL(
    //              s"""select owc_authors_uuid from $tableOwcPropertiesHasOwcAuthors where owc_authors_uuid = {uuid}""").on(
    //              'uuid -> author.uuid.toString
    //            ).as(SqlParser.str("owc_authors_uuid") *).isEmpty
    //
    //            val authorsAsContributorsEmpty = SQL(
    //              s"""select owc_authors_as_contributors_uuid from $tableOwcPropertiesHasOwcAuthorsAsContributors
    //                 |where owc_authors_as_contributors_uuid = {uuid}""".stripMargin).on(
    //              'uuid -> author.uuid.toString
    //            ).as(SqlParser.str("owc_authors_as_contributors_uuid") *).isEmpty
    //
    //            authorsEmpty && authorsAsContributorsEmpty
    //          }
    //        }.foreach(deleteOwcAuthor(_))
    //
    //        owcProperties.categories.filter {
    //          category => {
    //            SQL(
    //              s"""select owc_categories_uuid from $tableOwcPropertiesHasOwcCategories where owc_categories_uuid = {uuid}""").on(
    //              'uuid -> category.uuid.toString
    //            ).as(SqlParser.str("owc_categories_uuid") *).isEmpty
    //          }
    //        }.foreach(deleteOwcCategory(_))
    //
    //        owcProperties.links.filter {
    //          owcLink => {
    //            SQL(s"""select owc_links_uuid from $tableOwcPropertiesHasOwcLinks where owc_links_uuid = {uuid}""").on(
    //              'uuid -> owcLink.uuid.toString
    //            ).as(SqlParser.str("owc_links_uuid") *).isEmpty
    //          }
    //        }.foreach(deleteOwcLink(_))
    //      }
    //    )
    //
    //    rowCount match {
    //      case 1 => true
    //      case _ => false
    //    }
    //  }


  }
