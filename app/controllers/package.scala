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

import models.users.{PasswordUpdateCredentials, _}
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.libs.json._
import uk.gov.hmrc.emailaddress.{EmailAddress, PlayJsonFormats}

package object controllers {

  val AuthTokenCookieKey = "XSRF-TOKEN"
  val AuthTokenHeader = "X-XSRF-TOKEN"
  val AuthTokenUrlKey = "auth"

  val UserAgentHeader = "User-Agent"
  val UserAgentHeaderDefault = "Default-UA/1.0"
  val RefererHeader = "Referer"

  implicit val LoginCredentialsFromJsonReads: Reads[LoginCredentials] = (
    (JsPath \ "email").read[EmailAddress](PlayJsonFormats.emailAddressReads) and
      (JsPath \ "password").read[String](minLength[String](8))) (LoginCredentials.apply _)

  implicit val passwordUpdateCredentialsJsReads: Reads[PasswordUpdateCredentials] = (
    (JsPath \ "email").read[EmailAddress](PlayJsonFormats.emailAddressReads) and
      (JsPath \ "oldpassword").read[String](minLength[String](8)) and
      (JsPath \ "newpassword").read[String](minLength[String](8))
    ) (PasswordUpdateCredentials.apply _).filterNot(p => p.oldPassword.equalsIgnoreCase(p.newPassword))

  implicit val GAuthCredentialsFromJsonReads: Reads[GAuthCredentials] = (
    (JsPath \ "authcode").read[String] and
      (JsPath \ "accesstype").read[String]
    ) (GAuthCredentials.apply _).filter(p =>
      p.accesstype.equalsIgnoreCase("LOGIN") || p.accesstype.equalsIgnoreCase("REGISTER"))

  implicit val registerJsReads: Reads[RegisterJs] = (
    (JsPath \ "email").read[EmailAddress](PlayJsonFormats.emailAddressReads) and
      (JsPath \ "accountSubject").read[String] and
      (JsPath \ "firstname").read[String] and
      (JsPath \ "lastname").read[String] and
      (JsPath \ "password").read[String](minLength[String](8))) (RegisterJs.apply _)

  implicit val userReads: Reads[User] = (
    (JsPath \ "email").read[EmailAddress](PlayJsonFormats.emailAddressReads) and
      (JsPath \ "accountSubject").read[String] and
      (JsPath \ "firstname").read[String] and
      (JsPath \ "lastname").read[String] and
      (JsPath \ "password").read[String](minLength[String](8)) and
      (JsPath \ "laststatustoken").read[String] and
      (JsPath \ "laststatuschange").read[ZonedDateTime]) (User.apply _)

  implicit val userWrites: Writes[User] = (
    (JsPath \ "email").write[EmailAddress](PlayJsonFormats.emailAddressWrites) and
      (JsPath \ "accountSubject").write[String] and
      (JsPath \ "firstname").write[String] and
      (JsPath \ "lastname").write[String] and
      (JsPath \ "password").write[String] and
      (JsPath \ "laststatustoken").write[String] and
      (JsPath \ "laststatuschange").write[ZonedDateTime]) (unlift(User.unapply))

  implicit val profileJsReads: Reads[ProfileJs] = (
    (JsPath \ "email").read[EmailAddress](PlayJsonFormats.emailAddressReads) and
      (JsPath \ "accountSubject").read[String] and
      (JsPath \ "firstname").read[String] and
      (JsPath \ "lastname").read[String]) (ProfileJs.apply _)

  implicit val profileJsWrites: Writes[ProfileJs] = (
    (JsPath \ "email").write[EmailAddress](PlayJsonFormats.emailAddressWrites) and
      (JsPath \ "accountSubject").write[String] and
      (JsPath \ "firstname").write[String] and
      (JsPath \ "lastname").write[String]) (unlift(ProfileJs.unapply))

  implicit val userFilesFormat: Format[UserFile] = Json.format[UserFile]
  implicit val userMetaRecordsFormat: Format[UserMetaRecord] = Json.format[UserMetaRecord]

  implicit val userLinkLoggingsFormat: Format[UserLinkLogging] = Json.format[UserLinkLogging]
  implicit val userSessionsFormat: Format[UserSession] = Json.format[UserSession]

  implicit val userGroupContextsVisibilityFormat: Format[UserGroupContextsVisibility] = Json.format[UserGroupContextsVisibility]
  implicit val userGroupUsersLevelFormat: Format[UserGroupUsersLevel] = Json.format[UserGroupUsersLevel]
  implicit val userGroupFormat: Format[UserGroup] = Json.format[UserGroup]

}
