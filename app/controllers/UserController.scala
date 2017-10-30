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

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID
import javax.inject._

import controllers.security._
import models.ErrorResult
import models.users._
import play.api.Configuration
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import services.{EmailService, GoogleServicesDAO, OwcCollectionsService, UserService}
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
  * @param configuration
  * @param userService
  * @param passwordHashing
  * @param emailService
  * @param collectionsService
  * @param googleService
  * @param authenticationAction
  * @param userAction
  */
@Singleton
class UserController @Inject()(implicit configuration: Configuration,
                               userService: UserService,
                               passwordHashing: PasswordHashing,
                               emailService: EmailService,
                               collectionsService: OwcCollectionsService,
                               googleService: GoogleServicesDAO,
                               authenticationAction: AuthenticationAction,
                               userAction: UserAction)
  extends Controller with ClassnameLogger {

  /**
    * default actions composition, much more readable and "composable than original HasToken style implementation
    */
  private val defaultAuthAction = authenticationAction andThen userAction

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
        userService.findUserByEmailAddress(jsuser.email).fold {
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

          userService.createUser(newUser).fold {
            logger.error("User create error.")
            val error = ErrorResult("User create error.", None)
            BadRequest(Json.toJson(error)).as(JSON)
          } { user =>
            val emailWentOut = emailService.sendRegistrationEmail(jsuser.email, "Please confirm your GW HUB account", jsuser.firstname, regLinkId)
            logger.info(s"New user registered. Email went out $emailWentOut")
            // creating default collection only after registration, account is only barely usable here
            Ok(Json.toJson(user.asProfileJs))
          }
        } { user =>
          // found user with that email already
          logger.error("Email already in use.")
          val error = ErrorResult("Email already in use.", None)
          BadRequest(Json.toJson(error)).as(JSON)
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
        userService.findRegisteredUsersWithRegLink(linkId).headOption.fold {
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

          userService.updateNoPass(updateUser).fold {
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
      case Failure(e) => {
        // wrong uuid format even
        logger.error("Info from the exception: " + e.getMessage)
        // maybe flash error message
        Redirect("/#/register")
      }
    }
  }

  def userSelf: Action[Unit] = defaultAuthAction(parse.empty) {
    implicit request =>
      Ok(Json.toJson(request.user.asProfileJs))
  }

  /**
    * get own profile based on security token
    *
    * @return
    */
  def deleteSelf: Action[Unit] = defaultAuthAction(parse.empty) {
    implicit request =>
      val user = request.user
      val result = userService.deleteUser(user)
      if (result) {
        // cache.remove(token)
        userService.removeUserSessionCache(user.email, request.authenticatedRequest.userSession.token)
        Ok.discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
      } else {
        logger.error(s"User ${user.accountSubject} could not be deleted.")
        val error = ErrorResult("Sorry, we could not delete your account.", None)
        BadRequest(Json.toJson(error)).as(JSON)
      }
  }

  /**
    * get user's profle, currently only one's own profile (token match)
    *
    * @param email
    * @return
    */
  @deprecated
  def getProfile(email: String): Action[Unit] = defaultAuthAction(parse.empty) {
    implicit request =>
      userService.findUserByEmailAsString(email).fold {
        logger.error("User email not found.")
        val error = ErrorResult("User email not found.", None)
        BadRequest(Json.toJson(error)).as(JSON)
      } { user =>
        // currently only if it's the own user email matching with token.
        if (user.equals(request.user)) {
          Ok(Json.toJson(user.asProfileJs))
        } else {
          logger.error("User email Security Token mismatch.")
          val error = ErrorResult("User email Security Token mismatch.", None)
          Forbidden(Json.toJson(error)).as(JSON)
        }
      }
  }

  /**
    * update user's profile, can "never" touch password, password update is separate
    *
    * @return
    */
  def updateProfile: Action[JsValue] = defaultAuthAction(parse.json) {
    request =>
      val profileResult = request.body.validate[ProfileJs]
      profileResult.fold(
        errors => {
          val error = ErrorResult("Could not validate request.", Some(JsError.toJson(errors).toString()))
          BadRequest(Json.toJson(error)).as(JSON)
        },
        incomingProfileJs => {
          val hasTokenDbUser = request.user
          // This should be you. we don't change the accountSubject. email change is ok but needs to be extra validated
          if (hasTokenDbUser.email.equals(incomingProfileJs.email)) {
            // email didn't change, good, let's update your profile in the database (but not password nor accountsubject)
            val updateUser = hasTokenDbUser.copy(email = incomingProfileJs.email,
              firstname = incomingProfileJs.firstname,
              lastname = incomingProfileJs.lastname,
              laststatustoken = s"${StatusToken.ACTIVE}:PROFILEUPDATE",
              laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

            userService.updateNoPass(updateUser).fold {
              logger.error("Could not update User.")
              val error = ErrorResult("Could not update user.", None)
              BadRequest(Json.toJson(error)).as(JSON)
            } { user =>
              val emailWentOut = emailService.sendProfileUpdateInfoEmail(user.email, "Your account profile information was updated", user.firstname)
              logger.info(s"User profile updated. Email went out $emailWentOut")
              Ok(Json.toJson(user.asProfileJs))
            }
          } else {
            // check if new email is used already by another user than yourself
            userService.findUserByEmailAddress(incomingProfileJs.email).fold {

              // found none, good, let's update your profile in the database (but not password nor accountsubject)
              val regLinkId = java.util.UUID.randomUUID().toString()
              val updateUser = hasTokenDbUser.copy(email = incomingProfileJs.email,
                firstname = incomingProfileJs.firstname,
                lastname = incomingProfileJs.lastname,
                laststatustoken = s"${StatusToken.EMAILVALIDATION}:$regLinkId",
                laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

              userService.updateNoPass(updateUser).fold {
                logger.error("Could not update User.")
                val error = ErrorResult("Could not update user.", None)
                BadRequest(Json.toJson(error)).as(JSON)
              } { user =>
                val emailWentOut = emailService.sendNewEmailValidationEmail(user.email, "Please confirm your new email address", user.firstname, regLinkId)
                logger.info(s"User profile updated with email change. Email went out $emailWentOut with reglink: $regLinkId")
                Ok(Json.toJson(user.asProfileJs))
              }
            } { dbuser =>
              // found one, that means the email is already in use, ABORT
              logger.error("Email already in use.")
              val error = ErrorResult("Email already in use.", None)
              BadRequest(Json.toJson(error)).as(JSON)
            }
          }
        })
  }

  /**
    * should require "double check" in gui to issue this, requires to accept and store new session secret
    *
    * @return
    */
  def updatePassword(): Action[JsValue] = defaultAuthAction(parse.json) {
    request =>
      request.body.validate[PasswordUpdateCredentials].fold(
        errors => {
          logger.error(JsError.toJson(errors).toString())
          val error = ErrorResult("Could not validate request.", Some(JsError.toJson(errors).toString()))
          BadRequest(Json.toJson(error)).as(JSON)
        },
        valid = credentials => {
          // find user in db
          val dbuser = request.user
          // compare password
          if (passwordHashing.validatePassword(credentials.oldPassword, dbuser.password)) {
            val newCryptPass = passwordHashing.createHash(credentials.newPassword)
            val updateUser = dbuser.copy(
              password = newCryptPass,
              laststatustoken = s"${StatusToken.ACTIVE}:PASSWORDUPDATE",
              laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

            userService.updatePassword(updateUser).fold {
              logger.error("Password update error.")
              val error = ErrorResult("Password update error.", None)
              BadRequest(Json.toJson(error)).as(JSON)
            } { user =>
              val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
              // cache.remove(token)
              userService.removeUserSessionCache(request.user.email, request.authenticatedRequest.userSession.token)
              emailService.sendPasswordUpdateEmail(user.email, "Password Update on GW HUB", user.firstname)
              // val newtoken = passwordHashing.createSessionCookie(user.email, uaIdentifier)
              // cache.set(newtoken, user.email.value)
              val newtoken = userService.upsertUserSession(user.email.value, uaIdentifier)
              Ok(Json.obj("status" -> "OK", "token" -> newtoken, "email" -> user.email.value))
                .withCookies(Cookie(AuthTokenCookieKey, newtoken, None, httpOnly = false))
            }
          } else {
            logger.error("Password update error, not matching.")
            val error = ErrorResult("Password update error, not matching.", None)
            BadRequest(Json.toJson(error)).as(JSON)
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
        userService.findUserByEmailAddress(credentials.email).fold {
          logger.error("User email not found.")
          val error = ErrorResult("User email not found.", None)
          BadRequest(Json.toJson(error)).as(JSON)
        } { user =>
          val resetLink = java.util.UUID.randomUUID().toString
          // good, update regstatus and send confirmation email
          val updateUser = user.copy(laststatustoken = s"${StatusToken.PASSWORDRESET}:$resetLink",
            laststatuschange = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

          userService.updateNoPass(updateUser).fold {
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
        userService.findUsersByPassResetLink(linkId).headOption.fold {
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

              userService.updatePassword(updateUser).fold {
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
      case Failure(e) => {
        // wrong uuid format even
        logger.error("Info from the exception: " + e.getMessage)
        // maybe flash error message
        Redirect("/#/register")
      }
    }
  }


  /**
    * Log-out a user. Invalidates the authentication token.
    *
    * Discard the cookie [[AuthTokenCookieKey]] to have AngularJS no longer set the
    * X-XSRF-TOKEN in HTTP header.
    */
  def logout: Action[Unit] = authenticationAction(parse.empty) {
    request =>
      userService.removeUserSessionCache(request.userSession.email, request.userSession.token)
      Ok.discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
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
      valid = gAuthCredentials => {
        val accessTokenResult = googleService.getGoogleAuthorization(gAuthCredentials)
        accessTokenResult match {
          case Left(errorResult) => {
            logger.error(errorResult.message + errorResult.details.mkString)
            InternalServerError(Json.toJson(errorResult)).as(JSON)
          }
          case Right(googleTokenResponse) => {
            // Get profile info from ID token
            val gAuthPayload = googleTokenResponse.parseIdToken().getPayload
            // Use this value as a key to identify a user.
            val userId = gAuthPayload.getSubject()
            val email = gAuthPayload.getEmail()
            val emailVerified = gAuthPayload.getEmailVerified()
            // get further payload things that is easy in java but needs to work in scala too
            val name = Option(gAuthPayload.get("name").asInstanceOf[String])
            val familyName = Option(gAuthPayload.get("family_name").asInstanceOf[String])
            val givenName = Option(gAuthPayload.get("given_name").asInstanceOf[String])
            if ((gAuthCredentials.accesstype.equalsIgnoreCase("LOGIN") || gAuthCredentials.accesstype.equalsIgnoreCase("REGISTER")) &&
              emailVerified && EmailAddress.isValid(email)) {

              val emailAddress = EmailAddress(email)
              // viaConnection lookup email block
              val firstUserLookup = userService.findUserByEmailAddress(emailAddress)

              firstUserLookup.fold {
                // 'error, no user': well, must be a new user coming through from Google
                val cryptPass = passwordHashing.createHash(UUID.randomUUID().toString)

                // Use access token to call API for refresh or check-up?
                // val googleAccessToken = googleTokenResponse.getAccessToken()
                // val googleCredential = new GoogleCredential().setAccessToken(googleAccessToken)
                val newUser = User(emailAddress,
                  s"google:${userId}",
                  givenName.getOrElse(name.getOrElse(emailAddress.mailbox.value)),
                  familyName.getOrElse(""),
                  cryptPass,
                  s"${StatusToken.ACTIVE}:REGCONFIRMED",
                  ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

                val createUserCall = userService.createUser(newUser)

                createUserCall.fold {
                  logger.error("User create error.")
                  val error = ErrorResult("Error while creating user", None)
                  InternalServerError(Json.toJson(error)).as(JSON)
                } { createdUser =>
                  val emailWentOut = emailService.sendConfirmationEmail(createdUser.email, "Your GW HUB account is now active", createdUser.firstname)
                  // all good here, creating default collection (Unit)
                  val userOwcDocTransaction = collectionsService.createUserDefaultCollection(createdUser)

                  userOwcDocTransaction.fold {
                    logger.error(s"Couldn't create user default collection for ${createdUser.accountSubject}.")
                    val error = ErrorResult("Error while creating user default collection.", None)
                    InternalServerError(Json.toJson(error)).as(JSON)
                  } { owcDoc =>
                    logger.info(s"New user registered ${createdUser.accountSubject}. Email went out $emailWentOut. " +
                      s"User Collection ${owcDoc.id.toString}")
                    val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
                    // logger.debug(s"Logging in email from $uaIdentifier")
                    // val token = passwordHashing.createSessionCookie(createdUser.email, uaIdentifier)
                    // cache.set(token, createdUser.email.value)
                    val token = userService.upsertUserSession(createdUser.email.value, uaIdentifier)
                    Ok(Json.obj("status" -> "OK", "token" -> token, "email" -> createdUser.email.value, "userprofile" -> createdUser.asProfileJs))
                      .withCookies(Cookie(AuthTokenCookieKey, token, None, httpOnly = false))
                  }
                }
              } { existingUser =>
                val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
                // logger.debug(s"Logging in email from $uaIdentifier")
                // val token = passwordHashing.createSessionCookie(existingUser.email.value, uaIdentifier)
                // cache.set(token, existingUser.email.value)
                val token = userService.upsertUserSession(existingUser.email.value, uaIdentifier)
                Ok(Json.obj("status" -> "OK", "token" -> token, "email" -> existingUser.email.value, "userprofile" -> existingUser.asProfileJs))
                  .withCookies(Cookie(AuthTokenCookieKey, token, None, httpOnly = false))
              }

            } else {
              logger.error("Invalid accesstype requested")
              val error = ErrorResult("Invalid accestype requested", None)
              BadRequest(Json.toJson(error)).as(JSON)
            }
          }
          case _ => {
            logger.error("Google OAuth Access failed.")
            val error = ErrorResult("Google OAuth Access failed.", None)
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
  def gdisconnect: Action[Unit] = authenticationAction(parse.empty) {
    request =>
      // cache.remove(token)
      userService.removeUserSessionCache(request.userSession.email, request.userSession.token)
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
