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

import scala.util.Try

trait SosDataFormat {
  def value: String
  def contentType: String
  def fileSuffix: String
}

object SosDataFormat {

  case object OM20 extends SosDataFormat {
    val value = "http://www.opengis.net/om/2.0"
    val contentType = "application/xml"
    val fileSuffix = ".xml"
  }

  case object WML2 extends SosDataFormat {
    val value = "http://www.opengis.net/waterml/2.0"
    val contentType = "application/xml"
    val fileSuffix = ".wml"
  }

  case object XLS extends SosDataFormat {
    val value = "application/vnd.ms-excel"
    val contentType = "application/vnd.ms-excel"
    val fileSuffix = ".xlsx"
  }

  case object CSV extends SosDataFormat {
    val value = "text/csv"
    val contentType = "text/csv"
    val fileSuffix = ".csv"
  }

  def apply(v: String) : SosDataFormat = {
    v match {
      case "http://www.opengis.net/om/2.0" => OM20
      case "http://www.opengis.net/waterml/2.0" => WML2
      case "application/vnd.ms-excel" => XLS
      case "text/csv" => CSV
      case _ => throw new IllegalArgumentException(s"Value $v is not a supported data format value")
    }
  }

  val supportedParserFormats: List[String] = List(OM20.value, WML2.value)

  val supportedExportFormats: List[String] = List(WML2.value, XLS.value, CSV.value)

  def isValid(v: String) : Boolean = {
    Try (apply(v)).isSuccess
  }

  def isParser(f: SosDataFormat) : Boolean = {
    supportedParserFormats.contains(f.value)
  }

  def isExport(f: SosDataFormat) : Boolean = {
    supportedExportFormats.contains(f.value)
  }
}