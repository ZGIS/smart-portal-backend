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
import javax.inject._

import com.google.api.client.googleapis.auth.oauth2._
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import models.users._
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.{JsError, Json}
import play.api.mvc.{Action, Controller, Cookie, DiscardingCookie}
import services.{EmailService, OwcCollectionsService}
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
                     lastname: String)

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
  * @param collectionsService
  * @param userDAO
  * @param passwordHashing
  */
@Singleton
class UserController @Inject()(config: Configuration,
                               cacheApi: CacheApi,
                               emailService: EmailService,
                               collectionsService: OwcCollectionsService,
                               userDAO: UserDAO,
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
              val emailWentOut = emailService.sendRegistrationEmail(jsuser.email, "Please confirm your GW HUB account", jsuser.firstname, regLinkId)
              logger.info(s"New user registered. Email went out $emailWentOut")
              // creating default collection only after registration, account is only barely usable here
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
        } { user =>
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
            // all good here, creating default collection (Unit)
            collectionsService.createUserDefaultCollection(user)
            // maybe should also do the full login thing here? how does that relate with the alternative Google Oauth thing
            logger.info(s"Registered user ${user.username} confirmed email ${user.email}. Email went out $emailWentOut")
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
      cachedSecUser =>
        implicit request =>

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
      cachedSecUser =>
        implicit request =>

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
    * TODO should we remove username param or add additional check with HasToken?
    *
    * @param username
    * @return
    */
  def updateProfile(username: String) = HasToken(parse.json) {
    token =>
      cachedSecUser =>
        implicit request =>

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
      cachedSecUser =>
        implicit request =>

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
    * TODO should we remove username param or add additional check with HasToken?
    *
    * @param username
    * @return
    */
  def deleteUser(username: String) = Action { request =>
    val message = "some auth flow going on here"
    NotImplemented(Json.obj("status" -> "NA", "message" -> message))
  }

  /**
    * part of OAuth2 Flow for Google Login in
    *
    * @return
    */
  def gconnect = Action(parse.json) { implicit request =>
    request.body.validate[GAuthCredentials].fold(
      errors => {
        logger.error(JsError.toJson(errors).toString())
        BadRequest(Json.obj("status" -> "ERR", "message" -> JsError.toJson(errors)))
      },
      valid = credentials => {
        // find user in db and compare password stuff

        logger.debug(credentials.accesstype)

        // Set path to the Web application client_secret_*.json file you downloaded from the
        // Google API Console: https://console.developers.google.com/apis/credentials
        // You can also find your Web application client ID and client secret from the
        // console and specify them directly when you create the GoogleAuthorizationCodeTokenRequest
        // object.
        if (!new java.io.File(googleClientSecret).exists) {
          BadRequest(Json.obj("status" -> "ERR", "message" -> "service json file not available"))
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
          logger.debug(s"accessToken: $accessToken")

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
          val name = payload.get("name").asInstanceOf[String]
          val pictureUrl = payload.get("picture").asInstanceOf[String]
          val locale = payload.get("locale").asInstanceOf[String]
          val familyName = payload.get("family_name").asInstanceOf[String]
          val givenName = payload.get("given_name").asInstanceOf[String]

          credentials.accesstype match {
            case "LOGIN" => {
              userDAO.findUserByEmail(email).fold {
                logger.error("User not known.")
                Unauthorized(Json.obj("status" -> "ERR", "message" -> "User not known yet, please register first."))
              } { user =>
                val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
                // logger.debug(s"Logging in username from $uaIdentifier")
                val token = passwordHashing.createSessionCookie(user.email, uaIdentifier)
                cache.set(token, user.email)
                Ok(Json.obj("status" -> "OK", "token" -> token, "username" -> user.email, "userprofile" -> user.asProfileJs()))
                  .withCookies(Cookie(AuthTokenCookieKey, token, None, httpOnly = false))
              }
            }
            case "REGISTER" => {
              userDAO.findByUsername(userId).fold {
                // found none, good, but need to check for email address too
                userDAO.findUserByEmail(email).fold {
                  // found none, good, create user
                  val regLinkId = java.util.UUID.randomUUID().toString()
                  val cryptPass = passwordHashing.createHash(regLinkId)

                  val newUser = User(email,
                    userId,
                    givenName,
                    familyName,
                    cryptPass,
                    s"REGISTERED:$regLinkId",
                    ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)))

                  userDAO.createWithNps(newUser).fold {
                    logger.error("User create error.")
                    BadRequest(Json.obj("status" -> "ERR", "message" -> "User create error."))
                  } { user =>
                    val emailWentOut = emailService.sendRegistrationEmail(email, "Please confirm your GW HUB account", givenName, regLinkId)
                    logger.info(s"New user registered. Email went out $emailWentOut")
                    // creating default collection only after registration, account is only barely usable here
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
            }

            case _ => BadRequest(Json.obj("status" -> "ERR", "message" -> "invalid accesstype requested"))
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
  def gdisconnect = HasToken(parse.empty) {
    token =>
      username =>
        implicit request =>
          cache.remove(token)
          // do some Google OAuth2 unregisteR
          Ok.discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
  }

  /**
    *
    * @return
    */
  def oauth2callback = Action {
    Redirect("/#/account")
  }
}
