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

import play.api.{Configuration, Logger}
import play.api.cache.CacheApi
import play.api.libs.json._
import play.api.mvc._
import utils.PasswordHashing

/**
  * Security actions that should be used by all controllers that need to protect their actions.
  * Can be composed to fine-tune access control.
  *
  * from https://github.com/mariussoutier/play-angular-require-seed/blob/master/app/controllers/Security.scala
  */
trait Security { self: Controller =>

  val cache: CacheApi
  val configuration: Configuration
  val passwordHashing: PasswordHashing

  val AuthTokenCookieKey = "XSRF-TOKEN"
  val AuthTokenHeader = "X-XSRF-TOKEN"
  val AuthTokenUrlKey = "auth"

  val UserAgentHeader = "User-Agent"
  val UserAgentHeaderDefault = "Default-UA/1.0"

  /**
    * Checks that the token is:
    * - present in the cookie header of the request,
    * - either in the header or in the query string,
    * - matches a token already stored in the play cache
    *
    * @param p
    * @param f
    * @tparam A
    * @return
    */
  def HasToken[A](p: BodyParser[A] = parse.anyContent)(
    f: String => String => Request[A] => Result): Action[A] =
  Action(p) { implicit request =>

    request.cookies.get(AuthTokenCookieKey).fold {
      Unauthorized(Json.obj("status" -> "ERR", "message" -> "Invalid XSRF Token cookie"))
    } { xsrfTokenCookie =>
      Logger.trace(s"cookie ${xsrfTokenCookie.value}")
      val maybeToken = request.headers.get(AuthTokenHeader).orElse(request.getQueryString(AuthTokenUrlKey))

      maybeToken flatMap { token =>
        // ua needed to differentiate between different devices/sessions
        val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
        Logger.trace(s"token: $token")
        Logger.trace(s"ua: $uaIdentifier")
        // cache token -> maps to a String username
        cache.get[String](token) map { username =>
          // lazy val passwordHashing = new PasswordHashing(configuration)
          val cookieForUSerAndDevice = passwordHashing.testSessionCookie(token, username, uaIdentifier)
          Logger.trace(s"testcookie: $cookieForUSerAndDevice")
          if (xsrfTokenCookie.value == token && cookieForUSerAndDevice) {
            Logger.trace(s"request for active session: $username / $token / $uaIdentifier")
            f(token)(username)(request)
          } else {
            Unauthorized(Json.obj("status" -> "ERR", "message" -> "Invalid Token"))
          }
        }
      } getOrElse Unauthorized(Json.obj("status" -> "ERR", "message" -> "No Token"))
    }
  }

  /**
    * Checks IF the token is:
    * - present in the cookie header of the request,
    * - either in the header or in the query string,
    * - matches a token already stored in the play cache
    * - WILL ALLOW also not auth authenticated ANONYMOUS user too
    *
    * @param p
    * @param f
    * @tparam A
    * @return
    */
  /*def HasOptionalToken[A](p: BodyParser[A] = parse.anyContent)(
    f: Option[String] => Request[A] => Result): Action[A] =
    Action(p) { implicit request =>

      request.cookies.get(AuthTokenCookieKey).fold {
        // Unauthorized(Json.obj("status" -> "ERR", "message" -> "Invalid XSRF Token cookie"))
        Logger.trace(s"optional cookie: Invalid XSRF-Token cookie")
        f(None)(request)
      } { xsrfTokenCookie =>
        Logger.trace(s"cookie ${xsrfTokenCookie.value}")
        val maybeToken = request.headers.get(AuthTokenHeader).orElse(request.getQueryString(AuthTokenUrlKey))

        maybeToken flatMap { token =>
          // ua needed to differentiate between different devices/sessions
          val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
          Logger.trace(s"token: $token")
          Logger.trace(s"ua: $uaIdentifier")
          // cache token -> maps to a String username
          cache.get[String](token) map { username =>
            // lazy val passwordHashing = new PasswordHashing(configuration)
            val cookieForUSerAndDevice = passwordHashing.testSessionCookie(token, username, uaIdentifier)
            Logger.trace(s"testcookie: $cookieForUSerAndDevice")
            if (xsrfTokenCookie.value == token && cookieForUSerAndDevice) {
              Logger.trace(s"request for active session: $username / $token / $uaIdentifier")
              f(Some(username))(request)
            } else {
              // Unauthorized(Json.obj("status" -> "ERR", "message" -> "Invalid Token"))
              Logger.trace(s"optional cookie: Invalid Token")
              f(None)(request)
            }
          }
        } getOrElse {
          // Unauthorized(Json.obj("status" -> "ERR", "message" -> "No Token"))
          Logger.trace(s"optional cookie: No Token")
          f(None)(request)
        }

      }
    }*/
}
