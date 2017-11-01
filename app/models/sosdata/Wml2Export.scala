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

import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneId}

import utils.{ClassnameLogger, GeoDateParserUtils}

import scala.xml.NodeSeq

class Wml2Export(timeZone: String) extends ClassnameLogger {

  lazy val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

  def getWml2ExportFromSosGetObs(xmlText: String, sosCapabilities: SosCapabilities, sosXmlRequest: String): Option[NodeSeq] = {

    try {

      val nodeSeq = scala.xml.XML.loadString(xmlText)

      nodeSeq.head.label match {
        case "GetObservationResponse" =>

          val omMembers = (nodeSeq \\ "OM_Observation").map(
            node => <wml2:observationMember xsi:schemaLocation="http://www.opengis.net/waterml/2.0 http://www.opengis.net/waterml/2.0/waterml2.xsd">
  {node}
</wml2:observationMember>
          )

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

          val requestComment = s"""Following request was issued:
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

          val gmlDescriptionXml = <gml:description>{description}</gml:description>

          val resultCollection: NodeSeq = if (omMembers.length > 0) {

            implicit val offsetDateTimeOrdering: Ordering[OffsetDateTime] = Ordering.by(e => e.toEpochSecond)
            val beginPositions = (omMembers \\ "beginPosition").map(node => node.text)
              .map(tpos => GeoDateParserUtils.parseDateStringAsOffsetDateTimeSingle(tpos).toOption).filter(_.isDefined).map(_.get).sorted.max
            val endPositions = (omMembers \\ "endPosition").map(node => node.text)
              .map(tpos => GeoDateParserUtils.parseDateStringAsOffsetDateTimeSingle(tpos).toOption).filter(_.isDefined).map(_.get).sorted.min

            // return value
            val collection = <wml2:Collection xmlns:wml2="http://www.opengis.net/waterml/2.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
   xmlns:gml="http://www.opengis.net/gml/3.2" xmlns:om="http://www.opengis.net/om/2.0"
   xmlns:sa="http://www.opengis.net/sampling/2.0" xmlns:sams="http://www.opengis.net/samplingSpatial/2.0"
   xmlns:xlink="http://www.w3.org/1999/xlink"
   xsi:schemaLocation="http://www.opengis.net/waterml/2.0 http://www.opengis.net/waterml/2.0/waterml2.xsd"
   gml:id={"SacGwHub.Col." + updatedTime.toEpochSecond.toString}>
  {gmlDescriptionXml}
  <wml2:metadata>
    <wml2:DocumentMetadata gml:id={"SacGwHub.DocMD." + updatedTime.toEpochSecond.toString}>
      <wml2:generationDate>{updatedTime.format(formatter)}</wml2:generationDate>
      <wml2:generationSystem>Sac Gw Hub WaterML2.0 exporter</wml2:generationSystem>
    </wml2:DocumentMetadata>
  </wml2:metadata>
  <wml2:temporalExtent>
    <gml:TimePeriod gml:id={"SacGwHub.TempExt." + updatedTime.toEpochSecond.toString}>
      <gml:beginPosition>{beginPositions.format(formatter)}</gml:beginPosition>
      <gml:endPosition>{endPositions.format(formatter)}</gml:endPosition>
    </gml:TimePeriod>
  </wml2:temporalExtent>{omMembers}
</wml2:Collection>

            collection
          } else {

            // no observation members return value
            val collection = <wml2:Collection xmlns:wml2="http://www.opengis.net/waterml/2.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
   xmlns:gml="http://www.opengis.net/gml/3.2" xmlns:om="http://www.opengis.net/om/2.0"
   xmlns:sa="http://www.opengis.net/sampling/2.0" xmlns:sams="http://www.opengis.net/samplingSpatial/2.0"
   xmlns:xlink="http://www.w3.org/1999/xlink"
   xsi:schemaLocation="http://www.opengis.net/waterml/2.0 http://www.opengis.net/waterml/2.0/waterml2.xsd"
   gml:id={"SacGwHub.Col." + updatedTime.toEpochSecond.toString}>
  <!-- EMPTY COLLECTION, NO DATA FOUND -->
  {gmlDescriptionXml}
  <wml2:metadata>
    <wml2:DocumentMetadata gml:id={"SacGwHub.DocMD." + updatedTime.toEpochSecond.toString}>
      <wml2:generationDate>{updatedTime.format(formatter)}</wml2:generationDate>
      <wml2:generationSystem>Sac Gw Hub WaterML2.0 exporter</wml2:generationSystem>
    </wml2:DocumentMetadata>
  </wml2:metadata>
</wml2:Collection>

            collection
          }
          Some(resultCollection)
        case _ =>
          throw new IllegalArgumentException(f"Expected OperationsMetadataNode but found  ${nodeSeq.head.label}")
      }
    }
    catch {
      //FIXME SR replace by specific exceptions
      case e: Exception => logger.warn(f"Exception on parsing GetObservationResponse: ${e.getMessage}", e)
        None
    }
  }
}
