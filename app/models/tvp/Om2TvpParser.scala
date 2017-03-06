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

package models.tvp

import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.xml.{Node, NodeSeq}
import scala.xml.pull._

class Om2TvpParser {

  private val HREF = "href"
  private val OMOBS = "OM_Observation"
  private val OBSPROP = "observedProperty"

  private val FOI = "featureOfInterest"
  private val GMLID = "identifier"
  private val SFPOS = "pos"

  private val PROC = "procedure"

  private val OMPHENTIME = "phenomenonTime"
  private val OMRESTIME = "resultTime"
  private val TIMEPOS = "timePosition"

  private val RESULT = "result"
  private val UOM = "uom"

  /**
    *
    * @param source
    * @return
    */
  def parse(source: Source): Seq[TimeValuePair] = {

    val eventReader = new XMLEventReader(source)

    var datetime: String = null  // phentime = "time";
    var foiId: String = null // GMLID = "identifier";
    var measUnit: String = null // UOM = "uom";
    var measValue: String = null // result = "value";
    var geom: Option[String] = None // SFPOS = "pos";
    var obsProp: String = null // OBSPROP = "observedProperty";
    var procedure: String = null // PROC = "procedure";

    var tvps = ArrayBuffer[TimeValuePair]()

    for (event <- eventReader) {
      event match {
        case EvElemStart(prefix, label, attributes, ns) => {
          if (label == OMOBS) {
            println(s"$label start")
            // new obs
            obsProp = "" // OBSPROP = "observedProperty";
            foiId = "" // GMLID = "identifier";
            geom = None // SFPOS = "pos";
            measUnit = "" // UOM = "uom";
          }
          if (label == GMLID) {
            val next = eventReader.next().asInstanceOf[EvText]
            println(s"$label ${next.text}")
          }

          if (label == OBSPROP) {
            val next = eventReader.next()
            val attOpt = attributes.find(meta => meta.prefixedKey.equalsIgnoreCase("xlink:href"))
            val att = attOpt.map( meta => meta.value.text).getOrElse("")
            println(s"$label $att")
          }
          if (label == PROC) {
            val att = attributes.get(HREF).map( _.text ).mkString(", ")
            println(s"$label $att")
          }
          if (label == TIMEPOS) {
            val att = attributes.get(GMLID).map( _.text ).mkString(", ")
            println(s"$label $att")
          }
          if (label == FOI) {
            val att = attributes.get(HREF).map( _.text ).mkString(", ")
            println(s"$label $att")
          }

          if (label == SFPOS) {
            val att = attributes.get(HREF).map( _.text ).mkString(", ")
            println(s"$label $att")
          }
          if (label == RESULT) {
            val att = attributes.get(HREF).map( _.text ).mkString(", ")
            println(s"$label $att")
          }
        }
        // If we reach the end of an item element, we add it to the list
        case EvElemEnd(prefix, label) => {
          if (label == OMOBS) {
            println(s"$label end")
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
          }
        }
        case e @ EvElemStart(prefix, label, attributes, ns) => {
          val att = attributes.get(HREF).map( _.text ).mkString(", ")
          println(s"@@ $label $att")
        }
        case e @ EvElemEnd(prefix, label) => {

        }
        case EvText(t) => {
          println(s"t $t ")
        }
        case _ => // ignore
      }
    }

    tvps.toSeq

  }

}
