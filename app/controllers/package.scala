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

import java.time.ZonedDateTime

import models._
import models.users._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.libs.json._

package object controllers {

  /**
    *  JSON reader for [[models.LoginCredentials]].
    *  github.com/mariussoutier/play-angular-require-seed
    */
  implicit val LoginCredentialsFromJson = (
    (JsPath \ "username").read[String](minLength[String](3)) and
      (JsPath \ "password").read[String](minLength[String](6)))((username, password) => LoginCredentials(username, password))

  implicit val registerJsReads: Reads[RegisterJs] = (
    (JsPath \ "email").read[String](email) and
      (JsPath \ "username").read[String](minLength[String](3)) and
      (JsPath \ "firstname").read[String] and
      (JsPath \ "lastname").read[String] and
      (JsPath \ "password").read[String](minLength[String](8)))(RegisterJs.apply _)

  implicit val userReads: Reads[User] = (
    (JsPath \ "email").read[String](email) and
      (JsPath \ "username").read[String](minLength[String](3)) and
      (JsPath \ "firstname").read[String] and
      (JsPath \ "lastname").read[String] and
      (JsPath \ "password").read[String](minLength[String](8)) and
      (JsPath \ "laststatustoken").read[String] and
      (JsPath \ "laststatuschange").read[ZonedDateTime])(User.apply _)

  implicit val userWrites: Writes[User] = (
    (JsPath \ "email").write[String] and
      (JsPath \ "username").write[String] and
      (JsPath \ "firstname").write[String] and
      (JsPath \ "lastname").write[String] and
      (JsPath \ "password").write[String] and
      (JsPath \ "laststatustoken").write[String] and
      (JsPath \ "laststatuschange").write[ZonedDateTime])(unlift(User.unapply))

  implicit val profileJsReads: Reads[ProfileJs] = (
    (JsPath \ "email").read[String](email) and
      (JsPath \ "username").read[String](minLength[String](3)) and
      (JsPath \ "firstname").read[String] and
      (JsPath \ "lastname").read[String])(ProfileJs.apply _)

  implicit val profileJsWrites: Writes[ProfileJs] = (
    (JsPath \ "email").write[String] and
      (JsPath \ "username").write[String] and
      (JsPath \ "firstname").write[String] and
      (JsPath \ "lastname").write[String])(unlift(ProfileJs.unapply))

}
