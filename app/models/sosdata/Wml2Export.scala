package models.sosdata

import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneId}

import utils.{ClassnameLogger, GeoDateParserUtils}

import scala.xml.NodeSeq

class Wml2Export(timeZone: String) extends ClassnameLogger {

  lazy val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

  def getWml2ExportFromSosGetObs(xmlText: String, sosCapabilities: SosCapabilities): Option[NodeSeq] = {

    try {

      val nodeSeq = scala.xml.XML.loadString(xmlText)

      nodeSeq.head.label match {
        case "GetObservationResponse" =>

          val omMembers = (nodeSeq \\ "OM_Observation").map(
            node => <wml2:observationMember xsi:schemaLocation="http://www.opengis.net/waterml/2.0 http://www.opengis.net/waterml/2.0/waterml2.xsd">
              {node}
              </wml2:observationMember>
          )

          implicit val offsetDateTimeOrdering: Ordering[OffsetDateTime] = Ordering.by(e => e.toEpochSecond)

          val beginPositions = (omMembers \\ "beginPosition").map(node => node.text)
            .map(tpos => GeoDateParserUtils.parseDateStringAsOffsetDateTimeSingle(tpos).toOption).filter(_.isDefined).map(_.get).sorted.max

          val endPositions = (omMembers \\ "endPosition").map(node => node.text)
            .map(tpos => GeoDateParserUtils.parseDateStringAsOffsetDateTimeSingle(tpos).toOption).filter(_.isDefined).map(_.get).sorted.min

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

          // xml.Utility.escape() ?
          val gmlDescription =
            s"""Sac Gw Hub WaterML2.0 export:
               |title: ${sosCapabilities.title},
               |from service: ${sosCapabilities.sosUrl},
               |$serviceMeta
            """.stripMargin

          val resultCollection = <wml2:Collection xmlns:wml2="http://www.opengis.net/waterml/2.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:gml="http://www.opengis.net/gml/3.2" xmlns:om="http://www.opengis.net/om/2.0"
  xmlns:sa="http://www.opengis.net/sampling/2.0" xmlns:sams="http://www.opengis.net/samplingSpatial/2.0"
  xmlns:xlink="http://www.w3.org/1999/xlink"
  xsi:schemaLocation="http://www.opengis.net/waterml/2.0 http://www.opengis.net/waterml/2.0/waterml2.xsd"
  gml:id="SacGwHub.Col.1">
<gml:description>{gmlDescription}</gml:description>
<wml2:metadata>
  <wml2:DocumentMetadata gml:id="SacGwHub.DocMD.1">
    <wml2:generationDate>
      {updatedTime.format(formatter)}
    </wml2:generationDate>
    <wml2:generationSystem>Sac Gw Hub WaterML2.0 exporter</wml2:generationSystem>
  </wml2:DocumentMetadata>
</wml2:metadata>
<wml2:temporalExtent>
  <gml:TimePeriod gml:id="SacGwHub.TempExt.1">
    <gml:beginPosition>
      {beginPositions.format(formatter)}
    </gml:beginPosition>
    <gml:endPosition>
      {endPositions.format(formatter)}
    </gml:endPosition>
  </gml:TimePeriod>
</wml2:temporalExtent>
{omMembers}
</wml2:Collection>

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
