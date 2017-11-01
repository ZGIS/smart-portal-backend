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

package models.sosdata

import java.io.File
import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneId}
import java.util.Date

import com.norbitltd.spoiwo.model.enums.CellFill
import com.norbitltd.spoiwo.model._
import models.tvp.XmlTvpParser
import org.joda.time.{DateTime, DateTimeZone}
import utils.{ClassnameLogger, GeoDateParserUtils}

import scala.io.Source
import scala.util.Try
import scala.xml.NodeSeq

class SpreadsheetExport(timeZone: String) extends ClassnameLogger {

  lazy val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
  lazy val xlsxDateStyle = CellStyle(dataFormat = CellDataFormat("m/d/yy h:mm"))

  def getSpreadsheetExportFromSosGetObs(xmlText: String, sosCapabilities: SosCapabilities, sosXmlRequest: String): Option[Sheet] = {

    try {

      val updatedTime = OffsetDateTime.now(ZoneId.of(timeZone))

      val serviceMeta = sosCapabilities.serviceMetadata.map(m =>
        s"""${m.providerName.map(a => s"providerName: $a;").getOrElse("")}
           |${m.providerSite.map(a => s"providerSite: $a;").getOrElse("")}
           |${m.abstrakt.map(a => s"abstract: $a;").getOrElse("")}
           |${m.fees.map(a => s"fees: $a;").getOrElse("fees: None mentioned;")}
           |${m.accessConstraints.map(a => s"accessConstraints: $a;").getOrElse("accessConstraints: None mentioned;")}
           |${m.serviceContactName.map(a => s"serviceContactName: $a;").getOrElse("serviceContactName: None mentioned;")}
           |${m.serviceContactEmail.map(a => s"serviceContactEmail: $a;").getOrElse("serviceContactEmail: None mentioned;")}
               """.stripMargin).getOrElse("No extra info available")

      val requestComment =
        s"""Following request was issued:
           |$sosXmlRequest
           |""".stripMargin

      // xml.Utility.escape() ?
      val description =
        s"""Sac Gw Hub WaterML2.0 export:
           |title: ${sosCapabilities.title},
           |from service: ${sosCapabilities.sosUrl},
           |$serviceMeta
           |$requestComment
              """.stripMargin


      val tvp = new XmlTvpParser().parseOm2Measurements(Source.fromString(xmlText))
      val headerStyle = CellStyle(fillPattern = CellFill.Solid, fillForegroundColor = Color.AquaMarine, font = Font(bold = true))

      val headerRow = Row(style = headerStyle).withCellValues("datetime", "timezone", "value", "unit", "feature id", "observedProperty id", "procedure id")

      val dataRows = tvp.map { t =>
        Row().withCells(
          Try {
            val of = OffsetDateTime.parse(t.datetime).atZoneSameInstant(ZoneId.of("UTC"))
            val jodaDate = new DateTime(of.toInstant.toEpochMilli, DateTimeZone.forID("UTC"))
            Cell(jodaDate, style = xlsxDateStyle)
          }.getOrElse(Cell.Empty),
          Cell(DateTimeZone.getDefault().toTimeZone.getID),
          Cell(t.measValue, style = CellStyle(dataFormat = CellDataFormat("0.00"))),
          Cell(t.measUnit),
          Cell(t.foiId),
          Cell(t.obsProp),
          Cell(t.procedure))
      }

      logger.debug(s"${tvp.length} points in tvp")

      val exportWorkbook = Sheet(name = "data").addRow(headerRow).addRows(dataRows)
        .withColumns(
          Column(index = 0, style = CellStyle(font = Font(bold = true)), autoSized = true)
        )

      // return the workbook before saving
      Some(exportWorkbook)

    } catch {
      //FIXME SR replace by specific exceptions
      case e: Exception => logger.warn(f"Exception on parsing GetObservationResponse: ${e.getMessage}", e)
        None
    }
  }
}
