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

package services

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID
import javax.inject._

import models.ErrorResult
import models.db.DatabaseSessionHolder
import models.users._
import play.api.Configuration
import play.api.cache.CacheApi
import uk.gov.hmrc.emailaddress.EmailAddress
import utils.{ClassnameLogger, PasswordHashing}

@Singleton
class UserService @Inject()(dbSession: DatabaseSessionHolder,
                            val passwordHashing: PasswordHashing,
                            cache: CacheApi,
                            configuration: Configuration) extends ClassnameLogger {

  lazy private val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")

  /**
    * basic auth compare
    *
    * @param loginCredentials
    * @return
    */
  def authenticateLocal(loginCredentials: LoginCredentials): Either[ErrorResult, User] = {

    val userOpt = dbSession.viaConnection { implicit connection =>
      UserDAO.findUserByEmailAsString(loginCredentials.email).filter(user =>
        passwordHashing.validatePassword(loginCredentials.password, user.password)
      )
    }
    if (userOpt.isDefined) {
      // here is correct auth branch
      val user = userOpt.get
      if (user.isBlocked) {
        Left(ErrorResult("Your account was temporarily disabled. Please contact the administrator.", None))
      } else {
        Right(user)
      }

    } else {
      // should never indicate if user not found or just password wrong?
      Left(ErrorResult("User email or password wrong.", None))
    }
  }

  /**
    * passing findUserByEmailAsString request through from controller
    *
    * @param email
    * @return
    */
  def findUserByEmailAsString(email: String): Option[User] = {
    dbSession.viaConnection(implicit connection => {
      UserDAO.findUserByEmailAsString(email)
    })
  }

  /**
    * passing findUserByEmailAddress request through from controller
    *
    * @param email
    * @return
    */
  def findUserByEmailAddress(email: EmailAddress): Option[User] = {
    dbSession.viaConnection(implicit connection => {
      UserDAO.findUserByEmailAddress(email)
    })
  }

  /**
    * passing findUsersByPassResetLink request through from controller
    *
    * @param linkId
    * @return
    */
  def findRegisteredUsersWithRegLink(linkId: String): Seq[User] = {
    dbSession.viaConnection(implicit connection => {
      UserDAO.findRegisteredUsersWithRegLink(linkId)
    })
  }

  /**
    * passing findUsersByPassResetLink request through from controller
    *
    * @param linkId
    * @return
    */
  def findUsersByPassResetLink(linkId: String): Seq[User] = {
    dbSession.viaConnection(implicit connection => {
      UserDAO.findUsersByPassResetLink(linkId)
    })
  }

  /**
    * passing createUser request through from controller
    *
    * @param user
    * @return
    */
  def createUser(user: User): Option[User] = {
    dbSession.viaTransaction(implicit connection => {
      UserDAO.createUser(user)
    })
  }

  /**
    * passing deleteUser(user) request through from controller
    *
    * @param user
    * @return
    */
  def deleteUser(user: User): Boolean = {
    dbSession.viaTransaction(implicit connection => {
      UserDAO.deleteUser(user)
    })
  }

  /**
    * passing updateNoPass request through from controller
    *
    * @param user
    * @return
    */
  def updateNoPass(user: User): Option[User] = {
    dbSession.viaTransaction(implicit connection => {
      UserDAO.updateNoPass(user)
    })
  }

  /**
    * passing updatePassword request through from controller
    *
    * @param user
    * @return
    */
  def updatePassword(user: User): Option[User] = {
    dbSession.viaTransaction(implicit connection => {
      UserDAO.updatePassword(user)
    })
  }

  /**
    * set the new session and put it into cache
    *
    * @param userEmail
    * @param userAgentHeader
    * @return
    */
  def upsertUserSessionCache(userEmail: String, userAgentHeader: String): String = {
    val token = passwordHashing.createSessionCookie(userEmail, userAgentHeader)

    // TODO here replace cache access with DB
    cache.set(token, userEmail)

    token
  }

  /**
    * check for an active server-side session
    *
    * @param xsrfToken
    * @param xsrfTokenCookie
    * @param userAgentHeader
    * @return
    */
  def checkUserSessionCacheByToken(xsrfToken: String, xsrfTokenCookie: String, userAgentHeader: String): Option[String] = {

    // TODO here replace cache access with DB
    val userEmailOpt = cache.get[String](xsrfToken)

    if (userEmailOpt.isEmpty) {
      logger.debug(s"no session for xsrfToken: $xsrfToken")
      None
    } else {
      val cookieForUSerAndUserAgent = passwordHashing.testSessionCookie(xsrfToken, userEmailOpt.get, userAgentHeader)
      logger.trace(s"testcookie: $cookieForUSerAndUserAgent")
      if (xsrfToken == xsrfTokenCookie && cookieForUSerAndUserAgent) {
        logger.trace(s"request for active session: $userEmailOpt.get / $xsrfToken / $userAgentHeader")
        userEmailOpt
      } else {
        logger.error("Invalid Token")
        None
      }
    }
  }

  /**
    * remove a server-side session
    *
    * @param userEmail
    * @param xsrfToken
    */
  def removeUserSessionCache(userEmail: String, xsrfToken: String): Unit = {
    // TODO here replace with DB cache
    cache.remove(xsrfToken)
  }

  /**
    * unambiguous keep track of user upload files and owning them
    *
    * @param filename
    * @param authUser
    * @param filelink
    * @return
    */
  def insertUserFileEntry(filename: String, authUser: String, filelink: String): Option[UserFile] = {

    val userLookup = dbSession.viaConnection { implicit connection =>
      UserDAO.findUserByEmailAsString(authUser)
    }

    userLookup.fold[Option[UserFile]] {
      // user not found, then can't insert, shouldn't happen though
      logger.error("User not found, can't store in users collection")
      None
    } {
      user =>

        val userFile = UserFile(
          uuid = UUID.randomUUID(),
          users_accountsubject = user.accountSubject,
          originalfilename = filename,
          linkreference = filelink,
          laststatustoken = "UPLOAD",
          laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

        dbSession.viaTransaction(implicit connection => {
          UserFile.createUserFile(userFile)
        })
    }
  }


  /**
    * unambiguous keep track of user metadata records and owning them
    *
    * @param CSW_URL
    * @param mdMetadataUuid
    * @param authUser
    * @return
    */
  def insertUserMetaRecordEntry(CSW_URL: String, mdMetadataUuid: String, authUser: String): Option[UserMetaRecord] = {

    val userLookup = dbSession.viaConnection { implicit connection =>
      UserDAO.findUserByEmailAsString(authUser)
    }

    userLookup.fold[Option[UserMetaRecord]] {
      // user not found, then can't insert, shouldn't happen though
      logger.error("User not found, can't store in users collection")
      None
    } {
      user =>

        val userMetaRecord = UserMetaRecord(
          uuid = UUID.randomUUID(),
          users_accountsubject = user.accountSubject,
          originaluuid = mdMetadataUuid,
          cswreference = s"https://portal.smart-project.info/pycsw/csw?request=GetRecordById&service=CSW&version=2.0.2&elementSetName=full&outputSchema=http://www.isotc211.org/2005/gmd&id=$mdMetadataUuid",
          laststatustoken = "UPLOAD",
          laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

        dbSession.viaTransaction(implicit connection => {
          UserMetaRecord.createUserMetaRecord(userMetaRecord)
        })
    }
  }

  /**
    * log file downloads via consent accept button on webgui
    *
    * @param logRequest
    */
  def logLinkInfo(logRequest: UserLinkLogging): Int = {
    dbSession.viaConnection(implicit connection => {
      UserLinkLogging.createUserLinkLogging(logRequest)
    })
  }

}
