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

package models.tvp

import utils.ClassnameLogger

import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.xml.MetaData
import scala.xml.pull._

class XmlTvpParser extends ClassnameLogger {

  private val X_HREF = "xlink:href"
  private val CODE = "code"
  private val GMLID = "identifier"

  private val OMOBS = "OM_Observation"
  private val OBSPROP = "observedProperty"
  private val PROC = "procedure"

  private val FOI = "featureOfInterest"
  private val SFPOS = "pos"

  private val TIMEPOS = "timePosition"
  private val TIME = "time"
  private val VALUE = "value"

  private val TVP = "MeasurementTVP"
  private val RESULT = "result"
  private val UOM = "uom"

  /**
    *
    * @param source
    * @return
    */
  def parseOm2Measurements(source: Source): Seq[TimeValuePair] = {

    val eventReader = new XMLEventReader(source)

    // phentime = "time";
    var datetime: String = null
    // GMLID = "identifier";
    var foiId: String = null
    // UOM = "uom";
    var measUnit: String = null
    // result = "value";
    var measValue: String = null
    // OBSPROP = "observedProperty";
    var geom: Option[String] = None
    // SFPOS = "pos";
    var obsProp: String = null
    // PROC = "procedure";
    var procedure: String = null

    val tvps = ArrayBuffer[TimeValuePair]()

    for (event <- eventReader) {
      event match {
        case EvElemStart(prefix, label, attributes, ns) => {
          if (label == OMOBS) {
            logger.trace(s"$prefix:$label start")
            // new obs
            obsProp = "" // OBSPROP = "observedProperty";
            foiId = "" // GMLID = "identifier";
            geom = None // SFPOS = "pos";
            measUnit = "" // UOM = "uom";
          }
          if (label == GMLID) {
            // this would be the observation id of the enclosing OM_Observation
            val next = eventReader.next().asInstanceOf[EvText]
            logger.trace(s"$prefix:$label ${next.text}")
          }

          if (label == OBSPROP) {
            val att = extractPrefixedAtrrib(attributes, X_HREF)
            logger.trace(s"$prefix:$label $att")
            obsProp = att
          }
          if (label == PROC) {
            val att = extractPrefixedAtrrib(attributes, X_HREF)
            logger.trace(s"$prefix:$label $att")
            procedure = att
          }
          if (label == TIMEPOS) {
            val next = eventReader.next().asInstanceOf[EvText]
            logger.trace(s"$prefix:$label ${next.text}")
            datetime = next.text
          }
          if (label == FOI) {
            // should check if actual geometry is in here or an xlink:href
            val att = extractPrefixedAtrrib(attributes, X_HREF)
            logger.trace(s"$prefix:$label $att")
            foiId = att
          }
          if (label == SFPOS) {
            val next = eventReader.next().asInstanceOf[EvText]
            logger.trace(s"$prefix:$label ${Some(next.text)}")
            geom = Some(next.text)
          }
          if (label == RESULT) {
            val next = eventReader.next().asInstanceOf[EvText]
            measUnit = extractAttrib(attributes, UOM)
            logger.trace(s"$prefix:$label ${next.text} ${extractAttrib(attributes, UOM)}")
            measValue = next.text
          }
        }
        // If we reach the end of an item element, we add it to the list
        case EvElemEnd(prefix, label) => {
          if (label == OMOBS) {
            logger.debug(s"$prefix:$label end")
            // full build and add
            val tvp = TimeValuePair(
              datetime,
              foiId,
              measUnit,
              measValue,
              geom,
              obsProp,
              procedure
            )
            logger.trace(tvp.toJson.toString())
            tvps.append(tvp)
          }
        }
        case EvText(t) => {
          if (t.trim.size > 0) {
            logger.debug(s"missed text: $t ")
          }
        }
        case _ => // ignore
      }
    }

    tvps.toSeq
  }

  /**
    *
    * @param attrs
    * @param prefixedLabel
    * @return
    */
  def extractPrefixedAtrrib(attrs: Iterable[MetaData], prefixedLabel: String): String = {
    val attOpt = attrs.find(meta => meta.prefixedKey.equalsIgnoreCase(prefixedLabel))
    attOpt.map(meta => meta.value.text).getOrElse("")
  }

