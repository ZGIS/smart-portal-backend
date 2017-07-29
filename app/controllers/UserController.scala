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

package controllers

import java.io.FileReader
import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID
import javax.inject._

import com.google.api.client.googleapis.auth.oauth2._
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import models.ErrorResult
import models.db.SessionHolder
import models.users._
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import services.{EmailService, OwcCollectionsService}
import uk.gov.hmrc.emailaddress.EmailAddress
import utils.{ClassnameLogger, PasswordHashing}

import scala.util.{Failure, Success, Try}

/**
  * typical profile / account json exchange format, we could add picture and some other nice stuff?
  *
  * @param email
  * @param accountSubject
  * @param firstname
  * @param lastname
  */
case class ProfileJs(email: EmailAddress,
                     accountSubject: String,
                     firstname: String,
                     lastname: String)

/**
  * here password is compulsory
  *
  * @param email
  * @param accountSubject
  * @param firstname
  * @param lastname
  * @param password
  */
case class RegisterJs(email: EmailAddress,
                      accountSubject: String,
                      firstname: String,
                      lastname: String,
                      password: String)

/**
  *
  * @param config
  * @param cacheApi
  * @param emailService
  * @param collectionsService
  * @param sessionHolder
  * @param passwordHashing
  */
@Singleton
class UserController @Inject()(config: Configuration,
                               cacheApi: CacheApi,
                               emailService: EmailService,
                               collectionsService: OwcCollectionsService,
                               sessionHolder: SessionHolder,
                               override val passwordHashing: PasswordHashing) extends Controller with ClassnameLogger with Security {

  lazy private val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")
  lazy private val googleClientSecret: String = configuration.getString("google.client.secret")
    .getOrElse("client_secret.json")
  val cache: play.api.cache.CacheApi = cacheApi
  val configuration: play.api.Configuration = config
  val gauthRedirectUrl = "postmessage"

  /**
    * self registering for user accounts
    * will create "preliminary" user account and send out confirmation email with unique link to "activate" account
    * we could require app dunctions only to be used by "ACTIVE" users wit hconfirmed email
    *
    * @return
    */
  def registerUser: Action[JsValue] = Action(parse.json) { request =>

    request.body.validate[RegisterJs].fold(
      errors => {
        logger.error(JsError.toJson(errors).toString())
        val error = ErrorResult("Error while validating request.", Some(JsError.toJson(errors).toString))
        BadRequest(Json.toJson(error)).as(JSON)
      },
      jsuser => {
        // found none, good ...
        sessionHolder.viaTransaction { implicit connection =>
          UserDAO.findUserByEmailAddress(jsuser.email).fold {
            // found none, good, create user
            val regLinkId = java.util.UUID.randomUUID().toString()
            val cryptPass = passwordHashing.createHash(jsuser.password)

            val newUser = User(jsuser.email,
              s"local:${jsuser.email}",
              jsuser.firstname,
              jsuser.lastname,
              cryptPass,
              s"${StatusToken.REGISTERED}:$regLinkId",
              ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

            UserDAO.createUser(newUser).fold {
              logger.error("User create error.")
              val error = ErrorResult("User create error.", None)
              BadRequest(Json.toJson(error)).as(JSON)
            } { user =>
              val emailWentOut = emailService.sendRegistrationEmail(jsuser.email, "Please confirm your GW HUB account", jsuser.firstname, regLinkId)
              logger.info(s"New user registered. Email went out $emailWentOut")
              // creating default collection only after registration, account is only barely usable here
              Ok(Json.toJson(user.asProfileJs()))
            }
          } { user =>
            // found user with that email already
            logger.error("Email already in use.")
            val error = ErrorResult("Email already in use.", None)
            BadRequest(Json.toJson(error)).as(JSON)
          }
        }
      })
  }

  /**
    * confirm the registration link, based on that email is confirmed,
    * could add more require logic to app functions that want an established email account
    *
    * @param linkId
    * @return
    */
  def registerConfirm(linkId: String): Action[AnyContent] = Action { request =>
    // check db laststatustoken, get user etc, update status
    val uuidTest = Try(java.util.UUID.fromString(linkId))
    uuidTest match {
      case Success(v) => {
        // logger.debug("Result of " + uuidTest.get.toString + " is: " + v)
        sessionHolder.viaTransaction { implicit connection =>
          UserDAO.findRegisteredUsersWithRegLink(linkId).headOption.fold {
            // found none, if more than one take head of the list
            logger.error("Unknown registration link.")
            // flash error message
            // BadRequest(Json.obj("status" -> "ERR", "message" -> "Unknown registration link."))
            Redirect("/#/register")
          } { user =>
            // good, update regstatus and send confirmation email
            val updateUser = User(
              user.email,
              user.accountSubject,
              user.firstname,
              user.lastname,
              "***",
              s"${StatusToken.ACTIVE}:REGCONFIRMED",
              ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

            UserDAO.updateNoPass(updateUser).fold {
              logger.error("User update error.")
              // Redirect("/#/register")
              // flash error message?
              val error = ErrorResult("Error while updating user registration", None)
              InternalServerError(Json.toJson(error)).as(JSON)
            } { user =>
              val emailWentOut = emailService.sendConfirmationEmail(user.email, "Your GW HUB account is now active", user.firstname)
              // all good here, creating default collection (Unit)
              val userOwcDoc = collectionsService.createUserDefaultCollection(user)
              // maybe should also do the full login thing here? how does that relate with the alternative Google Oauth thing
              userOwcDoc.fold {
                logger.error("Couldn't create user default collection.")
                val error = ErrorResult("Error while creatin user default collection.", None)
                InternalServerError(Json.toJson(error)).as(JSON)
              } { owcDoc =>
                logger.info(s"Registered user ${user.accountSubject} confirmed email ${user.email}. " +
                  s"Email went out $emailWentOut. User Collection ${owcDoc.id.toString}")
                Redirect("/#/login")
              }
            }
          }
        }
      }
      case Failure(e) => {
        // wrong uuid format even
        logger.error("Info from the exception: " + e.getMessage)
        // maybe flash error message
        Redirect("/#/register")
      }
    }
  }

  /**
    * get own profile based on security token
    *
    * @return
    */
  def userSelf: Action[Unit] = HasToken(parse.empty) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          sessionHolder.viaConnection(implicit connection =>
            UserDAO.findUserByEmailAsString(cachedSecUserEmail).fold {
              logger.error("User email not found.")
              val error = ErrorResult("User email not found.", None)
              BadRequest(Json.toJson(error)).as(JSON)
            } { user =>
              Ok(Json.toJson(user.asProfileJs()))
            }
          )
  }

  /**
    * get own profile based on security token
    *
    * @return
    */
  def deleteSelf: Action[Unit] = HasToken(parse.empty) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          sessionHolder.viaTransaction { implicit connection =>
            UserDAO.findUserByEmailAsString(cachedSecUserEmail).fold {
              logger.error("User email not found.")
              val error = ErrorResult("User email not found.", None)
              BadRequest(Json.toJson(error)).as(JSON)
            } { user =>
              val result = UserDAO.deleteUser(user)
              if (result) {
                cache.remove(token)
                Ok.discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
              } else {
                logger.error(s"User ${user.accountSubject} could not be deleted.")
                val error = ErrorResult("Sorry, we could not delete your account.", None)
                BadRequest(Json.toJson(error)).as(JSON)
              }
            }
          }
  }

  /**
    * get user's profle, currently only one's own profile (token match)
    *
    * @param email
    * @return
    */
  def getProfile(email: String): Action[Unit] = HasToken(parse.empty) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          sessionHolder.viaConnection(implicit connection =>
            UserDAO.findUserByEmailAsString(email).fold {
              logger.error("User email not found.")
              val error = ErrorResult("User email not found.", None)
              BadRequest(Json.toJson(error)).as(JSON)
            } { user =>
              // too much checking here?
              if (user.email.value.equals(email) && cachedSecUserEmail.equals(email)) {
                Ok(Json.toJson(user.asProfileJs()))
              } else {
                logger.error("User email Security Token mismatch.")
                val error = ErrorResult("User email Security Token mismatch.", None)
                Forbidden(Json.toJson(error)).as(JSON)
              }
            }
          )
  }

  /**
    * update user's profile, can "never" touch password, password update is separate
    *
    * @return
    */
  def updateProfile: Action[JsValue] = HasToken(parse.json) {
    token =>
      cachedSecUserEmail =>
        implicit request =>

          val profileResult = request.body.validate[ProfileJs]
          profileResult.fold(
            errors => {
              val error = ErrorResult("Could not validate request.", Some(JsError.toJson(errors).toString()))
              BadRequest(Json.toJson(error)).as(JSON)
            },
            incomingProfileJs => {
              sessionHolder.viaTransaction { implicit connection =>
                // looking first for the HasToken auth subject
                UserDAO.findUserByEmailAsString(cachedSecUserEmail).fold {
                  logger.error("User Security Token mismatch.")
                  val error = ErrorResult("User Security Token mismatch.", None)
                  Forbidden(Json.toJson(error)).as(JSON)
                } { hasTokenDbUser =>
                  // This should be you. we don't change the accountSubject. email change is ok but needs to be extra validated
                  if (hasTokenDbUser.email.equals(incomingProfileJs.email)) {
                    // email didn't change, good, let's update your profile in the database (but not password nor accountsubject)
                    val updateUser = hasTokenDbUser.copy(email = incomingProfileJs.email,
                      firstname = incomingProfileJs.firstname,
                      lastname = incomingProfileJs.lastname,
                      laststatustoken = s"${StatusToken.ACTIVE}:PROFILEUPDATE",
                      laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

                    UserDAO.updateNoPass(updateUser).fold {
                      logger.error("Could not update User.")
                      val error = ErrorResult("Could not update user.", None)
                      BadRequest(Json.toJson(error)).as(JSON)
                    } { user =>
                      val emailWentOut = emailService.sendProfileUpdateInfoEmail(user.email, "Your account profile information was updated", user.firstname)
                      logger.info(s"User profile updated. Email went out $emailWentOut")
                      Ok(Json.toJson(user.asProfileJs()))
                    }
                  } else {
                    // check if new email is used already by another user than yourself
                    UserDAO.findUserByEmailAddress(incomingProfileJs.email).fold {

                      // found none, good, let's update your profile in the database (but not password nor accountsubject)
                      val regLinkId = java.util.UUID.randomUUID().toString()
                      val updateUser = hasTokenDbUser.copy(email = incomingProfileJs.email,
                        firstname = incomingProfileJs.firstname,
                        lastname = incomingProfileJs.lastname,
                        laststatustoken = s"${StatusToken.EMAILVALIDATION}:$regLinkId",
                        laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

                      UserDAO.updateNoPass(updateUser).fold {
                        logger.error("Could not update User.")
                        val error = ErrorResult("Could not update user.", None)
                        BadRequest(Json.toJson(error)).as(JSON)
                      } { user =>
                        val emailWentOut = emailService.sendNewEmailValidationEmail(user.email, "Please confirm your new email address", user.firstname, regLinkId)
                        logger.info(s"User profile updated with email change. Email went out $emailWentOut with reglink: $regLinkId")
                        Ok(Json.toJson(user.asProfileJs()))
                      }
                    } { dbuser =>
                      // found one, that means the email is already in use, ABORT
                      logger.error("Email already in use.")
                      val error = ErrorResult("Email already in use.", None)
                      BadRequest(Json.toJson(error)).as(JSON)
                    }
                  }
                }
              }
            })

  }

  /**
    * should require "double check" in gui to issue this, requires to accept and store new session secret
    *
    * @return
    */
  def updatePassword(): Action[JsValue] = HasToken(parse.json) {
    token =>
      cachedSecUserEmail =>
        implicit request =>
          request.body.validate[PasswordUpdateCredentials].fold(
            errors => {
              logger.error(JsError.toJson(errors).toString())
              val error = ErrorResult("Could not validate request.", Some(JsError.toJson(errors).toString()))
              BadRequest(Json.toJson(error)).as(JSON)
            },
            valid = credentials => {
              // find user in db
              sessionHolder.viaTransaction { implicit connection =>
                UserDAO.findUserByEmailAddress(credentials.email).fold {
                  logger.error("User not found.")
                  val error = ErrorResult("User not found", None)
                  BadRequest(Json.toJson(error)).as(JSON)
                } { dbuser =>
                  // compare password
                  if (passwordHashing.validatePassword(credentials.oldPassword, dbuser.password)) {
                    val newCryptPass = passwordHashing.createHash(credentials.newPassword)
                    val updateUser = dbuser.copy(
                      password = newCryptPass,
                      laststatustoken = s"${StatusToken.ACTIVE}:PASSWORDUPDATE",
                      laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

                    UserDAO.updatePassword(updateUser).fold {
                      logger.error("Password update error.")
                      val error = ErrorResult("Password update error.", None)
                      BadRequest(Json.toJson(error)).as(JSON)
                    } { user =>
                      val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
                      cache.remove(token)
                      emailService.sendPasswordUpdateEmail(user.email, "Password Update on GW HUB", user.firstname)
                      val newtoken = passwordHashing.createSessionCookie(user.email, uaIdentifier)
                      cache.set(newtoken, user.email.value)
                      Ok(Json.obj("status" -> "OK", "token" -> newtoken, "email" -> user.email.value))
                        .withCookies(Cookie(AuthTokenCookieKey, newtoken, None, httpOnly = false))
                    }
                  } else {
                    logger.error("Password update error, not matching.")
                    val error = ErrorResult("Password update error, not matching.", None)
                    BadRequest(Json.toJson(error)).as(JSON)
                  }
                }
              }
            })
  }

  /**
    *
    * @return
    */
  def resetPasswordRequest: Action[JsValue] = Action(parse.json) { request =>
    request.body.validate[LoginCredentials].fold(
      errors => {
        logger.error(JsError.toJson(errors).toString())
        val error = ErrorResult("Could not validate request.", Some(JsError.toJson(errors).toString()))
        BadRequest(Json.toJson(error)).as(JSON)
      },
      valid = credentials => {
        // find user in db
        sessionHolder.viaTransaction { implicit connection =>
          UserDAO.findUserByEmailAddress(credentials.email).fold {
            logger.error("User email not found.")
            val error = ErrorResult("User email not found.", None)
            BadRequest(Json.toJson(error)).as(JSON)
          } { user =>
            val resetLink = java.util.UUID.randomUUID().toString
            // good, update regstatus and send confirmation email
            val updateUser = user.copy(laststatustoken = s"${StatusToken.PASSWORDRESET}:$resetLink",
              laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

            UserDAO.updateNoPass(updateUser).fold {
              logger.error("User reset password request update error.")
              // flash error message?
              val error = ErrorResult("User reset password request update error.", None)
              BadRequest(Json.toJson(error)).as(JSON)
              // Redirect("/#/register")
            } { user =>
              emailService.sendResetPasswordRequestEmail(user.email, "Password Update on GW HUB", user.firstname, resetLink)
              logger.info(s"Registered user ${user.email} requested reset password. Email went out")
              Ok(Json.obj("status" -> "OK", "email" -> user.email.value, "message" -> "Email went out"))
            }
          }
        }
      })
  }

  /**
    *
    * @param linkId
    * @return
    */
  def resetPasswordRedeem(linkId: String): Action[JsValue] = Action(parse.json) { request =>
    // check db laststatustoken, get user etc, update status
    val uuidTest = Try(java.util.UUID.fromString(linkId))
    uuidTest match {
      case Success(v) => {
        sessionHolder.viaTransaction { implicit connection =>
          UserDAO.findUsersByPassResetLink(linkId).headOption.fold {
            logger.error("Unknown password reset link.")
            Redirect("/#/register")
          } { user =>
            // good, update password and status and send confirmation email
            request.body.validate[LoginCredentials].fold(
              errors => {
                logger.error(JsError.toJson(errors).toString())
                val error = ErrorResult("Could not validate request.", Some(JsError.toJson(errors).toString()))
                BadRequest(Json.toJson(error)).as(JSON)
              },
              valid = credentials => {
                val cryptPass = passwordHashing.createHash(credentials.password)
                val updateUser = User(
                  user.email,
                  user.accountSubject,
                  user.firstname,
                  user.lastname,
                  cryptPass,
                  s"${StatusToken.ACTIVE}:PASSWORDUPDATED",
                  ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

                UserDAO.updatePassword(updateUser).fold {
                  logger.error("User update error.")
                  // flash error message?
                  // BadRequest(Json.obj("status" -> "ERR", "message" -> "User update error."))
                  Redirect("/#/login")
                } { user =>
                  val emailWentOut = emailService.sendPasswordUpdateEmail(user.email, "Password Update on GW HUB", user.firstname)
                  Redirect("/#/login")
                }
              })

          }
        }
      }
      case Failure(e) => {
        // wrong uuid format even
        logger.error("Info from the exception: " + e.getMessage)
        // maybe flash error message
        Redirect("/#/register")
      }
    }
  }


  /**
    * part of OAuth2 Flow for Google Login in
    *
    * @return
    */
  def gconnect: Action[JsValue] = Action(parse.json) { implicit request =>
    request.body.validate[GAuthCredentials].fold(
      errors => {
        logger.error(JsError.toJson(errors).toString())
        val error = ErrorResult("Could not validate request.", Some(JsError.toJson(errors).toString()))
        BadRequest(Json.toJson(error)).as(JSON)
      },
      valid = credentials => {
        if (!new java.io.File(googleClientSecret).exists) {
          val error = ErrorResult("Service JSON file not available", None)
          InternalServerError(Json.toJson(error)).as(JSON)
        } else {
          // do lots of Google OAuth2 stuff
          val clientSecrets = GoogleClientSecrets.load(
            JacksonFactory.getDefaultInstance(), new FileReader(googleClientSecret))

          // 988846878323-bkja0j1tgep5ojthfr2e92ao8n7iksab.apps.googleusercontent.com
          val clientId = clientSecrets.getDetails().getClientId()
          logger.debug(s"clientId $clientId")
          // Specify the same redirect URI that you use with your web
          // app. If you don't have a web version of your app, you can
          // specify an empty string.
          val tokenResponse: GoogleTokenResponse = new GoogleAuthorizationCodeTokenRequest(
            new NetHttpTransport(),
            JacksonFactory.getDefaultInstance(),
            "https://www.googleapis.com/oauth2/v4/token",
            clientId,
            clientSecrets.getDetails().getClientSecret(),
            credentials.authcode,
            gauthRedirectUrl).execute()

          val accessToken = tokenResponse.getAccessToken()
          // Use access token to call API
          val credential = new GoogleCredential().setAccessToken(accessToken)
          // Get profile info from ID token
          val idToken: GoogleIdToken = tokenResponse.parseIdToken()
          val payload = idToken.getPayload()
          // Use this value as a key to identify a user.
          val userId = payload.getSubject()
          val email = payload.getEmail()
          val emailVerified = payload.getEmailVerified()
          // get further payload things that is easy in java but needs to work in scala too
          val name = Option(payload.get("name").asInstanceOf[String])
          val pictureUrl = Option(payload.get("picture").asInstanceOf[String])
          val locale = Option(payload.get("locale").asInstanceOf[String])
          val familyName = Option(payload.get("family_name").asInstanceOf[String])
          val givenName = Option(payload.get("given_name").asInstanceOf[String])
          if ((credentials.accesstype.equalsIgnoreCase("LOGIN") || credentials.accesstype.equalsIgnoreCase("REGISTER")) &&
            emailVerified && EmailAddress.isValid(email)) {

            val emailAddress = EmailAddress(email)
            // viaConnection lookup email block
            val firstUserLookup = sessionHolder.viaConnection { implicit connection =>
              UserDAO.findUserByEmailAddress(emailAddress)
            }

            firstUserLookup.fold {
              // 'error, no user': well, must be a new user coming through from Google
              val cryptPass = passwordHashing.createHash(UUID.randomUUID().toString)

              val newUser = User(emailAddress,
                s"google:${userId}",
                givenName.getOrElse(name.getOrElse(emailAddress.mailbox.value)),
                familyName.getOrElse(""),
                cryptPass,
                s"${StatusToken.ACTIVE}:REGCONFIRMED",
                ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

              val createUserCall = sessionHolder.viaConnection { implicit connection =>
                UserDAO.createUser(newUser)
              }

              createUserCall.fold {
                logger.error("User create error.")
                val error = ErrorResult("Error while creating user", None)
                InternalServerError(Json.toJson(error)).as(JSON)
              } { createdUser =>
                val emailWentOut = emailService.sendConfirmationEmail(createdUser.email, "Your GW HUB account is now active", createdUser.firstname)
                // all good here, creating default collection (Unit)
                val userOwcDocTransaction = sessionHolder.viaTransaction { implicit connection =>
                  collectionsService.createUserDefaultCollection(createdUser)
                }

                userOwcDocTransaction.fold {
                  logger.error(s"Couldn't create user default collection for ${createdUser.accountSubject}.")
                  val error = ErrorResult("Error while creating user default collection.", None)
                  InternalServerError(Json.toJson(error)).as(JSON)
                } { owcDoc =>
                  logger.info(s"New user registered ${createdUser.accountSubject}. Email went out $emailWentOut. " +
                    s"User Collection ${owcDoc.id.toString}")
                  val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
                  // logger.debug(s"Logging in email from $uaIdentifier")
                  val token = passwordHashing.createSessionCookie(createdUser.email, uaIdentifier)
                  cache.set(token, createdUser.email.value)
                  Ok(Json.obj("status" -> "OK", "token" -> token, "email" -> createdUser.email.value, "userprofile" -> createdUser.asProfileJs()))
                    .withCookies(Cookie(AuthTokenCookieKey, token, None, httpOnly = false))
                }
              }
            } { existingUser =>
              val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
              // logger.debug(s"Logging in email from $uaIdentifier")
              val token = passwordHashing.createSessionCookie(existingUser.email.value, uaIdentifier)
              cache.set(token, existingUser.email.value)
              Ok(Json.obj("status" -> "OK", "token" -> token, "email" -> existingUser.email.value, "userprofile" -> existingUser.asProfileJs()))
                .withCookies(Cookie(AuthTokenCookieKey, token, None, httpOnly = false))
            }

          } else {
            logger.error("Invalid accesstype requested")
            val error = ErrorResult("Invalid accestype requested", None)
            BadRequest(Json.toJson(error)).as(JSON)
          }
        }
      }
    )
  }


  /**
    * Disconnect the Google login
    *
    * @return
    */
  def gdisconnect: Action[Unit] = HasToken(parse.empty) {
    token =>
      email =>
        implicit request =>
          cache.remove(token)
          // do some Google OAuth2 unregisteR
          Ok.discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
  }

  /**
    *
    * @return
    */
  def oauth2callback: Action[AnyContent] = Action {
    Redirect("/#/account")
  }
}
