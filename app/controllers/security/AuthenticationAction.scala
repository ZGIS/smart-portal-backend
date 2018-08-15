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

package controllers.security

import javax.inject.{Inject, Singleton}
import models.ErrorResult
import play.api.http.MimeTypes
import play.api.libs.json._
import play.api.mvc.{ActionRefiner, _}
import services.{AdminService, UserService}
import utils.ClassnameLogger

import scala.concurrent.Future

/**
  * Use [[ActionBuilder]] when you want to add before/after logic in your actions.
  * Use [[ActionRefiner]] when you want to add custom information to a request under some conditions.
  * Use [[ActionTransformer]] when you want to always add custom information to a request.
  * Use [[ActionFilter]] when you want to filter requests under some condition and immediately return a result.
  * Always mix [[ActionRefiner]], [[ActionTransformer]] and [[ActionFilter]] to [[ActionBuilder]] so
  * you can use factory methods to easily construct your actions.
  * Use the andThen combinator to compose multiple ActionFunctions together.
  */

/**
  * the action checks if the incoming requets is for a valid server-side session (our original HasToken),
  * right for an incoming UserRequest, for direct securing of a controller function (compose per function as required)
  *
  * @param userService
  */
@Singleton
class AuthenticationAction @Inject()(val userService: UserService) extends ActionBuilder[AuthenticatedRequest]
  with ActionRefiner[Request, AuthenticatedRequest] with ClassnameLogger {

  def refine[A](request: Request[A]): Future[Either[Result, AuthenticatedRequest[A]]] = {

    val result = request.cookies.get(AuthTokenCookieKey).fold[Either[Result, AuthenticatedRequest[A]]] {
      Left(Results.Unauthorized(Json.obj("status" -> "ERR", "message" -> "Invalid XSRF Token cookie")))
    } { xsrfTokenCookie =>
      logger.trace(s"cookie ${xsrfTokenCookie.value}")
      request.headers.get(AuthTokenHeader).orElse(request.getQueryString(AuthTokenUrlKey)).map {
        headerToken =>
          // ua needed to differentiate between different devices/sessions
          val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
          logger.trace(s"headerToken: $headerToken")
          logger.trace(s"ua: $uaIdentifier")
          userService.getUserSessionByToken(headerToken, xsrfTokenCookie.value, uaIdentifier).fold[Either[Result, AuthenticatedRequest[A]]] {
            Left(Results.Unauthorized(Json.obj("status" -> "ERR", "message" -> "No server-side session"))
              .discardingCookies(DiscardingCookie(name = AuthTokenCookieKey)))
          } {
            userSession =>
              Right(new AuthenticatedRequest[A](userSession, request))
          }

      }.getOrElse(Left(Results.Unauthorized(Json.obj("status" -> "ERR", "message" -> "No token header found"))
        .discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))))
    }
    Future.successful(result)
  }
}

/**
  * the action checks if the incoming requets is for a valid server-side session,
  * adds an optional userSession to the request (our former HasOptionalToken thing)
  *
  * @param userService
  */
@Singleton
class OptionalAuthenticationAction @Inject()(val userService: UserService) extends ActionBuilder[OptionalAuthenticatedRequest]
  with ActionTransformer[Request, OptionalAuthenticatedRequest] with ClassnameLogger {

  def transform[A](request: Request[A]): Future[OptionalAuthenticatedRequest[A]] = {

    val result = request.cookies.get(AuthTokenCookieKey).fold[OptionalAuthenticatedRequest[A]] {
      // Left(Results.Unauthorized(Json.obj("status" -> "ERR", "message" -> "Invalid XSRF Token cookie")))
      new OptionalAuthenticatedRequest[A](None, request)
    } { xsrfTokenCookie =>
      logger.trace(s"cookie ${xsrfTokenCookie.value}")
      request.headers.get(AuthTokenHeader).orElse(request.getQueryString(AuthTokenUrlKey)).map {
        headerToken =>
          // ua needed to differentiate between different devices/sessions
          val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
          logger.trace(s"headerToken: $headerToken")
          logger.trace(s"ua: $uaIdentifier")
          userService.getUserSessionByToken(headerToken, xsrfTokenCookie.value, uaIdentifier).fold[OptionalAuthenticatedRequest[A]] {
            new OptionalAuthenticatedRequest[A](None, request)
          } {
            userSession =>
              new OptionalAuthenticatedRequest[A](Some(userSession), request)
          }

      }.getOrElse(new OptionalAuthenticatedRequest[A](None, request))
    }
    Future.successful(result)
  }
}

