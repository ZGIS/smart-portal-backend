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

import models.{User, UserDAO}
import play.api.cache.CacheApi
import play.api.{Configuration, Logger}
import play.api.libs.json.{JsError, Json}
import play.api.mvc.{Action, BodyParsers, Controller}
import services.EmailService
import utils.ClassnameLogger

/**
  *
  * @param email
  * @param username
  * @param firstname
  * @param lastname
  * @param password
  */
case class ProfileJs(email: String,
                     username: String,
                     firstname: String,
                     lastname: String,
                     password: Option[String])


/**
  *
  * @param config
  * @param cacheApi
  * @param emailService
  */
@Singleton
class UserController @Inject()(config: Configuration, cacheApi: CacheApi, emailService: EmailService, userDAO: UserDAO) extends Controller with ClassnameLogger with Security {

  val cache: play.api.cache.CacheApi = cacheApi
  val configuration: play.api.Configuration = config

  lazy private val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")

  def registerUser = Action(parse.json) { request =>

    val profileResult = request.body.validate[ProfileJs]

    profileResult.fold(
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
            val emailWentOut = emailService.sendRegistrationEmail(jsuser.email, "Please confirm your GW HUB account", jsuser.firstname, regLinkId)

            val newUser = User(jsuser.email,
              jsuser.username,
              jsuser.firstname,
              jsuser.lastname,
              jsuser.password.getOrElse("DEAD_DEFAULT_PASS"),
              s"REGISTERED:$regLinkId",
              ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

            userDAO.createWithNps(newUser).fold {
              logger.error("User create error.")
              BadRequest(Json.obj("status" -> "ERR", "message" -> "User create error."))
            } { user =>
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

  def registerConfirm(linkId: String) = Action { request =>
    // check db laststatustoken, get user etc, update status
    // val emailWentOut = emailService.sendConfirmationEmail(user.email, "Your GW HUB account is now active", user.firstname)
    Redirect("/#/account")
  }

  def authUser = HasToken(parse.empty) {
    token =>
      cachedSecUser => implicit request =>

        userDAO.findByUsername(cachedSecUser).fold {
          logger.error("User not found.")
          BadRequest(Json.obj("status" -> "ERR", "message" -> "Username not found."))
        } { user =>
          Ok(Json.toJson(user.asProfileJs()))
        }
  }

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
            BadRequest(Json.obj("status" -> "ERR", "message" -> "Username Security Token mismatch."))
          }
        }
  }

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
            } { user =>
              // is it really you? by the way you shouldn't / can't (?!) change your username!!!
              if (user.username.equals(jsuser.username) && cachedSecUser.equals(jsuser.username)) {

                // check if new email is used already by another user than yourself
                userDAO.findUserByEmail(jsuser.username).fold {
                  // found none, good
                  val updateUser = User(jsuser.email,
                    user.username,
                    jsuser.firstname,
                    jsuser.lastname,
                    "***",
                    user.laststatustoken,
                    user.laststatuschange)
                  userDAO.updateNoPass(updateUser).fold {
                    logger.error("User update error.")
                    BadRequest(Json.obj("status" -> "ERR", "message" -> "User update error."))
                  } { user =>
                    Ok(Json.toJson(user.asProfileJs()))
                  }
                } { foundEmailUser =>
                  // found one, is it yourself or somebody else?
                  if (foundEmailUser.username.equals(jsuser.username) && cachedSecUser.equals(jsuser.username)) {
                    // it is you, so we can update you
                    val updateUser = User(jsuser.email,
                      foundEmailUser.username,
                      jsuser.firstname,
                      jsuser.lastname,
                      "***",
                      foundEmailUser.laststatustoken,
                      foundEmailUser.laststatuschange)
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
                BadRequest(Json.obj("status" -> "ERR", "message" -> "Username Security Token mismatch."))
              }
            }

          })

  }

  def deleteUser(username: String) = Action { request =>
    val message = "some auth flow going on here"
    NotImplemented(Json.obj("status" -> "NA", "message" -> message))
  }
}
