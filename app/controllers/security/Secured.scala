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

import models.users.{User, UserSession}
import play.api.mvc._
import utils.ClassnameLogger

/**
  * base extension of Request object, adds a userSession to the request for the controller to use immediately
  *
  * @param userSession
  * @param request
  * @tparam A
  */
class AuthenticatedRequest[A](val userSession: UserSession, val request: Request[A])
  extends WrappedRequest[A](request)

/**
  * base extension of Request object, adds an optional userSession to the request (our former HasOptionalToken thing)
  *
  * @param optionalSession
  * @param request
  * @tparam A
  */
class OptionalAuthenticatedRequest[A](val optionalSession: Option[UserSession], val request: Request[A])
  extends WrappedRequest[A](request)

/**
  * 2nd level extension of Request object, adds the user obecjt to the request for the controller to use immediately,
  * is based on the AuthenticatedRequest, so only if theres a valid session
  *
  * @param user
  * @param authenticatedRequest
  * @tparam A
  */
class UserRequest[A](val user: User, val authenticatedRequest: AuthenticatedRequest[A])
  extends WrappedRequest[A](authenticatedRequest)

/**
  * Security actions that should be used by all controllers that need to protect their actions.
  * Can be composed to fine-tune access control.
  */
trait Secured extends ClassnameLogger {

}
