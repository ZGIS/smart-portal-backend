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

import javax.inject.Inject

import models.ErrorResult
import models.sosdata.{SosCapabilities, Timeseries, TimeseriesData}
import models.tvp.XmlTvpParser
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, Controller}
import utils.ClassnameLogger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

/**
  * Controller to access SOS server and return parsed time series to frontend.
  */
class SosDataController @Inject()(wsClient: WSClient)
  extends Controller with ClassnameLogger {

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
  def getTimeseries: Action[JsValue] = Action.async(parse.json) { request =>
    request.body.validate[Timeseries].fold(
      errors => {
        logger.error(JsError.toJson(errors).toString())
        val error = ErrorResult("Error while validating request.", Some(JsError.toJson(errors).toString))
        Future {
          BadRequest(Json.toJson(error)).as(JSON)
        }
      },

      timeseries => {
        val responseFuture = wsClient.url(timeseries.sosUrl)
          .withHeaders(CONTENT_TYPE -> "application/xml")
          .post(getObservationRequestXml(timeseries))

        responseFuture.recover {
          case e: Exception =>
            logger.error("Sos get capabilities threw exception", e)
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
            logger.error(s"SOS GetCapabilities result was not XML but ${response.header("Content-Type").get}")
            val error = ErrorResult(
              s"SOS GetCapabilities result was not XML but ${response.header("Content-Type").get}",
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
            val result = timeseries.copy(data = Some(tsdata), uom = Some(tvp.head.measUnit))
            Ok(result.toJson()).as(JSON)
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
  private def getObservationRequestXml(timeseries: Timeseries) = {
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
       |    <sos:responseFormat>http://www.opengis.net/om/2.0</sos:responseFormat>
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

    val responseFuture = wsClient.url(sosUrl)
      .withHeaders(CONTENT_TYPE -> "application/xml")
      .post(GET_CAPABILITIES_XML)

    responseFuture.recover {
      case e: Exception =>
        logger.error("Sos get capabilities threw exception", e)
        val error = ErrorResult(s"Exception (${e.getClass.getCanonicalName}) on SOS get capabilities.",
          Some(e.getMessage))
        InternalServerError(Json.toJson(error)).as(JSON)
    }

    responseFuture.map(response => {
      if (response.status != 200) {
        logger.error(s"Calling SOS ${sosUrl} HTTP result status ${response.status}")
        val error = ErrorResult(s"Server ${sosUrl} responded with ${response.status} on GetCapabilities",
          Some(response.body))
        InternalServerError(Json.toJson(error)).as(JSON)
      }
      else if (!response.header("Content-Type").get.contains("application/xml")) {
        logger.error(s"SOS GetCapabilities result was not XML but ${response.header("Content-Type").get}")
        val error = ErrorResult(s"SOS GetCapabilities result was not XML but ${response.header("Content-Type").get}",
          Some(response.body))
        InternalServerError(Json.toJson(error)).as(JSON)
      }
      else {
        val json = SosCapabilities.fromXml(response.xml, sosUrl).get.toJson()
        Ok(json).as(JSON)
      }
    })
  }
}
