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

import models.ErrorResult
import play.api.Configuration
import play.api.libs.json._
import play.api.mvc._
import services.UserService
import models.users.UserSession
import utils.{ClassnameLogger, PasswordHashing}

import scala.concurrent.Future

/**
  * Security actions that should be used by all controllers that need to protect their actions.
  * Can be composed to fine-tune access control.
  *
  * from https://github.com/mariussoutier/play-angular-require-seed/blob/master/app/controllers/Security.scala
  */
trait Security extends ClassnameLogger {
  self: Controller =>

  val userService: UserService
  val configuration: Configuration
  val passwordHashing: PasswordHashing

  def userAction(userService: UserService) = new ActionRefiner[AuthenticatedRequest, UserRequest] {
    def refine[A](authenticatedRequest: AuthenticatedRequest[A]) = Future.successful {
      userService.findUserByEmailAsString(authenticatedRequest.userSession.email)
        .map(user => new UserRequest(user, authenticatedRequest))
        .toRight{
          logger.error("User email not found.")
          val error = ErrorResult("User email not found.", None)
          Results.BadRequest(Json.toJson(error)).as(JSON)
        }
    }
  }

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
        logger.trace(s"cookie ${xsrfTokenCookie.value}")
        val maybeToken = request.headers.get(AuthTokenHeader).orElse(request.getQueryString(AuthTokenUrlKey))

        maybeToken flatMap { headerToken =>
          // ua needed to differentiate between different devices/sessions
          val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
          logger.trace(s"headerToken: $headerToken")
          logger.trace(s"ua: $uaIdentifier")
          // cache token -> maps to a String email
          // val cacheOpt: Option[String] = cache.get[String](headerToken)
          val cacheOpt: Option[String] = userService.findUserSessionByToken(
            headerToken, xsrfTokenCookie.value, uaIdentifier)
          val result = cacheOpt.fold {
            Unauthorized(Json.obj("status" -> "ERR", "message" -> "No server-side session"))
              .discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
          } {
            email =>
              val cookieForUSerAndDevice = passwordHashing.testSessionCookie(headerToken, email, uaIdentifier)
              logger.trace(s"testcookie: $cookieForUSerAndDevice")
              if (xsrfTokenCookie.value == headerToken && cookieForUSerAndDevice) {
                logger.trace(s"request for active session: $email / $headerToken / $uaIdentifier")
                f(headerToken)(email)(request)
              }
              else {
                Unauthorized(Json.obj("status" -> "ERR", "message" -> "Invalid Token"))
                  .discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
              }
          }
          Some(result)

        } getOrElse Unauthorized(Json.obj("status" -> "ERR", "message" -> "No token header found"))
          .discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
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
  def HasOptionalToken[A](p: BodyParser[A] = parse.anyContent)(
    f: Option[String] => Request[A] => Result): Action[A] =
    Action(p) { implicit request =>

      request.cookies.get(AuthTokenCookieKey).fold {
        // Unauthorized(Json.obj("status" -> "ERR", "message" -> "Invalid XSRF Token cookie"))
        logger.trace("optional cookie: Invalid XSRF-Token cookie")
        f(None)(request)
      } { xsrfTokenCookie =>
        logger.trace(s"cookie ${xsrfTokenCookie.value}")
        val maybeToken = request.headers.get(AuthTokenHeader).orElse(request.getQueryString(AuthTokenUrlKey))

        maybeToken flatMap { headerToken =>
          // ua needed to differentiate between different devices/sessions
          val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
          logger.trace(s"headerToken: $headerToken")
          logger.trace(s"ua: $uaIdentifier")
          // cache token -> maps to a String email
          // val cacheOpt: Option[String] = cache.get[String](headerToken)
          val cacheOpt: Option[String] = userService.findUserSessionByToken(
            headerToken, xsrfTokenCookie.value, uaIdentifier)
          val result = cacheOpt.fold {
            logger.trace("optional cookie: No server-side session")
            f(None)(request)
          } { email =>
            // lazy val passwordHashing = new PasswordHashing(configuration)
            val cookieForUSerAndDevice = passwordHashing.testSessionCookie(headerToken, email, uaIdentifier)
            logger.trace(s"testcookie: $cookieForUSerAndDevice")
            if (xsrfTokenCookie.value == headerToken && cookieForUSerAndDevice) {
              logger.trace(s"request for active session: $email / $headerToken / $uaIdentifier")
              f(Some(email))(request)
            }
            else {
              // Unauthorized(Json.obj("status" -> "ERR", "message" -> "Invalid Token"))
              logger.trace("optional cookie: Invalid Token")
              f(None)(request)
            }
          }
          Some(result)
        } getOrElse {
          // Unauthorized(Json.obj("status" -> "ERR", "message" -> "No Token"))
          logger.trace("optional cookie: No token header found")
          f(None)(request)
        }
      }
    }


