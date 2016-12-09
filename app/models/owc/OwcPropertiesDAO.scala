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

import javax.inject.{Inject, Singleton}

import anorm.SqlParser._
import anorm._
import models.owc.OwcLink
import play.api.db._
import utils.ClassnameLogger

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

  /***********
   * OwcAuthor
   ***********/

  /**
    * Parse a OwcAuthor from a ResultSet
    */
  val owcAuthorParser = {
    str("name") ~
      get[Option[String]]("email") ~
      get[Option[String]]("uri") map {
      case name ~ email ~ uri =>
        OwcAuthor(name, email, uri)
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
  def findOwcAuthorByName(name: String): Seq[OwcAuthor] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from $tableOwcAuthors where name like '${name}'""").as(owcAuthorParser *)
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
            {name}, {email}, {uri}
          )
        """).on(
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
            email = {email},
            uri = {uri} where name = {name}
        """).on(
        'email -> owcAuthor.email,
        'uri -> owcAuthor.uri,
        'name -> owcAuthor.name
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
    * @param name
    * @return
    */
  def deleteOwcAuthor(name: String): Boolean = {
    val rowCount = db.withConnection { implicit connection =>
      SQL(s"delete from $tableOwcAuthors where name = {name}").on(
        'name -> name
      ).executeUpdate()
    }

    rowCount match {
      case 1 => true
      case _ => false
    }
  }

  /**************
    * OwcCategory
    *************/

  /**
    * Parse a OwcCategory from a ResultSet
    */
  val owcCategoryParser = {
    str("scheme") ~
      str("term") ~
      get[Option[String]]("label") map {
      case scheme ~ term ~ label =>
        OwcCategory(scheme, term, label)
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
            {scheme}, {term}, {label}
          )
        """).on(
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
            label = {label}
            where scheme = {scheme}
            AND term = {term}
        """).on(
        'label -> owcCategory.label,
        'scheme -> owcCategory.scheme,
        'term -> owcCategory.term
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
      SQL(s"delete from $tableOwcCategories where term = {term} AND scheme = {scheme}").on(
        'term -> owcCategory.term,
        'scheme -> owcCategory.scheme
      ).executeUpdate()
    }

    rowCount match {
      case 1 => true
      case _ => false
    }
  }

  /**************
    * OwcLink
    *************/

  /**
    * Parse a OwcLink from a ResultSet
    */
  val owcLinkParser = {
    str("rel") ~
      get[Option[String]]("mime_type") ~
      str("href") ~
      get[Option[String]]("title") map {
      case rel ~ mimeType ~ href ~ title =>
        OwcLink(rel, mimeType, href, title)
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
            {rel}, {mimeType}, {href}, {title}
          )
        """).on(
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
    * Update single OwcLink (update the label basically only)
    *
    * @param owcLink
    * @return
    */
  def updateOwcLink(owcLink: OwcLink): Option[OwcLink] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        s"""
          update $tableOwcLinks set
            mime_type = {mimeType},
            title = {title}
            where rel = {rel}
            AND href = {href}
        """).on(
        'mimeType -> owcLink.mimeType,
        'title -> owcLink.title,
        'rel -> owcLink.rel,
        'href -> owcLink.href
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
      SQL(s"delete from $tableOwcLinks where rel = {rel} AND href = {href}").on(
        'rel -> owcLink.rel,
        'href -> owcLink.href
      ).executeUpdate()
    }

    rowCount match {
      case 1 => true
      case _ => false
    }
  }

  /**************
    * OwcProperties
    *************/

  /**
    * Parse a OwcProperties from a ResultSet
    */
  val owcPropertiesParser = {
    str("scheme") ~
      str("term") ~
      get[Option[String]]("label") map {
      case scheme ~ term ~ label =>
        OwcCategory(scheme, term, label)
    }
  }

}
