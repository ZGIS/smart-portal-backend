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

import com.norbitltd.spoiwo.model.Sheet
import com.norbitltd.spoiwo.natures.csv.CsvProperties
import controllers.security.{OptionalAuthenticationAction, RefererHeader, UserAgentHeader}
import javax.inject.Inject
import models.ErrorResult
import models.sosdata.SosDataFormat.{CSV, OM20, WML2, XLS}
import models.sosdata._
import models.tvp.XmlTvpParser
import models.users.UserLinkLogging
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.{Action, AnyContent}
import services.{PortalConfig, UserService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import scala.util.Try

/**
  * Controller to access SOS server and return parsed time series to frontend.
  */
class SosDataController @Inject()(portalConfig: PortalConfig,
                                  val userService: UserService,
                                  optionalAuthenticationAction: OptionalAuthenticationAction,
                                  wsClient: WSClient)
  extends ConfiguredController(portalConfig) {

  lazy val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  private lazy val wml2Exporter = new Wml2Export(portalConfig.appTimeZone)
  private lazy val xlsExporter = new SpreadsheetExport(portalConfig.appTimeZone)

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
  def getTimeseries: Action[JsValue] = optionalAuthenticationAction.async(parse.json) {
    request =>
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

          responseFuture.map { response =>
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
                timestamp = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(portalConfig.appTimeZone)),
                ipaddress = Some(request.remoteAddress),
                useragent = request.headers.get(UserAgentHeader),
                email = request.optionalSession.map(_.email),
                link = sosXmlRequest,
                referer = request.headers.get(RefererHeader))

              val updated = userService.logLinkInfo(logRequest)

              logger.trace(logRequest.toString)
              Ok(result.toJson()).as(JSON)
            }
          }
        }
      )
  }

  /**
    * Loads a time series from a SOS server. A Timeseries "configuration" has to be passd within the POST
    * request.
    *
    * @return
    */
  def exportTimeseries = optionalAuthenticationAction.async(parse.json) {
    request =>
      request.body.validate[Timeseries].fold(
        errors => {
          logger.error(JsError.toJson(errors).toString())
          val error = ErrorResult("Error while validating request.", Some(JsError.toJson(errors).toString))
          Future {
            BadRequest(Json.toJson(error)).as(JSON)
          }
        },

        timeseries => {

          if (!timeseries.responseFormat.exists(f => SosDataFormat.supportedExportFormats.contains(f))) {
            logger.error(s"This export format for observations is unsupported: ${timeseries.responseFormat.getOrElse("none")}")
            val error = ErrorResult(
              s"This export format for observations is unsupported: ${timeseries.responseFormat.getOrElse("")}", None)
            BadRequest(Json.toJson(error)).as(JSON)
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

          val sosLoadingFormat = timeseries.responseFormat match {
            case Some(WML2.value) => WML2.value
            case Some(XLS.value) => OM20.value
            case Some(CSV.value) => OM20.value
            case None => OM20.value
            case _ => throw new IllegalArgumentException(f"This format doesn't work, something is wrong, ${timeseries.responseFormat.getOrElse("unknown")}")
          }

          val sosXmlRequest = getObservationRequestXml(timeseries, sosLoadingFormat)
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
                case Right(sosCapabilities) => {
                  logger.debug(s"sosCapabilities ${sosCapabilities.responseFormats}")
                  if (!sosCapabilities.responseFormats.exists(_.contains(sosLoadingFormat))) {
                    logger.error(s"SOS Server does not support responseFormat for this request; ${timeseries.responseFormat.getOrElse("none")}")
                    val error = ErrorResult(
                      s"SOS Server does not support responseFormat for this request; ${timeseries.responseFormat.getOrElse("none")}", None)
                    BadRequest(Json.toJson(error)).as(JSON)
                  }

                  val logRequest = UserLinkLogging(id = None,
                    timestamp = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(portalConfig.appTimeZone)),
                    ipaddress = Some(request.remoteAddress),
                    useragent = request.headers.get(UserAgentHeader),
                    email = request.optionalSession.map(_.email),
                    link = sosXmlRequest,
                    referer = request.headers.get(RefererHeader))

                  val updated = userService.logLinkInfo(logRequest)
                  logger.trace(logRequest.toString)

                  val exported = exportToFile(timeseries.responseFormat, response.body, sosCapabilities, sosXmlRequest)
                  exported.fold(
                    error => BadRequest(Json.toJson(error)).as(JSON),
                    fileFile => Ok.sendFile(
                      content = fileFile,
                      fileName = _ => fileFile.getName,
                      onClose = () => {
                        Try(fileFile.delete()).failed.map(ex => logger.error(ex.getLocalizedMessage))
                        Try(Files.delete(fileFile.getParentFile.toPath)).failed.map(ex => logger.error(ex.getLocalizedMessage))
                      }
                    )
                      .as(timeseries.responseFormat.map(f => SosDataFormat.apply(f).contentType).getOrElse("application/octet-stream"))
                      .withHeaders("Content-disposition" -> s"attachment; filename=${fileFile.getName}")
                      .withHeaders("Access-Control-Expose-Headers" -> "Content-disposition")
                      .withHeaders("Access-Control-Expose-Headers" -> "x-filename")
                      .withHeaders("x-filename" -> fileFile.getName))
                }

                case Left(errorResult) => InternalServerError(Json.toJson(errorResult)).as(JSON)
              }
            }
          })
        }
      )
  }

  /**
    * a bit of a crippling nested fold but the write-out as file for xls/csv only does Unit return,
    * thought it would be nice to add some IO security around it
    *
    * @param responseFormat
    * @param sosResponseAsText
    * @param sosCapabilities
    * @param sosXmlRequest
    * @return
    */
  def exportToFile(responseFormat: Option[String], sosResponseAsText: String,
                   sosCapabilities: SosCapabilities, sosXmlRequest: String): Either[ErrorResult, File] = {

    val updatedTime = OffsetDateTime.now(ZoneId.of(portalConfig.appTimeZone))
    val fileNameTmpl = "export-" + Try(URLEncoder.encode(sosCapabilities.title.replace(" ", "_"), "UTF-8")
      + "-" + updatedTime.format(formatter)).getOrElse("-sosdata")
    val pathOfUploadTmp = Paths.get(portalConfig.uploadDataPath)
    val intermTempDir = Files.createTempDirectory(pathOfUploadTmp, "sos-export-")

    responseFormat match {
      case Some(WML2.value) => {
        val wml2 = wml2Exporter.getWml2ExportFromSosGetObs(sosResponseAsText, sosCapabilities, sosXmlRequest)

        if (wml2.isEmpty) {
          logger.error("Couldn't extract OM2 into SpreadSheet from this SOS GetObservation response")
          Left(ErrorResult(message = "Couldn't extract OM2 into SpreadSheet from this SOS GetObservation response",
            details = Some("Sheet is empty, no data?")))
        } else {
          val tmpFile = new File(intermTempDir.resolve(fileNameTmpl + WML2.fileSuffix).toAbsolutePath.toString)
          scala.xml.XML.save(tmpFile.getAbsolutePath, wml2.get.head, "UTF-8", true, null)
          // return ref handle
          Right(tmpFile)
        }
      }
      case Some(spreadSheet) if spreadSheet.equals(XLS.value) || spreadSheet.equals(CSV.value) => {
        val format = SosDataFormat.apply(spreadSheet)
        val tmpFile = new File(intermTempDir.resolve(fileNameTmpl + format.fileSuffix).toAbsolutePath.toString)
        val sheets = xlsExporter.getSpreadsheetExportFromSosGetObs(sosResponseAsText, sosCapabilities, sosXmlRequest)
        val saved = saveSpreadSheet(sheets, format, tmpFile.getAbsolutePath)
        saved.map(err => Left(err)).getOrElse(Right(tmpFile))
      }
      case Some(otherText) =>
        logger.error(s"Couldn't extract OM2 into SpreadSheet from this SOS GetObservation response, unsupported fomat:? $otherText")
        Left(ErrorResult(message = s"Couldn't extract OM2 into SpreadSheet from this SOS GetObservation response, unsupported fomat:? $otherText", details = None))
    }
  }

  /**
    * export sheet as either CSV or XLS format, write to re-destined file location
    *
    * @param sheets
    * @param format
    * @param filename
    */
  def saveSpreadSheet(sheets: Option[Sheet], format: SosDataFormat, filename: String): Option[ErrorResult] = {
    try {
      sheets.fold[Option[ErrorResult]] {
        logger.error("Couldn't extract OM2 into SpreadSheet from this SOS GetObservation response")
        Some(ErrorResult(message = "Couldn't extract OM2 into SpreadSheet from this SOS GetObservation response", details = Some("Sheet is empty, no data?")))
      } {
        wb =>
          if (format.equals(XLS)) {
            import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._
            // TODO here add spreadsheet with metadata export info
            wb.saveAsXlsx(filename)
            None
          } else if (format.equals(CSV)) {
            import com.norbitltd.spoiwo.natures.csv.Model2CsvConversions._
            // TODO here add metadata export info after last two empty lines
            val defCsvProps = CsvProperties(separator = ",", defaultDateFormat = "yyyy-MM-dd")
            wb.saveAsCsv(filename, defCsvProps)
            None
          } else {
            logger.error("Couldn't extract OM2 into SpreadSheet from this SOS GetObservation response")
            Some(ErrorResult(
              message = "Couldn't extract OM2 into SpreadSheet from this SOS GetObservation response",
              details = Some(s"Unsupported format $format?")))
          }
      }
    } catch {
      case ex: Exception =>
        logger.error(s"Couldn't extract OM2 into SpreadSheet from this SOS GetObservation response, ${ex.getLocalizedMessage}")
        Some(ErrorResult(message = "Couldn't extract OM2 into SpreadSheet from this SOS GetObservation response", details = Some(ex.getLocalizedMessage)))
    }
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
