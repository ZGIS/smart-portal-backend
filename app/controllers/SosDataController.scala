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

package controllers

import java.io.File
import java.net.URLEncoder
import java.nio.file.{Files, Paths}
import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneId, ZonedDateTime}
import javax.inject.Inject

import controllers.security.{RefererHeader, Secured, UserAgentHeader}
import models.ErrorResult
import models.sosdata.{SosCapabilities, Timeseries, TimeseriesData, Wml2Export}
import models.tvp.XmlTvpParser
import models.users.UserLinkLogging
import play.api.Configuration
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.{Action, AnyContent, Controller}
import services.UserService
import utils.{ClassnameLogger, PasswordHashing}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import scala.util.Try

/**
  * Controller to access SOS server and return parsed time series to frontend.
  */
class SosDataController @Inject()(val configuration: Configuration,
                                  val userService: UserService,
                                  val passwordHashing: PasswordHashing,
                                  wsClient: WSClient)
  extends Controller with ClassnameLogger with Secured {

  lazy private val uploadDataPath: String = configuration.getString("smart.upload.datapath")
    .getOrElse("/tmp")
  private lazy val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")
  private lazy val wml2Exporter = new Wml2Export(appTimeZone)
  lazy val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  val GET_CAPABILITIES_XML =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<sos:GetCapabilities
      |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      |    xmlns:sos="http://www.opengis.net/sos/2.0"
      |    xmlns:ows="http://www.opengis.net/ows/1.1"
      |    xmlns:swe="http://www.opengis.net/swe/2.0" service="SOS" xsi:schemaLocation="http://www.opengis.net/sos/2.0 http://schemas.opengis.net/sos/2.0/sosGetCapabilities.xsd">
      |    <ows:AcceptVersions>
      |        <ows:Version>2.0.0</ows:Version>
      |    </ows:AcceptVersions>
      |    <ows:Sections>
      |        <ows:Section>OperationsMetadata</ows:Section>
      |        <ows:Section>ServiceIdentification</ows:Section>
      |        <ows:Section>ServiceProvider</ows:Section>
      |        <ows:Section>FilterCapabilities</ows:Section>
      |        <ows:Section>Contents</ows:Section>
      |    </ows:Sections>
      |</sos:GetCapabilities>
    """.stripMargin


  /**
    * Loads a time series from a SOS server. A Timeseries "configuration" has to be passd within the POST
    * request.
    *
    * @return
    */
  def getTimeseries: Action[JsValue] = HasOptionalTokenAsync(parse.json) {
    authUserOption =>
      implicit request =>

        request.body.validate[Timeseries].fold(
          errors => {
            logger.error(JsError.toJson(errors).toString())
            val error = ErrorResult("Error while validating request.", Some(JsError.toJson(errors).toString))
            Future {
              BadRequest(Json.toJson(error)).as(JSON)
            }
          },

          timeseries => {
            val sosXmlRequest = getObservationRequestXml(timeseries)
            val responseFuture = wsClient.url(timeseries.sosUrl)
              .withHeaders(CONTENT_TYPE -> "application/xml")
              .post(sosXmlRequest)

            responseFuture.recover {
              case e: Exception =>
                logger.error("Sos GetObservation threw exception", e)
                val error = ErrorResult(s"Exception (${e.getClass.getCanonicalName}) on SOS get capabilities.",
                  Some(e.getMessage))
                InternalServerError(Json.toJson(error)).as(JSON)
            }

            responseFuture.map(response => {
              if (response.status != 200) {
                logger.error(s"Calling SOS ${timeseries.sosUrl} HTTP result status ${response.status} on GetObservation")
                val error = ErrorResult(s"Server ${timeseries.sosUrl} responded with ${response.status} on GetObservation",
                  Some(response.body))
                InternalServerError(Json.toJson(error)).as(JSON)
              }
              else if (!response.header("Content-Type").get.contains("application/xml")) {
                logger.error(s"SOS GetObservation result was not XML but ${response.header("Content-Type").get}")
                val error = ErrorResult(
                  s"SOS GetObservation result was not XML but ${response.header("Content-Type").get}",
                  Some(response.body))
                InternalServerError(Json.toJson(error)).as(JSON)
              }
              else if (timeseries.responseFormat.isDefined && !timeseries.responseFormat.get.equalsIgnoreCase("http://www.opengis.net/om/2.0")) {
                logger.error(s"SOS GetObservation responseFormat unsupported for this request; ${timeseries.responseFormat.getOrElse("none")}")
                val error = ErrorResult(
                  s"SOS GetObservation responseFormat unsupported for this request; ${timeseries.responseFormat.getOrElse("none")}",
                  Some(response.body))
                InternalServerError(Json.toJson(error)).as(JSON)
              }
              else {
                val tvp = new XmlTvpParser().parseOm2Measurements(Source.fromString(response.body))
                val tsdata =
                  if (tvp.isEmpty) {
                    TimeseriesData(
                      x = Seq.empty[String],
                      y = Seq.empty[String],
                      name = timeseries.timeseriesName
                    )
                  }
                  else {
                    TimeseriesData(
                      x = tvp.map(_.datetime),
                      y = tvp.map(_.measValue),
                      name = timeseries.timeseriesName
                    )
                  }
                val uom = if (tvp.isEmpty) {
                  if (timeseries.uom.isEmpty) {
                    timeseries.observedProperty
                  } else {
                    timeseries.uom.getOrElse("empty")
                  }
                } else tvp.head.measUnit
                val result = timeseries.copy(data = Some(tsdata), uom = Some(uom))

                val logRequest = UserLinkLogging(id = None,
                  timestamp = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)),
                  ipaddress = Some(request.remoteAddress),
                  useragent = request.headers.get(UserAgentHeader),
                  email = authUserOption,
                  link = sosXmlRequest,
                  referer = request.headers.get(RefererHeader))

                val updated = userService.logLinkInfo(logRequest)

                logger.trace(logRequest.toString)
                Ok(result.toJson()).as(JSON)
              }
            })
          }
        )
  }

  /**
    * Loads a time series from a SOS server. A Timeseries "configuration" has to be passd within the POST
    * request.
    *
    * @return
    */
  def exportTimeseries = HasOptionalTokenAsync(parse.json) {
    authUserOption =>
      implicit request =>

        request.body.validate[Timeseries].fold(
          errors => {
            logger.error(JsError.toJson(errors).toString())
            val error = ErrorResult("Error while validating request.", Some(JsError.toJson(errors).toString))
            Future {
              BadRequest(Json.toJson(error)).as(JSON)
            }
          },

          timeseries => {

            if (timeseries.responseFormat.isDefined && !timeseries.responseFormat.get.equalsIgnoreCase("http://www.opengis.net/waterml/2.0")) {
              logger.error(s"SOS GetObservation responseFormat unsupported for this request; ${timeseries.responseFormat.getOrElse("none")}")
              val error = ErrorResult(
                s"SOS GetObservation responseFormat unsupported for this request; ${timeseries.responseFormat.getOrElse("")}", None)
              InternalServerError(Json.toJson(error)).as(JSON)
            }

            val capabilitiesFuture = retrieveSosCapabilities(timeseries.sosUrl)

            // still normal future recover
            capabilitiesFuture.recover {
              case e: Exception =>
                logger.error("Sos get capabilities threw exception", e)
                val error = ErrorResult(s"Exception (${e.getClass.getCanonicalName}) on SOS get capabilities.",
                  Some(e.getMessage))
                InternalServerError(Json.toJson(error)).as(JSON)
            }

            val sosXmlRequest = getObservationRequestXml(timeseries, "http://www.opengis.net/waterml/2.0")
            val responseFuture = wsClient.url(timeseries.sosUrl)
              .withHeaders(CONTENT_TYPE -> "application/xml")
              .post(sosXmlRequest)

            responseFuture.recover {
              case e: Exception =>
                logger.error("Sos GetObservation threw exception", e)
                val error = ErrorResult(s"Exception (${e.getClass.getCanonicalName}) on SOS get capabilities.",
                  Some(e.getMessage))
                InternalServerError(Json.toJson(error)).as(JSON)
            }

            val combinedFuture: Future[(Either[ErrorResult, SosCapabilities], WSResponse)] = for {
              capaResult <- capabilitiesFuture
              sosDataResult <- responseFuture
            } yield (capaResult, sosDataResult)

            combinedFuture.map(combinedResponse => {

              val response = combinedResponse._2

              if (response.status != 200) {
                logger.error(s"Calling SOS ${timeseries.sosUrl} HTTP result status ${response.status} on GetObservation")
                val error = ErrorResult(s"Server ${timeseries.sosUrl} responded with ${response.status} on GetObservation",
                  Some(response.body))
                InternalServerError(Json.toJson(error)).as(JSON)
              }
              else if (!response.header("Content-Type").get.contains("application/xml")) {
                logger.error(s"SOS GetObservation result was not XML but ${response.header("Content-Type").get}")
                val error = ErrorResult(
                  s"SOS GetObservation result was not XML but ${response.header("Content-Type").get}",
                  Some(response.body))
                InternalServerError(Json.toJson(error)).as(JSON)
              }
              else {
                // Either future folding begins
                combinedResponse._1 match {
                  case Right(sosCapabilities) =>
                    logger.debug(s"sosCapabilities ${sosCapabilities.responseFormats}")
                    if (sosCapabilities.responseFormats.isDefined && !sosCapabilities.responseFormats.get.contains("http://www.opengis.net/waterml/2.0")) {
                      logger.error(s"SOS Server does not support responseFormat for this request; ${timeseries.responseFormat.getOrElse("none")}")
                      val error = ErrorResult(
                        s"SOS Server does not support responseFormat for this request; ${timeseries.responseFormat.getOrElse("none")}", None)
                      InternalServerError(Json.toJson(error)).as(JSON)
                    }
                    val wml2 = wml2Exporter.getWml2ExportFromSosGetObs(response.body, sosCapabilities, sosXmlRequest)
                    if (wml2.isEmpty) {
                      logger.error("Couldn't extract WML2 from this SOS GetObservation response")
                      val error = ErrorResult(
                        "Couldn't extract WML2 from this SOS GetObservation response",
                        Some(response.body))
                      InternalServerError(Json.toJson(error)).as(JSON)
                    } else {
                      val updatedTime = OffsetDateTime.now(ZoneId.of(appTimeZone))
                      val fileName = "export-" + Try(URLEncoder.encode(sosCapabilities.title.replace(" ", "_"), "UTF-8") + "-" + updatedTime.format(formatter)).getOrElse("-sosdata") + ".wml"

                      val pathOfUploadTmp = Paths.get(uploadDataPath)
                      val intermTempDir = Files.createTempDirectory(pathOfUploadTmp, "sos-export-")
                      val tmpFile = new File(intermTempDir.resolve(fileName).toAbsolutePath.toString)

                      scala.xml.XML.save(tmpFile.getAbsolutePath, wml2.get.head, "UTF-8", true, null)

                      val logRequest = UserLinkLogging(id = None,
                        timestamp = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone)),
                        ipaddress = Some(request.remoteAddress),
                        useragent = request.headers.get(UserAgentHeader),
                        email = authUserOption,
                        link = sosXmlRequest,
                        referer = request.headers.get(RefererHeader))

                      val updated = userService.logLinkInfo(logRequest)
                      logger.trace(logRequest.toString)

                      Ok.sendFile(
                        content = tmpFile,
                        fileName = _ => fileName,
                        onClose = () => {
                          Try(tmpFile.delete()).failed.map(ex => logger.error(ex.getLocalizedMessage))
                          Try(Files.delete(intermTempDir)).failed.map(ex => logger.error(ex.getLocalizedMessage))
                        }
                      )
                        .as("application/xml")
                        .withHeaders("Content-disposition" -> s"attachment; filename=$fileName")
                        .withHeaders("Access-Control-Expose-Headers" -> "Content-disposition")
                        .withHeaders("Access-Control-Expose-Headers" -> "x-filename")
                        .withHeaders("x-filename" -> fileName)
                    }
                  case Left(errorResult) => InternalServerError(Json.toJson(errorResult)).as(JSON)
                }
              }
            })
          }
        )
  }

  /**
    * Create GetObservation Request XML String.
    *
    * @param timeseries
    * @return
    */
  private def getObservationRequestXml(timeseries: Timeseries, responseFormat: String = "http://www.opengis.net/om/2.0") = {
    s"""
       |<sos:GetObservation
       |    xmlns:sos="http://www.opengis.net/sos/2.0"
       |    xmlns:fes="http://www.opengis.net/fes/2.0"
       |    xmlns:gml="http://www.opengis.net/gml/3.2"
       |    xmlns:swe="http://www.opengis.net/swe/2.0"
       |    xmlns:xlink="http://www.w3.org/1999/xlink"
       |    xmlns:swes="http://www.opengis.net/swes/2.0"
       |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" service="SOS" version="2.0.0" xsi:schemaLocation="http://www.opengis.net/sos/2.0 http://schemas.opengis.net/sos/2.0/sos.xsd">
       |
       |    <sos:procedure>${timeseries.procedure}</sos:procedure>
       |
       |    <sos:offering>${timeseries.offering}</sos:offering>
       |
       |    <sos:observedProperty>${timeseries.observedProperty}</sos:observedProperty>
       |
       |    <sos:temporalFilter>
       |        <fes:During>
       |            <fes:ValueReference>phenomenonTime</fes:ValueReference>
       |            <gml:TimePeriod gml:id="tp_1">
       |                <gml:beginPosition>${timeseries.fromDate}</gml:beginPosition>
       |                <gml:endPosition>${timeseries.toDate}</gml:endPosition>
       |            </gml:TimePeriod>
       |        </fes:During>
       |    </sos:temporalFilter>
       |
       |    <sos:featureOfInterest>${timeseries.featureOfInterest}</sos:featureOfInterest>
       |
       |    <sos:responseFormat>$responseFormat</sos:responseFormat>
       |</sos:GetObservation>
          """.stripMargin
  }

  /**
    * gets Capabilities from s specified SOS server.
    *
    * @param sosUrl
    * @return
    */
  def getCapabilities(sosUrl: String): Action[AnyContent] = Action.async { request =>

    val responseFuture = retrieveSosCapabilities(sosUrl)

    // still normal future recover
    responseFuture.recover {
      case e: Exception =>
        logger.error("Sos get capabilities threw exception", e)
        val error = ErrorResult(s"Exception (${e.getClass.getCanonicalName}) on SOS get capabilities.",
          Some(e.getMessage))
        InternalServerError(Json.toJson(error)).as(JSON)
    }

    // Either future folding begins
    responseFuture.map(response => {
      response match {
        case Right(sosCapabilities) => Ok(sosCapabilities.toJson()).as(JSON)
        case Left(errorResult) => InternalServerError(Json.toJson(errorResult)).as(JSON)
      }
    })
  }

  /**
    * making the capabilities call reusable
    *
    * @param sosUrl
    * @return
    */
  private def retrieveSosCapabilities(sosUrl: String): Future[Either[ErrorResult, SosCapabilities]] = {
    val responseFuture = wsClient.url(sosUrl)
      .withHeaders(CONTENT_TYPE -> "application/xml")
      .post(GET_CAPABILITIES_XML)

    responseFuture.recover {
      case e: Exception =>
        logger.error("Sos get capabilities threw exception", e)
        Left(ErrorResult(s"Exception (${e.getClass.getCanonicalName}) on SOS get capabilities.",
          Some(e.getMessage)))
    }

    responseFuture.map(response => {
      if (response.status != 200) {
        logger.error(s"Calling SOS ${sosUrl} HTTP result status ${response.status}")
        Left(ErrorResult(s"Server ${sosUrl} responded with ${response.status} on GetCapabilities",
          Some(response.body)))
      }
      else if (!response.header("Content-Type").get.contains("application/xml")) {
        logger.error(s"SOS GetCapabilities result was not XML but ${response.header("Content-Type").get}")
        Left(ErrorResult(s"SOS GetCapabilities result was not XML but ${response.header("Content-Type").get}",
          Some(response.body)))
      }
      else {
        Right(SosCapabilities.fromXml(response.xml, sosUrl).get)
      }
    })
  }
}
