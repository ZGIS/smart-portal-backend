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

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}

import anorm.SqlParser._
import anorm._
import play.api.db._
import utils.ClassnameLogger
import java.util.UUID

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
    str("uuid") ~
      str("name") ~
      get[Option[String]]("email") ~
      get[Option[String]]("uri") map {
      case uuid ~ name ~ email ~ uri =>
        OwcAuthor(UUID.fromString(uuid), name, email, uri)
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
    * @param name
    * @return
    */
  def findOwcAuthorByName(name: String): Option[OwcAuthor] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcAuthors where name like '${name}'""").as(owcAuthorParser.singleOpt)
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
      SQL(s"""select * from $tableOwcAuthors where uuid like '${uuid.toString}'""").as(owcAuthorParser.singleOpt)
    }
  }

  def findOwcAuthorsByPropertiesUUID(propertiesUuid: UUID): Seq[OwcAuthor] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""SELECT a.uuid as uuid, a.name as name, a.email as email, a.uri as uri
           |FROM $tableOwcAuthors a JOIN $tableOwcPropertiesHasOwcAuthors pa ON a.uuid=pa.owc_authors_uuid
           |WHERE pa.owc_properties_uuid={uuid}""".stripMargin).on(
        'uuid -> propertiesUuid.toString
      )
        .as(owcAuthorParser *)
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
        'email -> owcAuthor.email,
        'uri -> owcAuthor.uri
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcAuthor)
        case _ => None
      }
    }
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
        'email -> owcAuthor.email,
        'uri -> owcAuthor.uri,
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
      str("scheme") ~
      str("term") ~
      get[Option[String]]("label") map {
      case uuid ~ scheme ~ term ~ label =>
        OwcCategory(UUID.fromString(uuid), scheme, term, label)
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
      str("rel") ~
      get[Option[String]]("mime_type") ~
      str("href") ~
      get[Option[String]]("title") map {
      case uuid ~ rel ~ mimeType ~ href ~ title =>
        OwcLink(UUID.fromString(uuid), rel, mimeType, href, title)
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
    * Find OwcLinks by href
    *
    * @param href
    * @return
    */
  def findOwcLinksByHref(href: String): Seq[OwcLink] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcLinks where href like '${href}'""").as(owcLinkParser *)
    }
  }

  /**
    * Find OwcLink by term and scheme
    *
    * @param rel
    * @param href
    * @return
    */
  def findOwcLinksByRelAndHref(rel: String, href: String): Seq[OwcLink] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""select * from $tableOwcLinks
           |where rel like '${rel}'
           |AND href like '${href}'
           |""".stripMargin).as(owcLinkParser *)
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
            {uuid}, {rel}, {mimeType}, {href}, {title}
          )
        """).on(
        'uuid -> owcLink.uuid.toString,
        'rel -> owcLink.rel,
        'mimeType -> owcLink.mimeType,
        'href -> owcLink.href,
        'title -> owcLink.title
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcLink)
        case _ => None
      }
    }
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
            rel = {rel},
            mime_type = {mimeType},
            href = {href},
            title = {title} where uuid = {uuid}
        """).on(
        'rel -> owcLink.rel,
        'mimeType -> owcLink.mimeType,
        'href -> owcLink.href,
        'title -> owcLink.title,
        'uuid -> owcLink.uuid.toString
      ).executeUpdate()

      rowCount match {
        case 1 => Some(owcLink)
        case _ => None
      }
    }

  }

  /**
    * delete an OwcLink by rel and href
    *
    * @param owcLink
    * @return
    */
  def deleteOwcLink(owcLink: OwcLink): Boolean = {
    val rowCount = db.withConnection { implicit connection =>
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
    * OwcProperties
    * ************/

  /**
    * Parse a OwcProperties from a ResultSet
    */
  val owcPropertiesParser = {
    str("uuid") ~
      str("language") ~
      str("title") ~
      get[Option[String]]("subtitle") ~
      get[Option[ZonedDateTime]]("updated") ~
      get[Option[String]]("generator") ~
      get[Option[String]]("rights") ~
      get[Option[String]]("creator") ~
      get[Option[String]]("publisher") map {

      case uuid ~ language ~ title ~ subtitle ~ updated ~ generator ~ rights ~ creator ~ publisher =>
        OwcProperties(UUID.fromString(uuid), language, title, subtitle, updated, generator, rights,
          findOwcAuthorsByPropertiesUUID(UUID.fromString(uuid)).toList, List(), creator, publisher, List(), List())
    }
  }

  /**
    *
    * @return
    */
  def getAllOwcProperties: Seq[OwcProperties] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcProperties""").as(owcPropertiesParser *)
    }
  }

  /**
    *
    * @param uuid
    * @return
    */
  def findOwcPropertiesByUuid(uuid: UUID): Option[OwcProperties] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcProperties where uuid = '${uuid.toString}'""").as(owcPropertiesParser.singleOpt)
    }
  }

  /**
    *
    * @param title
    * @return
    */
  def findOwcPropertiesByTitle(title: String): Seq[OwcProperties] = ???

  /**
    *
    * @param owcProperties
    * @return
    */
  def createOwcProperties(owcProperties: OwcProperties): Option[OwcProperties] = {

    db.withTransaction {
      implicit connection => {

        val rowCount = SQL(
          s"""
          insert into $tableOwcProperties values (
            {uuid}, {language}, {title}, {subtitle}, {updated}, {generator}, {rights}, {creator}, {publisher}
          )
        """).on(
          'uuid -> owcProperties.uuid.toString,
          'language -> owcProperties.language,
          'title -> owcProperties.title,
          'subtitle -> owcProperties.subtitle,
          'updated -> owcProperties.updated,
          'generator -> owcProperties.generator,
          'rights -> owcProperties.rights,
          'creator -> owcProperties.creator,
          'publisher -> owcProperties.publisher
        ).executeUpdate()

        owcProperties.authors.foreach {
          author => {
            if (findOwcAuthorByUuid(author.uuid).isEmpty) {
              createOwcAuthor(author)
            }

            SQL(
              s"""insert into $tableOwcPropertiesHasOwcAuthors  values (
                 |{owc_properties_uuid}, {owc_authors_uuid}
                 |)
               """.stripMargin).on(
              'owc_properties_uuid -> owcProperties.uuid.toString,
              'owc_authors_uuid -> author.uuid.toString
            ).executeUpdate()
          }
        }

        rowCount match {
          case 1 => Some(owcProperties)
          case _ => None
        }
      }
    }
  }

  /**
    *
    * @param owcProperties
    * @return
    */
  def updateOwcProperties(owcProperties: OwcProperties): Option[OwcProperties] = ???

  /**
    *
    * @param owcProperties
    * @return
    */
  def deleteOwcProperties(owcProperties: OwcProperties): Boolean = {
    val rowCount = db.withTransaction {
      implicit connection => {

        SQL(s"""delete from $tableOwcPropertiesHasOwcAuthors where owc_properties_uuid = {uuid}""").on(
          'uuid -> owcProperties.uuid.toString
        ).executeUpdate()

        SQL(s"delete from $tableOwcProperties where uuid = {uuid}").on(
          'uuid -> owcProperties.uuid.toString
        ).executeUpdate()
      }
    }

    db.withConnection(
      implicit connection => owcProperties.authors.filter {
        author => {
          SQL(s"""select owc_authors_uuid from $tableOwcPropertiesHasOwcAuthors where owc_authors_uuid = {uuid}""").on(
            'uuid -> author.uuid.toString
          ).as(SqlParser.str("owc_authors_uuid") *).isEmpty
        }
      }.foreach(deleteOwcAuthor(_))
    )

    rowCount match {
      case 1 => true
      case _ => false
    }
  }

}
