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
import scala.xml.{MetaData, NamespaceBinding}
import scala.xml.pull._

class Wml2TvpParser {

  private val OMOBS = "OM_Observation"
  private val OBSPROP = "observedProperty"
  private val PROC = "procedure"
  private val HREF = "xlink:href"

  private val MP = "MonitoringPoint"
  private val GMLID = "identifier"
  private val SFPOS = "pos"

  private val RESULT = "result"
  private val TVPMETA = "DefaultTVPMeasurementMetadata"
  private val UOM = "uom"
  private val CODE = "code"

  private val TVP = "MeasurementTVP"
  private val TIME = "time"
  private val VALUE = "value"

  /**
    *
    * @param source
    * @return
    */
  def parse(source: Source) : Seq[TimeValuePair] = {

    val eventReader = new XMLEventReader(source)

    var datetime: String = null  // TIME = "time";
    var foiId: String = null // GMLID = "identifier";
    var measUnit: String = null // UOM = "uom";
    var measValue: String = null // VALUE = "value";
    var geom: Option[String] = None // SFPOS = "pos";
    var obsProp: String = null // OBSPROP = "observedProperty";
    var procedure: String = null // PROC = "procedure";

    var tvps = ArrayBuffer[TimeValuePair]()

    for (event <- eventReader) {
      event match {
        case EvElemStart(prefix, label, attributes, ns) => {
          if (label == OMOBS) {

          }
          if (label == OBSPROP) {

          }
        }
        case EvElemEnd(prefix, label) => {

        }
        case e @ EvElemStart(prefix, label, attributes, ns) => {

        }
        case e @ EvElemEnd(prefix, label) => {

        }
        case EvText(t) => {

        }
        case _ => // ignore
      }
    }

    tvps.toSeq

  }

}
