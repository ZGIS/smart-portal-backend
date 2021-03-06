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

import com.sendgrid._
import javax.inject.{Inject, Singleton}
import utils.ClassnameLogger

/**
  * Email Service sends emails out to the wide wide world
  *
  * @param configuration
  */
@Singleton
class EmailService @Inject()(portalConfig: PortalConfig) extends ClassnameLogger {

  lazy private val apikey: String = portalConfig.sendgridApikey
  lazy private val emailFrom: String = portalConfig.emailFrom
  lazy private val emailReplyTo: String = portalConfig.emailReplyTo
  lazy private val portalApiHost: String = portalConfig.portalExternalBaseLink
  lazy private val portalWebguiHost: String = portalConfig.portalExternalBaseLink

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
        |thank you for registering on the GW HUB. Your account will be fully active once you confirmed your email address.
        |Please click on the following link to confirm your registration:
        |
        |%s/api/v1/users/register/%s
        |
        |If you have any question please email us to %s.
        |
        |Your GW HUB Team
      """.format(usernameTo, portalApiHost, linkId, emailFrom).stripMargin

    val from = new Email(emailFrom)
    val to = new Email(emailTo)

    val content = new Content("text/plain", emailText)
    val mail = new Mail(from, subject, to, content)

    val replyTo = new Email(emailReplyTo)
    mail.setReplyTo(replyTo)

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
        |thank you for registering on the GW HUB, your account is now fully active.
        |
        |Login with your active account on:
        |
        |%s/#/login
        |
        |If you have any question please email us to %s.
        |
        |Your GW HUB Team
      """.format(usernameTo, portalWebguiHost, emailFrom).stripMargin

    val from = new Email(emailFrom)
    val to = new Email(emailTo)
    val content = new Content("text/plain", emailText)
    val mail = new Mail(from, subject, to, content)

    val replyTo = new Email(emailReplyTo)
    mail.setReplyTo(replyTo)

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
    * sends a password reset Email, with the special reset token
    *
    * @param emailTo
    * @param subject
    * @param usernameTo
    * @param linkId
    * @return
    */
  def sendResetPasswordRequestEmail(emailTo: String, subject: String, usernameTo: String, linkId: String): Boolean = {

    val emailText =
      """Hello %s,
        |we got your request to reset your password for the GW HUB.
        |Please click on the following link to reset your password:
        |
        |%s/#/resetpass/%s
        |
        |If you have any question please email us to %s.
        |
        |Your GW HUB Team
      """.format(usernameTo, portalWebguiHost, linkId, emailFrom).stripMargin

    val from = new Email(emailFrom)
    val to = new Email(emailTo)
    val content = new Content("text/plain", emailText)
    val mail = new Mail(from, subject, to, content)

    val replyTo = new Email(emailReplyTo)
    mail.setReplyTo(replyTo)

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
  def sendPasswordUpdateEmail(emailTo: String, subject: String, usernameTo: String): Boolean = {

    val emailText =
      """Hello %s,
        |you updated your password on the GW HUB. The new password is now active.
        |
        |Login with your account on:
        |
        |%s/#/login
        |
        |If you have any question please email us to %s.
        |
        |Your GW HUB Team
      """.format(usernameTo, portalWebguiHost, emailFrom).stripMargin

    val from = new Email(emailFrom)
    val to = new Email(emailTo)
    val content = new Content("text/plain", emailText)
    val mail = new Mail(from, subject, to, content)

    val replyTo = new Email(emailReplyTo)
    mail.setReplyTo(replyTo)

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
    * send a Registration Email with a pre-generated unique link to confirm your account
    *
    * @param emailTo
    * @param subject
    * @param usernameTo
    * @param linkId
    * @return
    */
  def sendNewEmailValidationEmail(emailTo: String, subject: String, usernameTo: String, linkId: String): Boolean = {

    val emailText =
      """Hello %s,
        |we got your request to change your email address for the GW HUB.
        |Please click on the following link to confirm your new email:
        |
        |%s/api/v1/users/register/%s
        |
        |If you have any question please email us to %s.
        |
        |Your GW HUB Team
      """.format(usernameTo, portalApiHost, linkId, emailFrom).stripMargin

    logger.debug(emailText)

    val from = new Email(emailFrom)
    val to = new Email(emailTo)
    val content = new Content("text/plain", emailText)
    val mail = new Mail(from, subject, to, content)

    val replyTo = new Email(emailReplyTo)
    mail.setReplyTo(replyTo)

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
  def sendNeEmailConfirmationEmail(emailTo: String, subject: String, usernameTo: String): Boolean = {

    val emailText =
      """Hello %s,
        |thank you for confirming your new email address, your account is now configured with your new email address.
        |
        |Login with your new email address on:
        |
        |%s/#/login
        |
        |If you have any question please email us to %s.
        |
        |Your GW HUB Team
      """.format(usernameTo, portalWebguiHost, emailFrom).stripMargin

    val from = new Email(emailFrom)
    val to = new Email(emailTo)
    val content = new Content("text/plain", emailText)
    val mail = new Mail(from, subject, to, content)

    val replyTo = new Email(emailReplyTo)
    mail.setReplyTo(replyTo)

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
  def sendProfileUpdateInfoEmail(emailTo: String, subject: String, usernameTo: String): Boolean = {

    val emailText =
      """Hello %s,
        |Your account profile has been updated. This email is for your information.
        |
        |To login to your account go to:
        |
        |%s/#/login
        |
        |If you have any questions please email us to %s.
        |
        |Your GW HUB Team
      """.format(usernameTo, portalWebguiHost, emailFrom).stripMargin

    val from = new Email(emailFrom)
    val to = new Email(emailTo)
    val content = new Content("text/plain", emailText)
    val mail = new Mail(from, subject, to, content)

    val replyTo = new Email(emailReplyTo)
    mail.setReplyTo(replyTo)

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
