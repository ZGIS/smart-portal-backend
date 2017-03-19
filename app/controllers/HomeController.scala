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

import models.ErrorResult
import models.users._
import play.api.Configuration
import play.api.cache._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Writes._
import play.api.libs.json._
import play.api.libs.ws._
import play.api.mvc._
import utils.{ClassnameLogger, PasswordHashing}

import scala.concurrent.duration._

/**
  * HomeController, maybe rename? Provides login and logout
  *
  * @param config
  * @param cacheApi
  * @param passwordHashing
  */
@Singleton
class HomeController @Inject()(config: Configuration,
                               cacheApi: CacheApi,
                               override val passwordHashing: PasswordHashing,
                               userDAO: UserDAO,
                               ws: WSClient) extends Controller with Security with ClassnameLogger {

  lazy private val reCaptchaSecret: String =
    configuration.getString("google.recaptcha.secret").getOrElse("secret api key")

  val cache: play.api.cache.CacheApi = cacheApi
  val configuration: play.api.Configuration = config
  val recaptcaVerifyUrl = "https://www.google.com/recaptcha/api/siteverify"

  /**
    * CORS needs preflight OPTION
    *
    * @param all
    * @return
    */
  def preflight(all: String): Action[AnyContent] = Action { request =>
    NoContent.withHeaders(headers: _*)
  }

  /**
    * to handle CORS and HttpSecurity headers, maybe that is already managed through the Filters?!
    *
    * @return
    */
  def headers: List[(String, String)] = List(
    "Access-Control-Allow-Origin" -> "*",
    "Allow" -> "*",
    "Access-Control-Allow-Methods" -> "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers" ->
      "Origin, X-Requested-With, Content-Type, Accept, Referer, User-Agent, Authorization, X-XSRF-TOKEN, Cache-Control, Pragma, Date",
    "Access-Control-Allow-Credentials" -> "true"
  )

  /**
    * idea is to provide a listing of api usable endpoints with some parameter description,selectable by the fields param
    *
    * @param fields
    * @return
    */
  def discovery(fields: Option[String]): Action[AnyContent] = Action { request =>
    Ok(Json.obj("status" -> "OK", "POST" -> "/api/v1/users/register"))
  }

  /**
    * Create an Action to render an HTML page with a welcome message.
    * The configuration in the `routes` file means that this method
    * will be called when the application receives a `GET` request with
    * a path of `/`.
    */
  def index: Action[AnyContent] = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  /**
    * login with JSON (from Angular / form)
    * {"email":"akmoch","password":"testpass123"}
    *
    * Create the authentication token and sets the cookie [[AuthTokenCookieKey]]
    * for AngularJS to use also in X-XSRF-TOKEN in HTTP header.
    *
    * @return
    */
  def login: Action[JsValue] = Action(parse.json) { implicit request =>
    request.body.validate[LoginCredentials].fold(
      errors => {
        logger.error(JsError.toJson(errors).toString())
        val error = ErrorResult("Could not validate request.", Some(JsError.toJson(errors).toString()))
        BadRequest(Json.toJson(error)).as(JSON)
      },
      valid = credentials => {
        // find user in db and compare password stuff
        userDAO.authenticate(credentials.email, credentials.password).fold {
          logger.error("User email or password wrong.")
          val error = ErrorResult("User email or password wrong.", None)
          Unauthorized(Json.toJson(error)).as(JSON)
        } { user =>
          val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
          // logger.debug(s"Logging in email from $uaIdentifier")
          val token = passwordHashing.createSessionCookie(user.email, uaIdentifier)
          cache.set(token, user.email)
          Ok(Json.obj("status" -> "OK", "token" -> token, "email" -> user.email, "userprofile" -> user.asProfileJs()))
            .withCookies(Cookie(AuthTokenCookieKey, token, None, httpOnly = false))
        }
      })
  }

  /**
    * https://developers.google.com/recaptcha/docs/verify
    *
    * @param recaptcaChallenge
    * @return
    */
  def recaptchaValidate(recaptcaChallenge: String): Action[AnyContent] = Action.async { implicit request =>
    // TODO check from where URL referer comes from
    ws.url(recaptcaVerifyUrl)
      .withHeaders("Accept" -> "application/json")
      .withRequestTimeout(10000.millis)
      .withQueryString("secret" -> reCaptchaSecret,
        "response" -> recaptcaChallenge).get().map {
      response =>
        val success = (response.json \ "success").as[Boolean]
        if (success) {
          Ok(Json.obj("status" -> "OK", "message" -> "granted", "success" -> JsBoolean(true)))
        }
        else {
          val errors = (response.json \ "error-codes")
          val jsErrors = errors.getOrElse(JsString("No further errors"))
          val error = ErrorResult("User email or password wrong.", Some(jsErrors.toString()))
          BadRequest(Json.toJson(error)).as(JSON)
        }
    }.recover {
      case e: Exception =>
        val error = ErrorResult("Exception on validating captcha.", Some(e.getMessage))
        InternalServerError(Json.toJson(error)).as(JSON)
    }
  }

  /**
    * Log-out a user. Invalidates the authentication token.
    *
    * Discard the cookie [[AuthTokenCookieKey]] to have AngularJS no longer set the
    * X-XSRF-TOKEN in HTTP header.
    */
  def logout: Action[Unit] = HasToken(parse.empty) { token =>
    email =>
      implicit request =>
        cache.remove(token)
        Ok.discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
  }
}
