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

package models

import java.time.ZonedDateTime

import anorm.JavaTimeColumn
import anorm.JavaTimeToStatement
import anorm.JavaTimeParameterMetaData
import javax.inject.{Inject, Singleton}

import anorm.SqlParser._
import anorm._
import play.api.db._
import utils.ClassnameLogger

class UserDAO  @Inject()(db: Database) extends ClassnameLogger {

  def getUsers : List[User] = {

    // access "default" database
    db.withConnection { implicit connection =>
      // do whatever you need with the connection
    }

    List()

  }

  // -- Parsers

  /**
    * Parse a User from a ResultSet
    *
    */
  val userParser = {
    get[String]("email") ~
      get[String]("username") ~
      get[String]("firstname") ~
      get[String]("lastname") ~
      get[String]("password") ~
      get[String]("laststatustoken") ~
      get[ZonedDateTime]("laststatuschange") map {
      case email~username~firstname~lastname~password~laststatustoken~laststatuschange =>
        User(email, username, firstname, lastname, password, laststatustoken, laststatuschange)
    }
  }

  val macroUserParser: RowParser[User] = Macro.namedParser[User]

  // -- Queries

  /**
    * Retrieve a User from username.
    */
  def findByUsername(username: String): Option[User] = {
    db.withConnection { implicit connection =>
      SQL("select * from users where username = {username}").on(
        'username -> username
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
    */
  def authenticate(username: String, password: String): Option[User] = {
    db.withConnection { implicit connection =>
      SQL(
        """
         select * from users where
         username = {username} and password = {password}
        """
      ).on(
        'username -> username,
        'password -> password
      ).as(userParser.singleOpt)
    }
  }

  def demo = {
    db.withConnection { implicit connection =>
      val nps = Seq[NamedParameter](// Tuples as NamedParameter before Any
        "a" -> "1",
        "b" -> "2",
        "c" -> 3)

      SQL("SELECT * FROM test WHERE (a={a} AND b={b}) OR c={c}").
        on(nps: _*) // Fail - no conversion (String,Any) => NamedParameter}
    }
  }

  def createNps(user: User): User = {

    db.withConnection { implicit connection =>
      val nps = Seq[NamedParameter](// Tuples as NamedParameter before Any
        "email" -> user.email,
        "username" -> user.username,
        "firstname" -> user.firstname,
        "lastname" -> user.lastname,
        "laststatustoken" -> user.laststatustoken,
        "password" -> user.password,
        "laststatuschange" -> user.laststatuschange)

      SQL(
        """
          insert into users values (
            {email}, {username}, {firstname}, {lastname}, {password}, {laststatustoken}, {laststatuschange}
          )
        """).on(nps: _*).executeUpdate()

      user

    }
  }

  /**
    * Create a User.
    */
  def create(user: User): User = {

    db.withConnection { implicit connection =>
      SQL(
        """
          insert into users values (
            {email}, {username}, {firstname}, {lastname}, {password}, {laststatustoken}, {laststatuschange}
          )
        """).on(
        'email -> user.email,
        'username -> user.username,
        'firstname -> user.firstname,
        'lastname -> user.lastname,
        'password -> user.password,
        'laststatustoken -> user.laststatustoken,
        'laststatuschange -> user.laststatuschange
      ).executeUpdate()

      user
    }
  }

  /*
  Update single parts of user
   */

}
