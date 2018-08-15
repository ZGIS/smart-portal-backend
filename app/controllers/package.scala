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
import play.api.libs.json._
import uk.gov.hmrc.emailaddress.{EmailAddress, PlayJsonFormats}

package object controllers {

  implicit val emailAddressReads: Reads[EmailAddress] = PlayJsonFormats.emailAddressReads
  implicit val emailAddressWrites: Writes[EmailAddress] = PlayJsonFormats.emailAddressWrites

  implicit val LoginCredentialsFromJsonReads: Reads[LoginCredentials] = (
    (JsPath \ "email").read[EmailAddress](emailAddressReads) and
      (JsPath \ "password").read[String](minLength[String](8))) (LoginCredentials.apply _)

  implicit val passwordUpdateCredentialsJsReads: Reads[PasswordUpdateCredentials] = (
    (JsPath \ "email").read[EmailAddress](emailAddressReads) and
      (JsPath \ "oldpassword").read[String](minLength[String](8)) and
      (JsPath \ "newpassword").read[String](minLength[String](8))
    ) (PasswordUpdateCredentials.apply _).filterNot(p => p.oldPassword.equalsIgnoreCase(p.newPassword))

  implicit val GAuthCredentialsFromJsonReads: Reads[GAuthCredentials] = (
    (JsPath \ "authcode").read[String] and
      (JsPath \ "accesstype").read[String]
    ) (GAuthCredentials.apply _).filter(p =>
    p.accesstype.equalsIgnoreCase("LOGIN") || p.accesstype.equalsIgnoreCase("REGISTER"))

  implicit val registerJsReads: Reads[RegisterJs] = (
    (JsPath \ "email").read[EmailAddress](emailAddressReads) and
      (JsPath \ "accountSubject").read[String] and
      (JsPath \ "firstname").read[String] and
      (JsPath \ "lastname").read[String] and
      (JsPath \ "password").read[String](minLength[String](8))) (RegisterJs.apply _)

  implicit val userReads: Reads[User] = (
    (JsPath \ "email").read[EmailAddress](emailAddressReads) and
      (JsPath \ "accountSubject").read[String] and
      (JsPath \ "firstname").read[String] and
      (JsPath \ "lastname").read[String] and
      (JsPath \ "password").read[String](minLength[String](8)) and
      (JsPath \ "laststatustoken").read[String] and
      (JsPath \ "laststatuschange").read[ZonedDateTime]) (User.apply _)

  implicit val userWrites: Writes[User] = Json.writes[User]

  //  implicit val profileJsReads: Reads[ProfileJs] = (
  //    (JsPath \ "email").read[EmailAddress](emailAddressReads) and
  //      (JsPath \ "accountSubject").read[String] and
  //      (JsPath \ "firstname").read[String] and
  //      (JsPath \ "lastname").read[String]) ((email: EmailAddress, accountSubject: String, firstname: String, lastname: String) => ProfileJs.apply(email, accountSubject, firstname, lastname))
  implicit val profileJsReads: Reads[ProfileJs] = (
    (JsPath \ "email").read[EmailAddress](emailAddressReads) and
      (JsPath \ "accountSubject").read[String] and
      (JsPath \ "firstname").read[String] and
      (JsPath \ "lastname").read[String] and
      (JsPath \ "laststatustoken").readNullable[String] and
      (JsPath \ "laststatuschange").readNullable[ZonedDateTime]) (ProfileJs.apply _)

  implicit val profileJsWrites: Writes[ProfileJs] = Json.writes[ProfileJs]

  implicit val userFilesFormat: Format[UserFile] = Json.format[UserFile]
  implicit val userMetaRecordsFormat: Format[UserMetaRecord] = Json.format[UserMetaRecord]

  implicit val userLinkLoggingsFormat: Format[UserLinkLogging] = Json.format[UserLinkLogging]
  implicit val userSessionsFormat: Format[UserSession] = Json.format[UserSession]

  implicit val userGroupContextsVisibilityFormat: Format[UserGroupContextsVisibility] = Json.format[UserGroupContextsVisibility]
  implicit val userGroupUsersLevelFormat: Format[UserGroupUsersLevel] = Json.format[UserGroupUsersLevel]
  implicit val userGroupFormat: Format[UserGroup] = Json.format[UserGroup]

  implicit val owcContextsRightsMatrixFormat: Format[OwcContextsRightsMatrix] = Json.format[OwcContextsRightsMatrix]

}
