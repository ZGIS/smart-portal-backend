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

package controllers

import java.time.{ZoneId, ZonedDateTime}
import javax.inject._

import models.{LoginCredentials, User, UserDAO}
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.http.Status._
import play.api.libs.json.{JsError, Json}
import play.api.mvc.{Action, Controller, Cookie}
import services.EmailService
import utils.{ClassnameLogger, PasswordHashing}

import scala.util.{Failure, Success, Try}

/**
  * typical profile / account json exchange format, we could add picture and some other nice stuff?
  *
  * @param email
  * @param username
  * @param firstname
  * @param lastname
  */
case class ProfileJs(email: String,
                     username: String,
                     firstname: String,
                     lastname: String )

/**
  * here password is compulsory
  *
  * @param email
  * @param username
  * @param firstname
  * @param lastname
  * @param password
  */
case class RegisterJs(email: String,
                     username: String,
                     firstname: String,
                     lastname: String,
                     password: String)


/**
  *
  * @param config
  * @param cacheApi
  * @param emailService
  */
@Singleton
class UserController @Inject()(config: Configuration,
                               cacheApi: CacheApi,
                               emailService: EmailService,
                               userDAO: UserDAO,
                               override val passwordHashing: PasswordHashing) extends Controller with ClassnameLogger with Security {

  val cache: play.api.cache.CacheApi = cacheApi
  val configuration: play.api.Configuration = config

  lazy private val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")

  /**
    * self registering for user accounts
    * will create "preliminary" user account and send out confirmation email with unique link to "activate" account
    * we could require app dunctions only to be used by "ACTIVE" users wit hconfirmed email
    *
    * @return
    */
  def registerUser = Action(parse.json) { request =>

    request.body.validate[RegisterJs].fold(
      errors => {
        logger.error(JsError.toJson(errors).toString())
        BadRequest(Json.obj("status" -> "ERR", "message" -> JsError.toJson(errors)))
      },
      jsuser => {
        userDAO.findByUsername(jsuser.username).fold {
          // found none, good, but need to check for email address too
          userDAO.findUserByEmail(jsuser.username).fold {
            // found none, good, create user
            val regLinkId = java.util.UUID.randomUUID().toString()
            val cryptPass = passwordHashing.createHash(jsuser.password)
            val emailWentOut = emailService.sendRegistrationEmail(jsuser.email, "Please confirm your GW HUB account", jsuser.firstname, regLinkId)

            val newUser = User(jsuser.email,
              jsuser.username,
              jsuser.firstname,
              jsuser.lastname,
              cryptPass,
              s"REGISTERED:$regLinkId",
              ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

            userDAO.createWithNps(newUser).fold {
              logger.error("User create error.")
              BadRequest(Json.obj("status" -> "ERR", "message" -> "User create error."))
            } { user =>
              logger.info("New user registered.")
              Ok(Json.toJson(user.asProfileJs()))
            }
          } { user =>
            // found user with that email already
            logger.error("Email already in use.")
            BadRequest(Json.obj("status" -> "ERR", "message" -> "Email already in use."))
          }
        } { user =>
          // found username already?! BAD
          logger.error("User not found.")
          BadRequest(Json.obj("status" -> "ERR", "message" -> "Username already in use."))
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
  def registerConfirm(linkId: String) = Action { request =>
    // check db laststatustoken, get user etc, update status
    val uuidTest = Try(java.util.UUID.fromString(linkId))
    uuidTest match {
      case Success(v) => {
        // logger.debug("Result of " + uuidTest.get.toString + " is: " + v)
        userDAO.findRegisteredUsersByRegLink(linkId).headOption.fold {
          // found none, if more than one take head of the list
          logger.error("Unknown registration link.")
          // flash error message
          // BadRequest(Json.obj("status" -> "ERR", "message" -> "Unknown registration link."))
          Redirect("/#/register")
        } { user  =>
          // good, update regstatus and send confirmation email
          val updateUser = User(
            user.email,
            user.username,
            user.firstname,
            user.lastname,
            "***",
            "ACTIVE:REGCONFIRMED",
            ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

          userDAO.updateNoPass(updateUser).fold {
            logger.error("User update error.")
            // flash error message?
            // BadRequest(Json.obj("status" -> "ERR", "message" -> "User update error."))
            Redirect("/#/register")
          } { user =>
            val emailWentOut = emailService.sendConfirmationEmail(user.email, "Your GW HUB account is now active", user.firstname)
            // all good here
            // maybe should also do the full login thing here? how does that relate with the alternative Google Oauth thing
            logger.info(s"Registered user ${user.username} confirmed email ${user.email}.")
            Redirect("/#/login")
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
  def userSelf = HasToken(parse.empty) {
    token =>
      cachedSecUser => implicit request =>

        userDAO.findByUsername(cachedSecUser).fold {
          logger.error("User not found.")
          BadRequest(Json.obj("status" -> "ERR", "message" -> "Username not found."))
        } { user =>
          Ok(Json.toJson(user.asProfileJs()))
        }
  }

  /**
    * get user's profle, currently only one's own profile (token match)
    *
    * @param username
    * @return
    */
  def getProfile(username: String) = HasToken(parse.empty) {
    token =>
      cachedSecUser => implicit request =>

        userDAO.findByUsername(username).fold {
          logger.error("User not found.")
          BadRequest(Json.obj("status" -> "ERR", "message" -> "Username not found."))
        } { user =>
          // too much checking here?
          if (user.username.equals(username) && cachedSecUser.equals(username)) {
            Ok(Json.toJson(user.asProfileJs()))
          } else {
            logger.error("Username Security Token mismatch.")
            Forbidden(Json.obj("status" -> "ERR", "message" -> "Username Security Token mismatch."))
          }
        }
  }

  /**
    * update user's profile, can "never" touch password, password update is separate
    *
    * @param username
    * @return
    */
  def updateProfile(username: String) = HasToken(parse.json) {
    token =>
      cachedSecUser => implicit request =>

        val profileResult = request.body.validate[ProfileJs]
        profileResult.fold(
          errors => {
            BadRequest(Json.obj("status" -> "ERR", "message" -> JsError.toJson(errors)))
          },
          jsuser => {
            userDAO.findByUsername(jsuser.username).fold {
              logger.error("User not found.")
              BadRequest(Json.obj("status" -> "ERR", "message" -> "Username not found."))
            } { dbuser =>
              // is it really you? by the way you shouldn't / can't (?!) change your username!!!
              if (dbuser.username.equals(jsuser.username) && cachedSecUser.equals(jsuser.username)) {

                // check if new email is used already by another user than yourself
                userDAO.findUserByEmail(jsuser.username).fold {
                  // found none, good
                  val updateUser = User(jsuser.email,
                    dbuser.username,
                    jsuser.firstname,
                    jsuser.lastname,
                    "***",
                    "ACTIVE:PROFILEUPDATE",
                    ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

                  userDAO.updateNoPass(updateUser).fold {
                    logger.error("User update error.")
                    BadRequest(Json.obj("status" -> "ERR", "message" -> "User update error."))
                  } { user =>
                    Ok(Json.toJson(user.asProfileJs()))
                  }
                } { dbuser =>
                  // found one, is it yourself or somebody else?
                  if (dbuser.username.equals(jsuser.username) && cachedSecUser.equals(jsuser.username)) {
                    // it is you, so we can update you
                    val updateUser = User(jsuser.email,
                      dbuser.username,
                      jsuser.firstname,
                      jsuser.lastname,
                      "***",
                      "ACTIVE:PROFILEUPDATE",
                      ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

                    userDAO.updateNoPass(updateUser).fold {
                      logger.error("User update error.")
                      BadRequest(Json.obj("status" -> "ERR", "message" -> "User update error."))
                    } { user =>
                      Ok(Json.toJson(user.asProfileJs()))
                    }
                  } else {
                    logger.error("Username Email mismatch, email already in use.")
                    BadRequest(Json.obj("status" -> "ERR", "message" -> "Username Email mismatch, email already in use."))
                  }
                }

              } else {
                logger.error("Username Security Token mismatch.")
                Forbidden(Json.obj("status" -> "ERR", "message" -> "Username Security Token mismatch."))
              }
            }

          })

  }

  /**
    * should require "double check" in gui to issue this, requires to accept and store new session secret
    *
    * @return
    */
  def updatePassword = HasToken(parse.json) {
    token =>
      cachedSecUser => implicit request =>

    request.body.validate[LoginCredentials].fold(
      errors => {
        logger.error(JsError.toJson(errors).toString())
        BadRequest(Json.obj("status" -> "ERR", "message" -> JsError.toJson(errors)))
      },
      valid = credentials => {
        // find user in db and compare password stuff
        userDAO.findByUsername(credentials.username).fold {
          logger.error("User not found.")
          BadRequest(Json.obj("status" -> "ERR", "message" -> "User not found."))
        } { dbuser =>
          val cryptPass = passwordHashing.createHash(credentials.password)
          val updateUser = User(dbuser.email,
            dbuser.username,
            dbuser.firstname,
            dbuser.lastname,
            cryptPass,
            "ACTIVE:PASSWORDUPDATE",
            ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

          userDAO.updatePassword(updateUser).fold {
            logger.error("Password update error.")
            BadRequest(Json.obj("status" -> "ERR", "message" -> "Password update error."))
          } { user =>
            val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
            // logger.info(s"Logging in username from $uaIdentifier")
            cache.remove(token)
            val newtoken = passwordHashing.createSessionCookie(user.username, uaIdentifier)
            cache.set(newtoken, user.username)
            Ok(Json.obj("status" -> "OK", "token" -> newtoken, "username" -> user.username))
              .withCookies(Cookie(AuthTokenCookieKey, newtoken, None, httpOnly = false))
          }


        }
      })
  }

  /**
    * should be really used function only except user really wants to delete own account
    *
    * @param username
    * @return
    */
  def deleteUser(username: String) = Action { request =>
    val message = "some auth flow going on here"
    NotImplemented(Json.obj("status" -> "NA", "message" -> message))
  }
}
