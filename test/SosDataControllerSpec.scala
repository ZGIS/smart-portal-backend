/*
 * Copyright (C) 2011-2017 Interfaculty Department of Geoinformatics, University of
 * Salzburg (Z_GIS) & Institute of Geological and Nuclear Sciences Limited (GNS Science)
 * in the SMART Aquifer Characterisation (SAC) programme funded by the New Zealand
 * Ministry of Business, Innovation and Employment (MBIE)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File
import java.net.URLEncoder
import java.nio.file.{Files, Paths}
import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneId}

import controllers.{appTimeZone, uploadDataPath}
import models.sosdata.{SosCapabilities, SpreadsheetExport, Wml2Export}
import play.api.mvc.Results

import scala.io.Source
import scala.util.Try

class SosDataControllerSpec extends WithDefaultTest with Results {

  private lazy val capaResource1  = this.getClass().getResource("tvp/sos-200-getcapa.xml")
  private lazy val om2Resource1 = this.getClass().getResource("tvp/sos-om2-one-series.xml")
  private lazy val wml2Resource1 = this.getClass().getResource("tvp/sos-wml2-one-series.xml")
  val url = "https://portal.smart-project.info/sos-smart/service?service=SOS&request=GetCapabilities&AcceptVersions=2.0.0"
  lazy val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  "SosCapabilites Parser" should {

    "read capabilites from xml" in {
      val sosCapa = SosCapabilities.fromXml(scala.xml.XML.load(capaResource1), url).get

      sosCapa.featuresOfInterest must contain ("http://vocab.smart-project.info/ngmp/feature/389")
      sosCapa.observedProperties must contain ("http://vocab.smart-project.info/ngmp/phenomenon/1616")
      sosCapa.procedures must contain ("http://vocab.smart-project.info/ngmp/procedure/1616")
      sosCapa.offerings must contain ("http://vocab.smart-project.info/ngmp/offering/1619")
      sosCapa.responseFormats.get must contain ("http://www.opengis.net/om/2.0")
      sosCapa.responseFormats.get must contain ("http://www.opengis.net/waterml/2.0")
      sosCapa.sosUrl mustEqual url
    }

    "read write json" in {
      val xml = Source.fromURL(capaResource1)
      val sosCapa = SosCapabilities.fromXml(scala.xml.XML.load(capaResource1), url).get

      SosCapabilities.fromJson(sosCapa.toJson()).get mustEqual sosCapa
      SosCapabilities.fromJson(sosCapa.toJson()).get.toJson() mustEqual sosCapa.toJson()
    }
  }

  "SosDataController" should {
    "getWml2ExportFromSosGetObs" in {

      val sosCapa = SosCapabilities.fromXml(scala.xml.XML.load(capaResource1), url).get

      val appTimeZone: String = "Pacific/Auckland"
      val updatedTime = OffsetDateTime.now(ZoneId.of(appTimeZone))
      val wml2Exporter = new Wml2Export(appTimeZone)
      val sourceString = scala.io.Source.fromURL(wml2Resource1).getLines().mkString

      val requestXml = s"""<sos:GetObservation
            |    xmlns:sos="http://www.opengis.net/sos/2.0"
            |    xmlns:fes="http://www.opengis.net/fes/2.0"
            |    xmlns:gml="http://www.opengis.net/gml/3.2"
            |    xmlns:swe="http://www.opengis.net/swe/2.0"
            |    xmlns:xlink="http://www.w3.org/1999/xlink"
            |    xmlns:swes="http://www.opengis.net/swes/2.0"
            |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" service="SOS" version="2.0.0" xsi:schemaLocation="http://www.opengis.net/sos/2.0 http://schemas.opengis.net/sos/2.0/sos.xsd">
            |    <sos:procedure>${sosCapa.procedures.head}</sos:procedure>
            |    <sos:offering>${sosCapa.offerings.head}</sos:offering>
            |    <sos:observedProperty>${sosCapa.observedProperties.head}</sos:observedProperty>
            |    <sos:temporalFilter>
            |        <fes:During>
            |            <fes:ValueReference>phenomenonTime</fes:ValueReference>
            |            <gml:TimePeriod gml:id="tp_1">
            |                <gml:beginPosition>${updatedTime.minusYears(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}</gml:beginPosition>
            |                <gml:endPosition>${updatedTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}</gml:endPosition>
            |            </gml:TimePeriod>
            |        </fes:During>
            |    </sos:temporalFilter>
            |    <sos:featureOfInterest>${sosCapa.featuresOfInterest.head}</sos:featureOfInterest>
            |    <sos:responseFormat>http://www.opengis.net/waterml/2.0</sos:responseFormat></sos:GetObservation>
          """.stripMargin

      val wml2 = wml2Exporter.getWml2ExportFromSosGetObs(sourceString,sosCapa, requestXml)
      val fileName = "export-" + Try (URLEncoder.encode(sosCapa.title.replace(" ", "_"), "UTF-8") ).getOrElse("-sosdata") + ".wml"

      wml2.toString.contains("NGMP") mustBe true
      fileName mustEqual "export-NGMP_SOS.wml"

      println(wml2.toString())
    }

    "getExcelSpreadSheetExportFromSosGetObs" in {
      val sosCapa = SosCapabilities.fromXml(scala.xml.XML.load(capaResource1), url).get

      val appTimeZone: String = "Pacific/Auckland"
      val updatedTime = OffsetDateTime.now(ZoneId.of(appTimeZone))
      val xlsExporter = new SpreadsheetExport(appTimeZone)
      val sourceString = scala.io.Source.fromURL(om2Resource1).getLines().mkString

      val requestXml = s"""<sos:GetObservation
                          |    xmlns:sos="http://www.opengis.net/sos/2.0"
                          |    xmlns:fes="http://www.opengis.net/fes/2.0"
                          |    xmlns:gml="http://www.opengis.net/gml/3.2"
                          |    xmlns:swe="http://www.opengis.net/swe/2.0"
                          |    xmlns:xlink="http://www.w3.org/1999/xlink"
                          |    xmlns:swes="http://www.opengis.net/swes/2.0"
                          |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" service="SOS" version="2.0.0" xsi:schemaLocation="http://www.opengis.net/sos/2.0 http://schemas.opengis.net/sos/2.0/sos.xsd">
                          |    <sos:procedure>${sosCapa.procedures.head}</sos:procedure>
                          |    <sos:offering>${sosCapa.offerings.head}</sos:offering>
                          |    <sos:observedProperty>${sosCapa.observedProperties.head}</sos:observedProperty>
                          |    <sos:temporalFilter>
                          |        <fes:During>
                          |            <fes:ValueReference>phenomenonTime</fes:ValueReference>
                          |            <gml:TimePeriod gml:id="tp_1">
                          |                <gml:beginPosition>${updatedTime.minusYears(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}</gml:beginPosition>
                          |                <gml:endPosition>${updatedTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}</gml:endPosition>
                          |            </gml:TimePeriod>
                          |        </fes:During>
                          |    </sos:temporalFilter>
                          |    <sos:featureOfInterest>${sosCapa.featuresOfInterest.head}</sos:featureOfInterest>
                          |    <sos:responseFormat>http://www.opengis.net/waterml/2.0</sos:responseFormat></sos:GetObservation>
          """.stripMargin

      val sheets = xlsExporter.getSpreadsheetExportFromSosGetObs(sourceString,sosCapa, requestXml)
      sheets mustBe defined


      val fileNameTmpl = "export-" + Try(URLEncoder.encode(sosCapa.title.replace(" ", "_"), "UTF-8") + "-" + updatedTime.format(formatter)).getOrElse("-sosdata")
      val intermTempDir = Files.createTempDirectory("sos-export-")

      import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._
      val tmpFile = new File(intermTempDir.resolve(fileNameTmpl + ".xlsx").toAbsolutePath.toString)
      sheets.foreach(wb => wb.saveAsXlsx(tmpFile.getAbsolutePath))
      println(tmpFile.getAbsolutePath)
      new File(tmpFile.getAbsolutePath).canWrite mustBe true
    }
  }
  /*
  POST        /api/v1/sos/timeseries                         controllers.SosDataController.getTimeseries()
GET         /api/v1/sos/getCapabilities                    controllers.SosDataController.getCapabilities(sosUrl: String)
   */
}