/**
  * this is "just" an ActionBuilder, without differentiation to Refiner, Filter or Transformer,
  * supposedly we should always try to build upon the more differentiated Action generators instead of just ActionBuilder
  *
  * @param userService
  */
@Singleton
@deprecated
class AuthenticatedPlainAction @Inject()(val userService: UserService)
  extends ActionBuilder[AuthenticatedRequest] with ClassnameLogger {

  def invokeBlock[A](request: Request[A],
                     block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {

    request.cookies.get(AuthTokenCookieKey).fold {
      Future.successful(Results.Unauthorized(Json.obj("status" -> "ERR", "message" -> "Invalid XSRF Token cookie")))
    } { xsrfTokenCookie =>
      logger.trace(s"cookie ${xsrfTokenCookie.value}")
      request.headers.get(AuthTokenHeader).orElse(request.getQueryString(AuthTokenUrlKey)).map {
        headerToken =>
          // ua needed to differentiate between different devices/sessions
          val uaIdentifier: String = request.headers.get(UserAgentHeader).getOrElse(UserAgentHeaderDefault)
          logger.trace(s"headerToken: $headerToken")
          logger.trace(s"ua: $uaIdentifier")
          userService.getUserSessionByToken(headerToken, xsrfTokenCookie.value, uaIdentifier).fold {
            Future.successful(Results.Unauthorized(Json.obj("status" -> "ERR", "message" -> "No server-side session"))
              .discardingCookies(DiscardingCookie(name = AuthTokenCookieKey)))
          } {
            userSession =>
              block(new AuthenticatedRequest(userSession, request))
          }

      }.getOrElse(Future.successful(Results.Unauthorized(Json.obj("status" -> "ERR", "message" -> "No token header found"))
        .discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))))
    }
  }
}

/**
  * the action looks the corresponsind full user object up from the database for an incoming AuthenticatedRequest,
  * if user is found extends the the request to a UserRequest and adds the user object to the request object,
  * for direct securing of a controller function (compose per function as required)
  *
  * @param userService
  */
@Singleton
class UserAction @Inject()(val userService: UserService) extends ActionRefiner[AuthenticatedRequest, UserRequest] with ClassnameLogger {
  import scala.concurrent.ExecutionContext.Implicits.global

  def refine[A](authenticatedRequest: AuthenticatedRequest[A]) = Future {
    userService.findUserByEmailAsString(authenticatedRequest.userSession.email)
      .map(user => new UserRequest(user, authenticatedRequest))
      .toRight{
        logger.error("User email not found.")
        val error = ErrorResult("User email not found.", None)
        Results.BadRequest(Json.toJson(error)).as(MimeTypes.JSON)
      }
  }
}

/**
  * the action checks admin right for an incoming UserRequest, for direct securing of a controller function (compose per function as required)
  *
  * @param adminService
  */
@Singleton
class AdminPermissionCheckAction @Inject()(val adminService: AdminService) extends ActionFilter[UserRequest] with ClassnameLogger {
  // import scala.concurrent.ExecutionContext.Implicits.global

  def filter[A](input: UserRequest[A]) = Future.successful {
    if (!adminService.isAdmin(input.user.email)) {
      logger.error("User email not Admin.")
      val error = ErrorResult("User email not Admin.", None)
      Some(Results.Forbidden(Json.toJson(error)))
    } else {
      None
    }
  }
}
