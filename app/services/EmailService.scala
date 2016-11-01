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

import java.util.Properties
import javax.inject.{Inject, Singleton}
import javax.mail._
import javax.mail.internet._

import play.api.Configuration
import utils.ClassnameLogger

/**
  * Email Service sends emails out to the wide wide world
  *
  * @param configuration
  */
@Singleton
class EmailService @Inject()(configuration: Configuration) extends ClassnameLogger {

  lazy private val emailUsername: String = configuration.getString("email.username").getOrElse("user")
  lazy private val emailPassword: String = configuration.getString("email.password").getOrElse("pass")
  lazy private val emailFrom: String = configuration.getString("email.from").getOrElse("portal@smart-project.info")
  lazy private val emailServAddress: String = configuration.getString("email.serv.address").getOrElse("mail.smart-project.info")
  lazy private val emailServPort: String = configuration.getString("email.serv.port").getOrElse("587")

  var mailServerProperties: Properties = System.getProperties()
  var getMailSession: javax.mail.Session = _

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

    mailServerProperties.put("mail.smtp.host", emailServAddress)
    mailServerProperties.put("mail.smtp.port", emailServPort)
    mailServerProperties.put("mail.smtp.auth", "true")
    mailServerProperties.put("mail.smtp.starttls.enable", "true")

    getMailSession = Session.getDefaultInstance(mailServerProperties, null)

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

    try {
      // Create a default MimeMessage object.
      val message = new MimeMessage(getMailSession)
      // Set From: header field of the header.
      message.setFrom(new InternetAddress(emailFrom))
      // Set To: header field of the header.
      message.addRecipient(Message.RecipientType.TO, new InternetAddress(emailTo))
      // Set Subject: header field. eg "Please confirm your GW HUB account"
      message.setSubject(subject)

      message.setText(emailText)
      logger.debug(emailText)

      // Send message
      val transport: Transport = getMailSession.getTransport("smtp")
      transport.connect(emailServAddress, emailUsername, emailPassword)
      transport.sendMessage(message, message.getAllRecipients)
      transport.close()
      true
    } catch {
      case mex: MessagingException => {
        logger.error("Messaging exception: " + mex.getLocalizedMessage)
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

    mailServerProperties.put("mail.smtp.host", emailServAddress)
    mailServerProperties.put("mail.smtp.port", emailServPort)
    mailServerProperties.put("mail.smtp.auth", "true")
    mailServerProperties.put("mail.smtp.starttls.enable", "true")

    getMailSession = Session.getDefaultInstance(mailServerProperties, null)

    val emailText =
      """Hello %s,
        |thank you for registering on the GW HUB, your account is now active.
        |Please click on the following link to confirm your registration:
        |
        |If you have any question please email us to %s.
        |
        |Your GW HUB Team
      """.format(usernameTo, emailFrom).stripMargin

    try {
      // Create a default MimeMessage object.
      val message = new MimeMessage(getMailSession)
      // Set From: header field of the header.
      message.setFrom(new InternetAddress(emailFrom))
      // Set To: header field of the header.
      message.addRecipient(Message.RecipientType.TO, new InternetAddress(emailTo))
      // Set Subject: header field. eg "Please confirm your GW HUB account"
      message.setSubject(subject)

      message.setText(emailText)
      logger.debug(emailText)

      // Send message
      val transport: Transport = getMailSession.getTransport("smtp")
      transport.connect(emailServAddress, emailUsername, emailPassword)
      transport.sendMessage(message, message.getAllRecipients)
      transport.close()
      true
    } catch {
      case mex: MessagingException => {
        logger.error("Messaging exception: " + mex.getLocalizedMessage)
        false
      }
      case e: Exception => {
        logger.error("Other email problem: " + e.getLocalizedMessage)
        false
      }
    }
  }

  /**
    * send and Info Email to many recipients, with the message body provided
    *
    * @param emailTo
    * @param subject
    * @param usernameTo
    * @param mainMessageText
    */
  def sendInfoEmail(emailTo: List[String], subject: String, usernameTo: String, mainMessageText: String): Boolean = {

    mailServerProperties.put("mail.smtp.host", emailServAddress)
    mailServerProperties.put("mail.smtp.port", emailServPort)
    mailServerProperties.put("mail.smtp.auth", "true")
    mailServerProperties.put("mail.smtp.starttls.enable", "true")

    getMailSession = Session.getDefaultInstance(mailServerProperties, null)

    val emailText =
      """Hello %s,
        |
        |%s
        |
        |If you have any question please email us to %s.
        |
        |Your GW HUB Team
      """.format(usernameTo, mainMessageText, emailFrom).stripMargin

    try {
      // Create a default MimeMessage object.
      val message = new MimeMessage(getMailSession)
      // Set From: header field of the header.
      message.setFrom(new InternetAddress(emailFrom))
      // Set To: header field of the header.

      message.addRecipients(Message.RecipientType.TO, emailTo.mkString(", "))

      // Set Subject: header field. eg "Please confirm your GW HUB account"
      message.setSubject(subject)

      message.setText(emailText)
      logger.debug(emailText)

      // Send message
      val transport: Transport = getMailSession.getTransport("smtp")
      transport.connect(emailServAddress, emailUsername, emailPassword)
      transport.sendMessage(message, message.getAllRecipients)
      transport.close()
      true
    } catch {
      case mex: MessagingException => {
        logger.error("Messaging exception: " + mex.getLocalizedMessage)
        false
      }
      case e: Exception => {
        logger.error("Other email problem: " + e.getLocalizedMessage)
        false
      }
    }
  }

}
