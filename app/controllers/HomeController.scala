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

import javax.inject._

import models.UserDAO
import play.api._
import play.api.libs.json.JsPath
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.libs.functional.syntax._
import play.api.mvc._
import utils.{ClassnameLogger, PasswordHashing}
import play.api.Configuration
import play.api.cache._


/**
  * HomeController, maybe rename? Provides login and logout
  *
  * @param config
  * @param cacheApi
  * @param passwordHashing
  */
@Singleton
class HomeController @Inject() (config: Configuration, cacheApi: CacheApi, passwordHashing: PasswordHashing, userDAO: UserDAO) extends Controller with Security with ClassnameLogger {

  val cache: play.api.cache.CacheApi = cacheApi
  val configuration: play.api.Configuration = config

  /**
    * to handle CORS and HttpSecurity headers, maybe that is already managed through the Filters?!
    *
    * @return
    */
  def headers = List(
    "Access-Control-Allow-Origin" -> "*",
    "Allow" -> "*",
    "Access-Control-Allow-Methods" -> "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers" -> "Origin, X-Requested-With, Content-Type, Accept, Referer, User-Agent, Authorization, X-XSRF-TOKEN, Cache-Control, Pragma, Date",
    "Access-Control-Allow-Credentials" -> "true"
  )

  /**
    * CORS needs preflight OPTION
    *
    * @param all
    * @return
    */
  def preflight(all: String) = Action { request =>
    NoContent.withHeaders(headers: _*)
  }

  /**
    * Create an Action to render an HTML page with a welcome message.
    * The configuration in the `routes` file means that this method
    * will be called when the application receives a `GET` request with
    * a path of `/`.
    */
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }


  /**
    *  Used for obtaining the email and password from the HTTP login request
    *  from github.com/mariussoutier/play-angular-require-seed
    */
  case class LoginCredentials(username: String, password: String)

  /**
    *  JSON reader for [[LoginCredentials]].
    *  github.com/mariussoutier/play-angular-require-seed
    */
  implicit val LoginCredentialsFromJson = (
      (JsPath \ "username").read[String](minLength[String](3)) and
      (JsPath \ "password").read[String](minLength[String](6)))((username, password) => LoginCredentials(username, password))

  /**
    * login with JSON (from Angular / form)
    * {"username":"akmoch","password":"testpass123"}
    *
    * Create the authentication token and sets the cookie [[AuthTokenCookieKey]]
    * for AngularJS to use also in X-XSRF-TOKEN in HTTP header.
    *
    * @return
    */
  def login = Action(parse.json) { implicit request =>
    request.body.validate[LoginCredentials].fold(
      errors => {
        logger.error(JsError.toJson(errors).toString())
        BadRequest(Json.obj("status" -> "ERR", "message" -> JsError.toJson(errors)))
      },
      valid = credentials => {
        // find user in db and compare password stuff
        userDAO.authenticate(credentials.username, credentials.password).fold {
          logger.error("User or password wrong.")
          BadRequest(Json.obj("status" -> "ERR", "message" -> "Username or password wrong."))
        } { user =>
          val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
          logger.info(s"Logging in username from $uaIdentifier")
          val token = passwordHashing.createSessionCookie("username", uaIdentifier)
          cache.set(token, user.username)
          Ok(Json.obj("status" -> "OK", "token" -> token, "username" -> "username"))
            .withCookies(Cookie(AuthTokenCookieKey, token, None, httpOnly = false))
        }
      })
  }

  /**
    * Log-out a user. Invalidates the authentication token.
    *
    * Discard the cookie [[AuthTokenCookieKey]] to have AngularJS no longer set the
    * X-XSRF-TOKEN in HTTP header.
    */
  def logout = HasToken(parse.empty) { token =>
    username => implicit request =>
      cache.remove(token)
      Ok.discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
  }

  /**
    * part of OAuth2 Flow for Google Login in
    *
    * @return
    */
  def gconnect = Action { implicit request =>
    val data = request.body.asJson.getOrElse(Json.obj("status" -> "ERR", "message" -> "gconnect went south"))
    logger.debug(data.toString())
    // do lots of Google OAuth2 stuff
    Ok(data)
  }

  /**
    * Disconnect the Google login
    *
    * @return
    */
  def gdisconnect = HasToken(parse.empty) { token =>
    username => implicit request =>
      cache.remove(token)
      // do some Google OAuth2 unregisteR
      Ok.discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
  }
}