  /**
    * HasToken for asynchronous actions
    *
    * @param p
    * @param f
    * @tparam A
    * @return
    */
  //TODO SR how to use HasToken here and not copy paste code?
  def HasTokenAsync[A](p: BodyParser[A] = parse.anyContent)
                      (f: String => String => Request[A] => Future[Result]): Action[A] =
    Action.async(p) { implicit request => {
      import scala.concurrent.ExecutionContext.Implicits.global;

      request.cookies.get(AuthTokenCookieKey).fold {
        logger.error(request.cookies.toString)
        Future[Result] {
          Unauthorized(Json.obj("status" -> "ERR", "message" -> "Invalid XSRF Token cookie"))
        }
      } { xsrfTokenCookie =>
        logger.trace(s"cookie ${xsrfTokenCookie.value}")
        val maybeToken = request.headers.get(AuthTokenHeader).orElse(request.getQueryString(AuthTokenUrlKey))

        maybeToken flatMap { token =>
          // ua needed to differentiate between different devices/sessions
          val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
          logger.trace(s"token: $token")
          logger.trace(s"ua: $uaIdentifier")
          // cache token -> maps to a String email
          // cache.get[String](token) map {
          userService.findUserSessionByToken(
            token, xsrfTokenCookie.value, uaIdentifier) map {
            email =>
            // lazy val passwordHashing = new PasswordHashing(configuration)
            val cookieForUSerAndDevice = passwordHashing.testSessionCookie(token, email, uaIdentifier)
            logger.trace(s"testcookie: $cookieForUSerAndDevice")
            if (xsrfTokenCookie.value == token && cookieForUSerAndDevice) {
              logger.trace(s"request for active session: $email / $token / $uaIdentifier")
              f(token)(email)(request)
            }
            else {
              Future[Result] {
                Unauthorized(Json.obj("status" -> "ERR", "message" -> "Invalid Token"))
                  .discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
              }
            }
          }
        } getOrElse {
          Future[Result] {
            Unauthorized(Json.obj("status" -> "ERR", "message" -> "No Token"))
              .discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
          }
        }
      }
    }
    }


  def HasOptionalTokenAsync[A](p: BodyParser[A] = parse.anyContent)
                              (f: Option[String] => Request[A] => Future[Result]): Action[A] =
    Action.async(p) { implicit request => {

      request.cookies.get(AuthTokenCookieKey).fold {
        logger.error(request.cookies.toString)

        logger.trace("optional cookie: Invalid XSRF-Token cookie")
        f(None)(request)

      } { xsrfTokenCookie =>
        logger.trace(s"cookie ${xsrfTokenCookie.value}")
        val maybeToken = request.headers.get(AuthTokenHeader).orElse(request.getQueryString(AuthTokenUrlKey))

        maybeToken flatMap { token =>
          // ua needed to differentiate between different devices/sessions
          val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
          logger.trace(s"token: $token")
          logger.trace(s"ua: $uaIdentifier")
          // cache token -> maps to a String email
          // cache.get[String](token) map {
          userService.findUserSessionByToken(
            token, xsrfTokenCookie.value, uaIdentifier) map {
            email =>
            // lazy val passwordHashing = new PasswordHashing(configuration)
            val cookieForUSerAndDevice = passwordHashing.testSessionCookie(token, email, uaIdentifier)
            logger.trace(s"testcookie: $cookieForUSerAndDevice")
            if (xsrfTokenCookie.value == token && cookieForUSerAndDevice) {
              logger.trace(s"request for active session: $email / $token / $uaIdentifier")
              f(Some(email))(request)
            }
            else {
              logger.trace("optional cookie: Invalid Token")
              f(None)(request)
            }
          }
        } getOrElse {
          logger.trace("optional cookie: No token header found")
          f(None)(request)
        }
      }
    }
    }
}
