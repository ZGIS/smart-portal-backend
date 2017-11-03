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
import models.owc.OwcContextDAO
import models.users._
import play.api.Configuration
import uk.gov.hmrc.emailaddress.EmailAddress
import utils.{ClassnameLogger, PasswordHashing}

@Singleton
class UserService @Inject()(dbSession: DatabaseSessionHolder,
                            val passwordHashing: PasswordHashing,
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
        val status = user.getToken
        Left(ErrorResult(s"Your account was ${status.value}. Please contact the administrator.", None))
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
    * @param accountSubject
    * @return
    */
  def findUserByAccountSubject(accountSubject: String): Option[User] = {
    dbSession.viaConnection(implicit connection => {
      UserDAO.findByAccountSubject(accountSubject)
    })
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
    * passing deleteUser(user) request through from controller,
    * BUT we need to delete contexts, userfiles and bucket objects?,
    * usermetarecords and the csw records?
    *
    *
    * @param user
    * @return
    */
  def deleteUser(user: User): Boolean = {
    dbSession.viaTransaction(implicit connection => {
      // val userFiles = UserFile.findUserFileByAccountSubject(user.accountSubject)
      // for file in userFiles delete in DB and maybe even delete in remote datastore?

      // val userMetas = UserMetaRecord.findUserMetaRecordByAccountSubject(user.accountSubject)
      // for record in userMetas delete in DB and maybe even delete in remote csw?

      val userGroups = UserGroup.findUserGroupsForUser(user)
      // for each usergroups_has_users remove row with users_accountsubject reference

      val updatedUserGroups = userGroups.map{ ug =>
        val withRemoved = ug.hasUsersLevel.filterNot(level => level.users_accountsubject.equals(user.accountSubject))
        ug.copy(hasUsersLevel = withRemoved)
      }.forall(ug => UserGroup.updateUserGroup(ug).isDefined)

      // val contexts = OwcContextDAO.findOwcContextsByUser(user)
      // for each owcContext in contexts remove reference from usergroups_has_owc_context_rights
      // delete each owcContext in contexts

      // eventually delete user
      // UserDAO.deleteUser(user)
      val statusToken = StatusToken.DELETED
      val updateUser = user.copy(laststatustoken = s"${statusToken.value}:BY-SELF",
        laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

      UserDAO.updateNoPass(updateUser).isDefined && updatedUserGroups
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
  def upsertUserSession(userEmail: String, userAgentHeader: String): String = {
    val token = passwordHashing.createSessionCookie(userEmail, userAgentHeader)
    val sessionOpt = dbSession.viaConnection(implicit connection => {
      UserSession.findUserSessionByToken(token, 1).fold{
        UserSession.createUserSession(UserSession(
          token = token,
          useragent = userAgentHeader,
          email = userEmail,
          laststatustoken = s"${StatusToken.ACTIVE}:NEW_SESSION",
          laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone))
        ))
      }{
        session =>
          UserSession.updateUserSessionStatus(UserSession(
            token = token,
            useragent = userAgentHeader,
            email = userEmail,
            laststatustoken = s"${StatusToken.ACTIVE}:SESSION_UPDATE",
            laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone))
          ))
      }
    })

    sessionOpt.map(_.token).getOrElse("XXXXXXXXXXXXXXXXXXXXX-INVALID-xxxxxxxxxxxxxxx")
  }

  /**
    * check for an active server-side session
    *
    * @param xsrfToken
    * @param xsrfTokenCookie
    * @param userAgentHeader
    * @return
    */
  def getUserSessionByToken(xsrfToken: String, xsrfTokenCookie: String, userAgentHeader: String): Option[UserSession] = {
    dbSession.viaConnection(implicit connection => {
      UserSession.findUserSessionByToken(xsrfToken, 1)
    })
      .fold[Option[UserSession]] {
        logger.debug(s"no session for xsrfToken: $xsrfToken")
        None
      } {
        session =>
          val cookieForUSerAndUserAgent = passwordHashing.testSessionCookie(xsrfToken, session.email, userAgentHeader)
          logger.trace(s"testcookie: $cookieForUSerAndUserAgent")
          if (xsrfToken == xsrfTokenCookie && cookieForUSerAndUserAgent) {
            logger.trace(s"request for active session: ${session.email} / $xsrfToken / $userAgentHeader")
            Some(session)
          } else {
            logger.error("Invalid Token")
            None
          }
    }
  }

  /**
    * auxiliary intermediate for above function
    *
    * @param xsrfToken
    * @param xsrfTokenCookie
    * @param userAgentHeader
    * @return
    */
  def findUserSessionByToken(xsrfToken: String, xsrfTokenCookie: String, userAgentHeader: String): Option[String] = {
    getUserSessionByToken(xsrfToken, xsrfTokenCookie, userAgentHeader).map(_.email)
  }

  /**
    * remove a server-side session
    *
    * @param userEmail
    * @param xsrfToken
    */
  def removeUserSessionCache(userEmail: String, xsrfToken: String): Unit = {
    dbSession.viaConnection(implicit connection => {
      UserSession.deleteUserSessionByToken(xsrfToken)
    })
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
    * for reverse mapping finding the file
    *
    * @param uuid
    * @return
    */
  def findUserFileByUuid(uuid: UUID): Option[UserFile] = {
    dbSession.viaConnection( implicit connection =>
      UserFile.findUserFileByUuid(uuid)
    )
  }

  /**
    * for reverse mapping finding the files
    *
    * @param user
    * @return
    */
  def findUserFileByAccountSubject(user: User): Seq[UserFile] = {
    dbSession.viaConnection( implicit connection =>
      UserFile.findUserFileByAccountSubject(user.accountSubject)
    )
  }

  def updateUserFile(userFile: UserFile): Option[UserFile] = {
    dbSession.viaConnection( implicit connection =>
      UserFile.updateUserFile(userFile)
    )
  }

  /**
    * deleting, needs upstream auth logic who is allowed and who not
    *
    * @param uuid
    * @return
    */
  def deleteUserFile(uuid: UUID): Boolean = {
    dbSession.viaTransaction( implicit connection =>
      UserFile.deleteUserFile(uuid)
    )
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
    * for reverse mapping finding the record ref
    *
    * @param uuid
    * @return
    */
  def findUserMetaRecordByUuid(uuid: UUID): Option[UserMetaRecord] = {
    dbSession.viaConnection( implicit connection =>
      UserMetaRecord.findUserMetaRecordByUuid(uuid)
    )
  }

  /**
    * for reverse mapping finding the  record ref
    *
    * @param user
    * @return
    */
  def findUserMetaRecordByAccountSubject(user: User): Seq[UserMetaRecord] = {
    dbSession.viaConnection( implicit connection =>
      UserMetaRecord.findUserMetaRecordByAccountSubject(user.accountSubject)
    )
  }

  def updateUserMetaRecord(userMetaRecord: UserMetaRecord): Option[UserMetaRecord] = {
    dbSession.viaConnection( implicit connection =>
      UserMetaRecord.updateUserMetaRecord(userMetaRecord)
    )
  }

  /**
    * deleting record ref, needs upstream auth logic who is allowed and who not
    *
    * @param uuid
    * @return
    */
  def deleteUserMetaRecord(uuid: UUID): Boolean = {
    dbSession.viaTransaction( implicit connection =>
      UserMetaRecord.deleteUserMetaRecord(uuid)
    )
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