  /**
    *
    * @param attrs
    * @param label
    * @return
    */
  def extractAttrib(attrs: Iterable[MetaData], label: String): String = {
    val attOpt = attrs.find(meta => meta.key.equalsIgnoreCase(label))
    attOpt.map(meta => meta.value.text).getOrElse("")
  }

  /**
    *
    * @param source
    * @return
    */
  def parseWml2TvpSeries(source: Source): Seq[TimeValuePair] = {

    val eventReader = new XMLEventReader(source)

    // TIME = "time";
    var datetime: String = null
    // GMLID = "identifier";
    var foiId: String = null
    // UOM = "uom";
    var measUnit: String = null
    // VALUE = "value";
    var measValue: String = null
    // SFPOS = "pos";
    var geom: Option[String] = None
    // OBSPROP = "observedProperty";
    var obsProp: String = null
    // PROC = "procedure";
    var procedure: String = null

    val tvps = ArrayBuffer[TimeValuePair]()

    for (event <- eventReader) {
      event match {
        case EvElemStart(prefix, label, attributes, ns) => {
          if (label == OMOBS) {
            logger.trace(s"$prefix:$label start")
            // new obs
            obsProp = "" // OBSPROP = "observedProperty";
            foiId = "" // GMLID = "identifier";
            geom = None // SFPOS = "pos";
            measUnit = "" // UOM = "uom";
          }
          if (label == GMLID) {
            // this would be the observation identifier of the enclosing OM_Observation
            val next = eventReader.next().asInstanceOf[EvText]
            logger.trace(s"$prefix:$label ${next.text}")
          }
          if (label == OBSPROP) {
            val att = extractPrefixedAtrrib(attributes, X_HREF)
            logger.trace(s"$prefix:$label $att")
            obsProp = att
          }
          if (label == PROC) {
            val att = extractPrefixedAtrrib(attributes, X_HREF)
            logger.trace(s"$prefix:$label $att")
            procedure = att
          }
          if (label == FOI) {
            // should check if actual geometry is in here or an xlink:href
            val att = extractPrefixedAtrrib(attributes, X_HREF)
            logger.trace(s"$prefix:$label $att")
            foiId = att
          }
          if (label == SFPOS) {
            val next = eventReader.next().asInstanceOf[EvText]
            logger.trace(s"$prefix:$label ${Some(next.text)}")
            geom = Some(next.text)
          }

          if (label == UOM) {
            measUnit = extractAttrib(attributes, CODE)
            logger.trace(s"$prefix:$label ${extractAttrib(attributes, CODE)}")
          }

          // here result contains the actual timeseriew, thus all former information is needed for many tvp points
          if (label == TIME) {
            val next = eventReader.next().asInstanceOf[EvText]
            logger.trace(s"$prefix:$label ${next.text}")
            datetime = next.text
          }
          if (label == VALUE) {
            val next = eventReader.next().asInstanceOf[EvText]
            logger.trace(s"$prefix:$label ${next.text}")
            measValue = next.text
          }
        }
        // If we reach the end of an tvp element, we already have to add it to the list
        case EvElemEnd(prefix, label) => {
          if (label == TVP) {
            logger.debug(s"$prefix:$label end")
            // full build and add
            val tvp = TimeValuePair(
              datetime,
              foiId,
              measUnit,
              measValue,
              geom,
              obsProp,
              procedure
            )
            logger.trace(tvp.toJson.toString())
            tvps.append(tvp)
          }
          // If we reach the end of an OM_observation element, we don't have to act really
          if (label == OMOBS) {
            logger.debug(s"$prefix:$label end")
            // refresh obsProp, procedure etc?
          }
        }
        case EvText(t) => {
          if (t.trim.size > 0) {
            logger.debug(s"missed text: $t ")
          }
        }
        case _ => // ignore
      }
    }

    tvps.toSeq
  }

}
