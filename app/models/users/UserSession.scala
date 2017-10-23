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

package models.users

import java.time.ZonedDateTime

import utils.ClassnameLogger

/*
CREATE TABLE sessions (
token varchar(255) NOT NULL,
useragent varchar(255) NOT NULL,
email varchar(255) NOT NULL,
accountsubject varchar(255) NOT NULL,
laststatustoken varchar(255) NOT NULL,
laststatuschange TIMESTAMP WITH TIME ZONE NOT NULL,
PRIMARY KEY (token)
);
*/

/**
  * we move the sessions into DB
  *
  * @param token the security token for an active session
  * @param useragent informative, user for hashing and separating logins from different devices
  * @param email
  * @param accountsubject
  * @param laststatustoken session active/expire/deactivate
  * @param laststatuschange
  */
case class UserSession(token: String,
                       useragent: String,
                       email: String,
                       accountsubject: String,
                       laststatustoken: String,
                       laststatuschange: ZonedDateTime) extends ClassnameLogger
