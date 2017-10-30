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
import controllers.security.{AuthTokenCookieKey, UserAgentHeader, UserAgentHeaderDefault}
import models.ErrorResult
import models.users._
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Writes._
import play.api.libs.json._
import play.api.libs.ws._
import play.api.mvc._
import services.UserService
import utils.{ClassnameLogger, PasswordHashing}

import scala.concurrent.duration._

/**
  * HomeController, maybe rename? Provides login and logout
  *
  * @param configuration
  * @param userService
  * @param ws
  */
@Singleton
class HomeController @Inject()(implicit configuration: Configuration,
                               userService: UserService,
                               ws: WSClient) extends Controller with ClassnameLogger {

  lazy private val reCaptchaSecret: String = configuration.getString("google.recaptcha.secret").getOrElse("secret api key")

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
    * the BuildInfo object is generated through sbt-buildinfo plugin, src under target/scala-2.11/src_managed
    * configuration of this in build.sbt buildinfokeys
    *
    * @param fields
    * @return
    */
  def discovery(fields: Option[String]): Action[AnyContent] = Action { request =>
    val appName = utils.BuildInfo.name
    val appVersion = utils.BuildInfo.version
    val buildNumber = utils.BuildInfo.buildNumber
    Ok(Json.obj(
      "appName" -> appName,
      "version" -> s"$appVersion-$buildNumber"
    ))
  }

  /**
    * just to have something on /
    */
  def index: Action[AnyContent] = Action {
    Ok(Json.obj(
      "status" -> "Ok",
      "message" -> "application is ready"
    ))
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
        // authenticate user in db
        userService.authenticateLocal(credentials) match {
          case Left(error) =>
            logger.error(error.message)
            Unauthorized(Json.toJson(error)).as(JSON)

          case Right(user) =>
            val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
            logger.trace(s"Logging in email from $uaIdentifier")
            val token = userService.upsertUserSession(user.email.value, uaIdentifier)
            logger.trace(s"Logging in setting cache $token")
            Ok(Json.obj("status" -> "OK", "token" -> token, "email" -> user.email.value, "userprofile" -> user.asProfileJs))
              .withCookies(Cookie(AuthTokenCookieKey, token, None, httpOnly = false))

          case _ =>
            val error = ErrorResult("Could not validate request.", None)
            logger.error(error.message)
            InternalServerError(Json.toJson(error)).as(JSON)
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
}
