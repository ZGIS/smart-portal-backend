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

package models.owc

import java.time.ZonedDateTime
import java.time.format.{DateTimeFormatter, DateTimeParseException}

import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.io.ShapeIO
import org.locationtech.spatial4j.shape.Rectangle
import utils.ClassnameLogger

/**
  * Model of OWS Context Documents and provide GeoJson encoding thereof (and maybe AtomXML)
  * An OWC document is an extended FeatureCollection, where the features (aka entries) hold a variety of metadata
  * about the things they provide the context for (i.e. other data sets, services, metadata records)
  * OWC documents do not duplicate a CSW MD_Metadata record, but a collection of referenced resources;
  *
  * http://www.opengeospatial.org/standards/owc
  *
  * Classically, the WMC documents (Web Map Context documents) were a list of WMS layers for a web map viewer
  * with a certain context, i.e. title, Bounding Box and a few visualisation properties like scale/zoom,
  * OWC has superseded that concept into a generic collection of resources:
  *
  * We use OWC primarily in the form of collections of case studies, preferably with at least two offerings per entry:
  * 1) a web visualisable form, e.g.WMS, WFS, SOS ...
  * 2) a CSW addressable MD_Metadata record according to the resource
  *
  * The OWC JSON Encoding is a profile of GeoJSON FeatureCollection, the XML encoding is a profile of Atom/GeoRSS Feed
  */
sealed trait OwcFeatureType {
  val id: String
  val bbox: Option[Rectangle]
  val properties: OwcProperties
}

/**
  * implicit type is Feature
  *
  * @param id
  * @param bbox
  * @param properties
  * @param offerings // aka resources
  */
case class OwcEntry(
                     id: String,
                     bbox: Option[Rectangle],
                     properties: OwcProperties,
                     offerings: List[OwcOffering] // special here of course
                   ) extends OwcFeatureType

/**
  * the OwcDocument wraps it all up
  * implicit type is FeatureCollection
  * properties.links must contain a profile link element
  *
  * @param id
  * @param bbox
  * @param properties
  * @param features // aka the entries
  */
case class OwcDocument(
                        id: String,
                        bbox: Option[Rectangle],
                        properties: OwcProperties,
                        features: List[OwcEntry] // special here of course
                      ) extends OwcFeatureType


/**
  * Companion Object to [[OwcDocument]]
  */
object OwcDocument extends ClassnameLogger {

  private lazy val ctx = SpatialContext.GEO
  private lazy val wktReader = ctx.getFormats().getReader(ShapeIO.WKT)
  private lazy val minLon = ctx.getWorldBounds.getMinX
  private lazy val maxLon = ctx.getWorldBounds.getMaxX
  private lazy val minLat = ctx.getWorldBounds.getMinY
  private lazy val maxLat = ctx.getWorldBounds.getMaxY

  /**
    * TODO AK ponder if precise ZoneInfo for parsedDate needed, alternatively either UTC or NZ TimeZone
    *
    * @param dateStringOpt
    * @return
    */
  def dateFromString(dateStringOpt: Option[String]): Option[ZonedDateTime] = {

    val yearMonthMatcher = """^\d\d\d\d-\d\d$""".r
    val yearMatcher = """^\d\d\d\d$""".r
    val isoInstantMatcher = """.*Z$""".r

    val acceptedDateFormats = List(
      DateTimeFormatter.ISO_INSTANT, //2011-12-03T10:15:30Z
      DateTimeFormatter.ISO_OFFSET_DATE_TIME, //2011-12-03T10:15:30+01:00 "2013-01-02T15:24:24.446-03:30"
      DateTimeFormatter.ISO_OFFSET_DATE, //2011-12-03+01:00
      DateTimeFormatter.ISO_LOCAL_DATE_TIME, //2011-12-03T10:15:30
      DateTimeFormatter.ISO_LOCAL_DATE, //2011-12-03
      DateTimeFormatter.BASIC_ISO_DATE //20111203
    )

    val datesList = dateStringOpt.map(
      dateString => acceptedDateFormats.map(//try all parsers per date
        df => {
          try {
            val correctedDateString = dateString match {
              case yearMonthMatcher() => dateString.concat("-01")
              case yearMatcher() => dateString.concat("-01-01")
              case isoInstantMatcher() => dateString.dropRight(1)
              case _ => dateString
            }
            Some(ZonedDateTime.parse(correctedDateString, df))
          }
          catch {
            case e: DateTimeParseException => None
          }
        }
      ).filter(_.isDefined)) //filter None and remove the Option

    if (datesList.isEmpty) {
      logger.warn(f"Could not parse the date option (${dateStringOpt})")
      None
    }
    if (datesList.size > 1) {
      logger.warn(f"Could parse ${datesList.size} values for (${dateStringOpt}) only returning first success")
    }
    datesList.head.head
  }

  /**
    * tries to naively prune the provided coordinates into good shape for WSG84
    * TODO DATE Line Wraps :-( ?
    * Rectangle rect(double minX, double maxX, double minY, double maxY);
    * bboxFromCoords(west, east, south, north)
    *
    * @param west most western value / minY
    * @param east most eastern value / maxY
    * @return tuple of viable coordinates in WSG84
    */
  def pruneLongitudeValues(west: Double, east: Double): (Double, Double) = {
    if (math.abs(west - east) > math.abs(minLon - maxLon)) {
      (minLon, maxLon) //in case the rectangle spans more than 360 deg make it world
    }
    else {
      val result = List(west, east).map({ (value: Double) =>
        value match {
          case n if value >= minLon && value <= maxLon => n
          case n if math.abs(value % math.abs(minLon - maxLon)) < maxLon => {
            val result = value % maxLon
            logger.warn(f"changing longitude value $n to $result")
            result
          }
          case _ => {
            val result = math.signum(value) * minLon + (value % maxLon)
            logger.warn(f"changing longitude value $value to $result")
            result
          }
        }
      })
      (result(0), result(1))
    }


  }

  /**
    * Cuts off latitudes outside of minLax / maxLat and swaps if south > north
    *
    * @param south most southern value / minY
    * @param north most northern value / maxY
    * @return tuple of viable coordinates
    */
  def pruneLatitudeValues(south: Double, north: Double): (Double, Double) = {
    //min/max in tuples swaps north/south if necessary,
    (Math.max(minLat, Math.min(south, north)),
      Math.min(maxLat, Math.max(south, north)))
  }

  /**
    * tries to build a bounding box rectangle as safely as possible from provided coordinates
    * Rectangle rect(double minX, double maxX, double minY, double maxY);
    * bboxFromCoords(west, east, south, north)
    *
    * @param west  most western value / minX
    * @param east  most eastern value / maxX
    * @param south most southern value / minY
    * @param north most northern value / maxY
    * @return the resulting bounding box
    */
  def bboxFromCoords(west: Double, east: Double, south: Double, north: Double): Rectangle = {
    val (prunedWest, prunedEast) = pruneLongitudeValues(west, east)
    val (prunedSouth, prunedNorth) = pruneLatitudeValues(south, north)

    val rect = ctx.getShapeFactory().rect(prunedWest, prunedEast, prunedSouth, prunedNorth)
    logger.debug(s"parsed rect ${rect.toString}")
    rect
  }
}