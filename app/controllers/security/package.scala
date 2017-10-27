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
import models.users.UserSession
import play.api.http.MimeTypes
import play.api.libs.json.Json
import play.api.mvc.{ActionRefiner, Request, Results, WrappedRequest}
import services.UserService
import utils.ClassnameLogger

import scala.concurrent.Future

package object security extends ClassnameLogger {

  val AuthTokenCookieKey = "XSRF-TOKEN"
  val AuthTokenHeader = "X-XSRF-TOKEN"
  val AuthTokenUrlKey = "auth"

  val UserAgentHeader = "User-Agent"
  val UserAgentHeaderDefault = "Default-UA/1.0"
  val RefererHeader = "Referer"

  /**
    * the action looks the corresponsind full user object up from the database for an incoming AuthenticatedRequest,
    * if user is found extends the the request to a UserRequest and adds the user object to the request object,
    * for direct securing of a controller function (compose per function as required)
    *
    * @param userService
    * @return
    */
  def userAction(userService: UserService) = new ActionRefiner[AuthenticatedRequest, UserRequest] {
    def refine[A](authenticatedRequest: AuthenticatedRequest[A]) = Future.successful {
      userService.findUserByEmailAsString(authenticatedRequest.userSession.email)
        .map(user => new UserRequest(user, authenticatedRequest))
        .toRight{
          logger.error("User email not found.")
          val error = ErrorResult("User email not found.", None)
          Results.BadRequest(Json.toJson(error)).as(MimeTypes.JSON)
        }
    }
  }

}
