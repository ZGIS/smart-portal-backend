/*
 * Copyright (c) 2011-2017 Interfaculty Department of Geoinformatics, University of
 * Salzburg (Z_GIS) & Institute of Geological and Nuclear Sciences Limited (GNS Science)
 * in the SMART Aquifer Characterisation (SAC) programme funded by the New Zealand
 * Ministry of Business, Innovation and Employment (MBIE) and Department of Geography,
 * University of Tartu, Estonia (UT) under the ETAG Mobilitas Pluss grant No. MOBJD233.
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

import java.util.{List => JList}

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import utils.ClassnameLogger

import scala.collection.JavaConverters._

trait PortalConfigHolder {

  type JListString = JList[String]
  type JListConfiguration = JList[Configuration]

  val appTimeZone: String
  val sendgridApikey: String
  val emailFrom: String
  val emailReplyTo: String
  val uploadDataPath: String
  val appSecret: String
  val cswInternalApiUrl: String
  val portalExternalBaseLink: String
  val cswIngesterInternalApiUrl: String
  val vocabApiUrl: String
  val adminApiUrl: String
  val adminEmails: Option[JListString]
  val reCaptchaSecret: String
  val recaptcaVerifyUrl: String
  val googleClientSecretFile: String
  val googleServiceAccountSecretFile: String
  val googleStorageBucket: String
  val googleProjectId: String
  val metadataValidValues: Option[JListConfiguration]
}

/**
  * provides reasonably easy central place for the variable config values from application.conf
  * for emailing and app defaults, if any of these fails, we don't want the program to start
  *
  * @param configuration
  */
@Singleton
class PortalConfig @Inject()(configuration: Configuration) extends PortalConfigHolder with ClassnameLogger {

  implicit class OptionOps[A](opt: Option[A]) {
    def check(msg: String): Option[A] = {
      if (opt.isEmpty) {
        new NoSuchElementException("Config not found for " + msg)
      }
      opt
    }
  }

  /**
    *
    * fail early
    *
    * @param a
    * @param path
    * @return
    */
  private def getOrReportStr(path: String): Option[String] = {
    val a = configuration.getString(path)
    a.check(path)
  }

  private def getOrReportEmpty[A](a: Option[A], path: String): Option[A] = {
    a.fold({
      throw new NoSuchElementException("Config not found for " + path)
    })({
      lst =>
        lst match {
          case x: JListConfiguration =>
            if (x.size() <= 0) {
              logger.error("Config is empty list for " + path)
            }
            Some(lst)
          case x: JListString  =>
            if (x.size() <= 0) {
              logger.error("Config is empty list for " + path)
            } else {
              x.asScala.foreach(s => logger.info(path + " value: " + s))
            }
            Some(lst)
          case _ => Some(lst)
        }
    })
  }

  /**
    * datetime.timezone="Pacific/Auckland"
    *   datetime.timezone=${?APP_TIMEZONE}
    */
  val appTimeZone: String = getOrReportStr("datetime.timezone").get

  /**
    * email.sendgrid.apikey="your api key"
    *   email.sendgrid.apikey=${?SENDGRID_API_KEY}
    *   email.sendgrid.from="portal@smart-project.info"
    */
  val sendgridApikey: String = getOrReportStr("email.sendgrid.apikey").get
  val emailFrom: String = getOrReportStr("email.sendgrid.from").get
  val emailReplyTo: String = getOrReportStr("email.sendgrid.replyto").get

  /**
    * upload.datapath = "/tmp"
    *     upload.datapath = ${?UPLOAD_DATA_DIR}
    */
  val uploadDataPath: String = getOrReportStr("smart.upload.datapath").get

  /**
    * the appsecret for hashing purposes
    */
  val appSecret: String = getOrReportStr("play.crypto.secret").get

  /**
    * csw.url = "http://localhost:8000"
    *     csw.url = ${?PYCSW_URL}
    */
  val cswInternalApiUrl: String = getOrReportStr("smart.csw.url").get

  /**
    * # base link for contexts and resources identifiers, also consentableFilelink: https://portal.smart-project.info
    * # email service lazy private val portalApiHost and portalWebguiHost: Option[String] = "https://dev.smart-project.info"
    * # site map website base url: https://dev.smart-project.info
    * # CSW external URL? eg for requestUrl getCapaRequest for new metadata/cswreference
    *     base.url = "https://nz-groundwater-hub.org"
    *     base.url = ${?BASE_URL}
    *
    * lazy private val portalApiHost: Option[String] = "https://dev.smart-project.info"
    * lazy private val portalWebguiHost: Option[String] = "https://dev.smart-project.info"
    */
  val portalExternalBaseLink: String = getOrReportStr("smart.base.url").get

  /**
    * csw-ingester.url = "http://localhost:9001"
    * csw-ingester.url = ${?CSWI_URL}
    */
  val cswIngesterInternalApiUrl: String = getOrReportStr("smart.csw-ingester.url").get

  /**
    * # vocab urls for categoeries etc: http://vocab.smart-project.info
    *   vocab.url = "http://vocab.smart-project.info"
    *   vocab.url = ${?VOCAB_URL}
    */
  val vocabApiUrl: String = getOrReportStr("smart.vocab.url").get

  /**
    * # vocab control update ADMIN_JENA_UPDATE_URL = "https://admin.smart-project.info/kubectl/jena/reload"
    *   admin.url = "https://admin.smart-project.info"
    *   admin.url = ${?ADMIN_URL}
    */
  val adminApiUrl: String = getOrReportStr("smart.admin.url").get

  /**
    * admin.emails = ["allixender@gmail.com" ,"m.moreau@gns.cri.nz"]
    *     admin.emails = ${?ADMIN_EMAILS}
    */
  val adminEmails: Option[JListString] = getOrReportEmpty(
    configuration.getStringList("smart.admin.emails"), "smart.admin.emails")

  /**
    *
    */
  val reCaptchaSecret: String = getOrReportStr("google.recaptcha.secret").get

  /**
    *
    */
  val recaptcaVerifyUrl = "https://www.google.com/recaptcha/api/siteverify"

  /**
    * client.secret = ${?GOOGLE_CLIENT_SECRET}
    */
  val googleClientSecretFile: String = getOrReportStr("google.client.secret").get

  /**
    * service_account.secret = ${?GOOGLE_SERVICE_ACCOUNT_SECRET}
    */
  val googleServiceAccountSecretFile: String = getOrReportStr("google.service_account.secret").get

  /**
    * storage.bucket = ${?GOOGLE_BUCKET_NAME}
    */
  val googleStorageBucket: String = getOrReportStr("google.storage.bucket").get

  /**
    * project.id = ${?GOOGLE_PROJECT_ID}
    */
  val googleProjectId: String = getOrReportStr("google.project.id").get

  /**
    * include "metadata/valid-values.conf"
    */
  val metadataValidValues: Option[JListConfiguration] = getOrReportEmpty(
    configuration.getConfigList("smart.metadata.validValues"), "smart.metadata.validValues")

}
