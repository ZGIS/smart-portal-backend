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

package models.users

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}

import anorm.SqlParser._
import anorm._
import play.api.db._
import utils.{ClassnameLogger, PasswordHashing}

/**
  * UserDAO
  *
  * @param db
  * @param passwordHashing
  */
@Singleton
class UserDAO  @Inject()(db: Database, passwordHashing: PasswordHashing) extends ClassnameLogger {

  /**
    * Parse a User from a ResultSet
    *
    */
  val userParser = {
    get[String]("email") ~
      get[String]("accountSubject") ~
      get[String]("firstname") ~
      get[String]("lastname") ~
      get[String]("password") ~
      get[String]("laststatustoken") ~
      get[ZonedDateTime]("laststatuschange") map {
      case email~accountsubject~firstname~lastname~password~laststatustoken~laststatuschange =>
        User(email, accountsubject, firstname, lastname, password, laststatustoken, laststatuschange)
    }
  }

  /**
    * Retrieve a User from email.
    *
    * @param accountSubject
    */
  def findByAccountSubject(accountSubject: String): Option[User] = {
    db.withConnection { implicit connection =>
      SQL("select * from users where accountsubject = {accountsubject}").on(
        'accountsubject -> accountSubject
      ).as(userParser.singleOpt)
    }
  }

  /**
    * Retrieve all users.
    */
  def findAll: Seq[User] = {
    db.withConnection { implicit connection =>
      SQL("select * from users").as(userParser *)
    }
  }

  /**
    * Authenticate a User.
    *
    * @param email
    * @param password
    */
  def authenticate(email: String, password: String): Option[User] = {

    db.withConnection { implicit connection =>
      SQL(
        """
         select * from users where
         email = {email}
        """
      ).on(
        'email -> email
      ).as(userParser.singleOpt)
    }.filter { user =>
      passwordHashing.validatePassword(password, user.password)
    }

  }

  /**
    * Create a User with a NamedParameterSet (supposedly more typesafe?)
    *
    * new java.time. API only partially implemented in Anorm Type mapping
    *
    * @param user
    */
  def createWithNps(user: User): Option[User] = {

    db.withConnection { implicit connection =>
      val nps = Seq[NamedParameter](// Tuples as NamedParameter before Any
        "email" -> user.email,
        "accountsubject" -> user.accountSubject,
        "firstname" -> user.firstname,
        "lastname" -> user.lastname,
        "laststatustoken" -> user.laststatustoken,
        "password" -> user.password,
        "laststatuschange" -> user.laststatuschange)

      val rowCount = SQL(
        """
          insert into users values (
            {email}, {accountsubject}, {firstname}, {lastname}, {password}, {laststatustoken}, {laststatuschange}
          )
        """).on(nps: _*).executeUpdate()

      rowCount match {
        case 1 => Some(user)
        case _ => None
      }


    }
  }

  /**
    * Create a User.
    *
    * @param user
    * @return
    */
  def create(user: User): Option[User] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        """
          insert into users values (
            {email}, {accountsubject}, {firstname}, {lastname}, {password}, {laststatustoken}, {laststatuschange}
          )
        """).on(
        'email -> user.email,
        'accountsubject -> user.accountSubject,
        'firstname -> user.firstname,
        'lastname -> user.lastname,
        'password -> user.password,
        'laststatustoken -> user.laststatustoken,
        'laststatuschange -> user.laststatuschange
      ).executeUpdate()

      rowCount match {
        case 1 => Some(user)
        case _ => None
      }
    }
  }

  /**
    * Update single parts of user without touch password
    *
    * @param user
    * @return
    */
  def updateNoPass(user: User) : Option[User] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        """
          update users set
            accountsubject = {accountsubject},
            firstname = {firstname},
            lastname = {lastname},
            laststatustoken = {laststatustoken},
            laststatuschange = {laststatuschange} where email = {email}
        """).on(
        'accountsubject -> user.accountSubject,
        'firstname -> user.firstname,
        'lastname -> user.lastname,
        'laststatustoken -> user.laststatustoken,
        'laststatuschange -> user.laststatuschange,
        'email -> user.email
      ).executeUpdate()

      rowCount match {
        case 1 => Some(user)
        case _ => None
      }
    }

  }

  /**
    * Update password parts of user without other parts
    * @param user
    * @return
    */
  def updatePassword(user: User) : Option[User] = {

    db.withConnection { implicit connection =>
      val rowCount = SQL(
        """
          update users set
            password = {password},
            laststatustoken = {laststatustoken},
            laststatuschange = {laststatuschange} where email = {email}
        """).on(
        'password -> user.password,
        'laststatustoken -> user.laststatustoken,
        'laststatuschange -> user.laststatuschange,
        'email -> user.email
      ).executeUpdate()

      rowCount match {
        case 1 => Some(user)
        case _ => None
      }
    }

  }

  // more utility functions

  /**
    * find User By Email
    *
    * @param email
    * @return
    */
  def findUserByEmail(email: String) : Option[User] = {
    db.withConnection { implicit connection =>
      SQL("select * from users where email = {email}").on(
        'email -> email
      ).as(userParser.singleOpt)
    }
  }

  /**
    * delete a User
    *
    * @param email
    * @return
    */
  def deleteUser(email: String) : Boolean = {
    val rowCount = db.withConnection { implicit connection =>
      SQL("delete from users where email = {email}").on(
        'email -> email
      ).executeUpdate()
    }

    rowCount match {
      case 1 => true
      case _ => false
    }
  }

  /**
    * find Users By their status token
    *
    * @param token
    * @return
    */
  def findUsersByToken(token: String) : Seq[User] = {
    db.withConnection { implicit connection =>
      SQL(s"""select * from users where laststatustoken like '${token}'""").as(userParser *)
    }
  }

  /**
    * find Users By their status token "REGISTERED" and their uniqu registration link id
    *
    * @param regLink
    * @return
    */
  def findRegisteredUsersByRegLink(regLink: String) : Seq[User] = {
    findUsersByToken(s"REGISTERED:$regLink")
  }

  /**
    * find Users By their status token "REGISTERED"
    *
    * @return
    */
  def findRegisteredOnlyUsers : Seq[User] = {
    findUsersByToken("REGISTERED%")
  }

  /**
    * find Users By their status token "ACTIVE"
    *
    * @return
    */
  def findActiveUsers : Seq[User] = {
    findUsersByToken("ACTIVE%")
  }

}
