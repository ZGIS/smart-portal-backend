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

package utils

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.io.ShapeIO
import org.locationtech.spatial4j.shape.Rectangle

import scala.util.Try

/**
  * custom utils
  */
object GeoDateParserUtils extends ClassnameLogger {

  private lazy val ctx = SpatialContext.GEO
  private lazy val wktReader = ctx.getFormats().getReader(ShapeIO.WKT)

  /**
    * quick and dirty bbox from wkt stored in DB
    *
    * @param bboxAsWkt
    * @return
    */
  def createOptionalBbox(bboxAsWkt: Option[String]): Option[Rectangle] = {
    if (bboxAsWkt.isDefined) {
      Try {
        wktReader.read(bboxAsWkt.get).asInstanceOf[Rectangle]
      }.toOption
    } else {
      None
    }
  }

  /**
    * quick and dirty string from a bbox to store wkt in DB
    *
    * @param rect
    * @return
    */
  def rectToWkt(rect: Rectangle): String = {
    val shpWriter = ctx.getFormats().getWriter(ShapeIO.WKT)
    shpWriter.toString(rect)
  }

  /**
    *
    * @param dateStringOption
    * @return
    */
  def parseOffsetDateString(dateStringOption: Option[String]): Option[List[OffsetDateTime]] = {
    if (dateStringOption.isDefined) {
      val isoTemporalString = dateStringOption.get
      if (isoTemporalString.contains("/")) {
        parseDateStringAsOffsetInterval(isoTemporalString).toOption
      } else {
        parseDateStringAsOffsetDateTime(isoTemporalString).toOption
      }
    } else {
      None
    }
  }

  /**
    *
    * @param isoTemporalString
    * @return
    */
  private def parseDateStringAsOffsetDateTime(isoTemporalString: String): Try[List[OffsetDateTime]] = {
    Try {
      val date = OffsetDateTime.parse(isoTemporalString)
      List(date)
    }
  }

  /**
    *
    * @param isoTemporalString
    * @return
    */
  private def parseDateStringAsOffsetInterval(isoTemporalString: String): Try[List[OffsetDateTime]] = {
    Try {
      val dateStrings = isoTemporalString.replace("\"", "").trim.split("/").toList
      val date1 = OffsetDateTime.parse(dateStrings.head)
      val date2 = OffsetDateTime.parse(dateStrings.last)
      List(date1, date2)
    }
  }

  /**
    * String serialiser to store either single offset date or the interval in DB
    *
    * @param dateRange
    * @return
    */
  def writeOffsetDatesAsDateString(dateRange: List[OffsetDateTime]): Option[String] = {
    lazy val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    val dates = dateRange.sortWith((a, b) => a.isBefore(b))
    if (dates.head == dates.last) {
      Try(dates.head.format(formatter)).toOption
    } else {
      Try {
        val d1 = dates.head.format(formatter)
        val d2 = dates.last.format(formatter)
        d1 + "/" + d2
      }.toOption
    }
  }

}
