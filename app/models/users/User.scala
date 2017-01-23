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

import controllers.ProfileJs
import utils.ClassnameLogger

/**
  * the user case class, however the UserDAO implements all database
  * functions as it needed Dependency Injection and could not just be
  * the companion object here
  *
  * @param email
  * @param accountSubject
  * @param firstname
  * @param lastname
  * @param password
  * @param laststatustoken
  * @param laststatuschange
  */
case class User(email: String,
                accountSubject: String,
                firstname: String,
                lastname: String,
                password: String,
                laststatustoken: String,
                laststatuschange: ZonedDateTime) extends ClassnameLogger {

  /**
    * toString, should be more explicit with date handling
    *
    * @return
    */
  override def toString: String = {
    f"""User(
        |$email,
        |${accountSubject},
        |${firstname},
        |${lastname},
        |***,
        |${laststatustoken},
        |${laststatuschange}
     """.stripMargin.replaceAll("\n", " ")
  }

  /**
    * asProfileJs should never need to return the password?!
    *
    * @return
    */
  def asProfileJs(): ProfileJs = {
    ProfileJs(email, accountSubject, firstname, lastname)
  }

}

/**
  *  Used for obtaining the email and password from the HTTP login request
  *  from github.com/mariussoutier/play-angular-require-seed
  */
case class LoginCredentials(email: String, password: String)

/**
  *  Used for obtaining the gauth flow code and login/register action from the HTTP google login request
  *
  *  accesstype should only be of LOGIN or REGISTER
  */
case class GAuthCredentials(authcode: String, accesstype: String)