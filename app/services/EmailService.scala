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

package services

import java.io.IOException
import javax.inject.{Inject, Singleton}
import javax.mail._
import javax.mail.internet._

import com.sendgrid._
import play.api.Configuration
import utils.ClassnameLogger

/**
  * Email Service sends emails out to the wide wide world
  *
  * @param configuration
  */
@Singleton
class EmailService @Inject()(configuration: Configuration) extends ClassnameLogger {

  lazy private val apikey: String = configuration.getString("email.sendgrid.apikey").getOrElse("empty-api-key")
  lazy private val emailFrom: String = configuration.getString("email.sendgrid.from").getOrElse("allixender@googlemail.com")

  lazy val sg = new SendGrid(apikey)

  /**
    * send a Registration Email with a pre-generated unique link to confirm your account
    *
    * @param emailTo
    * @param subject
    * @param usernameTo
    * @param linkId
    * @return
    */
  def sendRegistrationEmail(emailTo: String, subject: String, usernameTo: String, linkId: String): Boolean = {

    val emailText =
      """Hello %s,
        |thank you for registering on the GW HUB.
        |Please click on the following link to confirm your registration:
        |
        |http://dev.smart-project.info/api/v1/users/register/%s
        |
        |If you have any question please email us to %s.
        |
        |Your GW HUB Team
      """.format(usernameTo, linkId, emailFrom).stripMargin

    val from = new Email(emailFrom)
    val to = new Email(emailTo)
    val content = new Content("text/plain", emailText)
    val mail = new Mail(from, subject, to, content)

    try {

      val request = new Request()
      request.method = Method.POST
      request.endpoint = "mail/send"
      request.body = mail.build()
      val response = sg.api(request)
      logger.trace(s"mail api response status: ${response.statusCode}")
      logger.trace(s"mail api response.body: ${response.body}")
      logger.trace(s"response.headers: ${response.headers}")
      logger.trace(s"mail api response status: ${response.statusCode}")

      true
    } catch {
      case ioex: IOException => {
        logger.error("IO Messaging exception: " + ioex.getLocalizedMessage)
        false
      }
      case e: Exception => {
        logger.error("Other email problem: " + e.getLocalizedMessage)
        false
      }
    }
  }

  /**
    * sends a Confirmation Email, Your account is now active
    *
    * @param emailTo
    * @param subject
    * @param usernameTo
    * @return
    */
  def sendConfirmationEmail(emailTo: String, subject: String, usernameTo: String): Boolean = {

    val emailText =
      """Hello %s,
        |thank you for registering on the GW HUB, your account is now active.
        |Please click on the following link to confirm your registration:
        |
        |If you have any question please email us to %s.
        |
        |Your GW HUB Team
      """.format(usernameTo, emailFrom).stripMargin

    val from = new Email(emailFrom)
    val to = new Email(emailTo)
    val content = new Content("text/plain", emailText)
    val mail = new Mail(from, subject, to, content)

    try {
      val request = new Request()
      request.method = Method.POST
      request.endpoint = "mail/send"
      request.body = mail.build()
      val response = sg.api(request)
      logger.trace(s"mail api response status: ${response.statusCode}")
      logger.trace(s"mail api response.body: ${response.body}")
      logger.trace(s"response.headers: ${response.headers}")
      logger.trace(s"mail api response status: ${response.statusCode}")

      true
    } catch {
      case ioex: IOException => {
        logger.error("IO Messaging exception: " + ioex.getLocalizedMessage)
        false
      }
      case e: Exception => {
        logger.error("Other email problem: " + e.getLocalizedMessage)
        false
      }
    }
  }
}
