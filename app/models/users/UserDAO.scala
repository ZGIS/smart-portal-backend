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

import java.sql.Connection
import java.time.ZonedDateTime
import java.util.UUID

import anorm.SqlParser._
import anorm._
import uk.gov.hmrc.emailaddress.EmailAddress
import utils.ClassnameLogger

/**
  * UserDAO requires implicit connections from calling entity
  */
object UserDAO extends ClassnameLogger {

  /**
    * Parse a User from a ResultSet
    *
    */
  val userParser = {
    get[String]("email") ~
      get[String]("accountsubject") ~
      get[String]("firstname") ~
      get[String]("lastname") ~
      get[String]("password") ~
      get[String]("laststatustoken") ~
      get[ZonedDateTime]("laststatuschange") map {
      case email ~ accountsubject ~ firstname ~ lastname ~ password ~ laststatustoken ~ laststatuschange =>
        User(EmailAddress(email), accountsubject, firstname, lastname, password, laststatustoken, laststatuschange)
    }
  }

  /**
    * Create a User with a NamedParameterSet (supposedly more typesafe?)
    *
    * new java.time. API only partially implemented in Anorm Type mapping
    *
    * @param user
    */
  def createUser(user: User)(implicit connection: Connection): Option[User] = {
    val nps = Seq[NamedParameter](// Tuples as NamedParameter
      "email" -> user.email.value,
      "accountsubject" -> user.accountSubject,
      "firstname" -> user.firstname,
      "lastname" -> user.lastname,
      "laststatustoken" -> user.laststatustoken,
      "password" -> user.password,
      "laststatuschange" -> user.laststatuschange)

    val rowCount = SQL(
      s"""
          insert into $table_users values (
            {email}, {accountsubject}, {firstname}, {lastname}, {password}, {laststatustoken}, {laststatuschange}
          )
        """).on(nps: _*).executeUpdate()

    rowCount match {
      case 1 => Some(user)
      case _ => None
    }
  }

  /**
    * Update single parts of user without touch password
    *
    * @param user
    * @return
    */
  def updateNoPass(user: User)(implicit connection: Connection): Option[User] = {
    val rowCount = SQL(
      s"""
          update $table_users set
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
      'email -> user.email.value
    ).executeUpdate()

    rowCount match {
      case 1 => Some(user)
      case _ => None
    }
  }

  /**
    * Update password parts of user without other parts
    *
    * @param user
    * @return
    */
  def updatePassword(user: User)(implicit connection: Connection): Option[User] = {
    val rowCount = SQL(
      s"""
          update $table_users set
            password = {password},
            laststatustoken = {laststatustoken},
            laststatuschange = {laststatuschange} where email = {email}
        """).on(
      'password -> user.password,
      'laststatustoken -> user.laststatustoken,
      'laststatuschange -> user.laststatuschange,
      'email -> user.email.value
    ).executeUpdate()

    rowCount match {
      case 1 => Some(user)
      case _ => None
    }
  }

  /**
    * delete a User
    *
    * @param email
    * @return
    */
  def deleteUser(email: String)(implicit connection: Connection): Boolean = {
    val u = findUserByEmailAsString(email)
    if (u.isDefined) {
      deleteUser(u.get)
    } else {
      logger.error(s"user with email: $email wasn't found")
      false
    }
  }

  /**
    * delete a User
    *
    * @param user
    * @return
    */
  def deleteUser(user: User)(implicit connection: Connection): Boolean = {
    val rowCount = SQL(s"delete from $table_users where accountsubject = {accountsubject}").on(
        'accountsubject -> user.accountSubject
      ).executeUpdate()

    rowCount match {
      case 1 => true
      case _ => false
    }
  }

  /**
    * find User By Email
    *
    * @param emailString
    * @return
    */
  def findUserByEmailAsString(emailString: String)(implicit connection: Connection): Option[User] = {
    if (EmailAddress.isValid(emailString)) {
      findUserByEmailAddress(EmailAddress(emailString))
    } else {
      logger.error("not a valid email address")
      None
    }
  }

  /**
    * find User By Email
    *
    * @param emailAddress
    * @return
    */
  def findUserByEmailAddress(emailAddress: EmailAddress)(implicit connection: Connection): Option[User] = {
      SQL(s"select * from $table_users where email = {email}").on(
        'email -> emailAddress.value
      ).as(userParser.singleOpt)
  }

  /**
    * Retrieve a User via accountSubject
    *
    * @param accountSubject
    */
  def findByAccountSubject(accountSubject: String)(implicit connection: Connection): Option[User] = {
      SQL(s"select * from $table_users where accountsubject = {accountsubject}").on(
        'accountsubject -> accountSubject
      ).as(userParser.singleOpt)
  }

  /**
    * Retrieve all
    */
  def getAllUsers(implicit connection: Connection): Seq[User] = {
      SQL(s"select * from $table_users").as(userParser *)
  }

  // more utility functions

  /**
    * find Users By their status token
    *
    * @param token
    * @return
    */
  def findUsersByToken(token: StatusToken, statusInfo: String)(implicit connection: Connection): Seq[User] = {
      SQL(s"""select * from $table_users where laststatustoken like '$token$statusInfo'""").as(userParser *)
  }

  /**
    * find Users By their status token "REGISTERED" and their unique registration confirmation link id
    *
    * @param regLink
    * @return
    */
  def findRegisteredUsersWithRegLink(regLink: String)(implicit connection: Connection): Seq[User] = {
    findUsersByToken(StatusToken.REGISTERED, s":$regLink")
  }

  /**
    * find Users By their status token "EMAILVALIDATION" and their unique registration confirmation link id
    *
    * @param regLink
    * @return
    */
  def findEmailValidationRequiredUsersWithRegLink(regLink: String)(implicit connection: Connection): Seq[User] = {
    findUsersByToken(StatusToken.EMAILVALIDATION, s":$regLink")
  }

  /**
    * find Users By their status token "PASSWORDRESET" and their uniqu reset link id
    *
    * @param resetLink
    * @return
    */
  def findUsersByPassResetLink(resetLink: String)(implicit connection: Connection): Seq[User] = {
    findUsersByToken(StatusToken.PASSWORDRESET, s":$resetLink")
  }

  /**
    * find Users By their status token that are only registered but have not yet activated their accounts
    *
    * @return
    */
  def findRegisteredOnlyUsers(implicit connection: Connection): Seq[User] = {
    findUsersByToken(StatusToken.REGISTERED, "%")
  }

  /**
    * find active Users By their status token
    *
    * @return
    */
  def findActiveUsers(implicit connection: Connection): Seq[User] = {
    StatusToken.activatedTokens.flatMap(t => findUsersByToken(StatusToken(t), "%"))
  }

}
