import java.net.URL
import java.time.{OffsetDateTime, ZoneId, ZonedDateTime}
import java.util.UUID

import info.smart.models.owc100._
import models.users.{StatusToken, User}
import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.shape.Rectangle
import uk.gov.hmrc.emailaddress.EmailAddress

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

import utils.StringUtils._

class DemoData {

  lazy val ctx = SpatialContext.GEO
  lazy val testTime: OffsetDateTime = OffsetDateTime.now(ZoneId.of("UTC"))
  lazy val world: Rectangle = ctx.getShapeFactory().rect(-180.0, 180.0, -90.0, 90.0)

  val author1 = OwcAuthor(Some("Alex"), None, None, UUID.randomUUID())
  val author2 = OwcAuthor(Some("Alex K"), Some(EmailAddress("a.kmoch@gns.cri.nz")), None, UUID.randomUUID())
  val author3 = OwcAuthor(Some("Alex Kmoch"), Some(EmailAddress("a.kmoch@gns.cri.nz")), Some(new URL("http://gns.cri.nz")), UUID.randomUUID())
  val author3_1: OwcAuthor = author3.copy(uri = Some(new URL("https://www.gns.cri.nz")))

  val creatorApp1 = OwcCreatorApplication(Some("GW HUB"), None, None, UUID.randomUUID())
  val creatorApp2 = OwcCreatorApplication(Some("GW HUB"), Some(new URL("http://gns.cri.nz")), None, UUID.randomUUID())
  val creatorApp3 = OwcCreatorApplication(Some("SAC GW HUB"), Some(new URL("http://gns.cri.nz")), Some("v0.9.0"), UUID.randomUUID())
  val creatorApp3_1: OwcCreatorApplication = creatorApp3.copy(uri = Some(new URL("https://portal.smart-project.info")))

  val displayApp1 = OwcCreatorDisplay(pixelWidth = Some(600), pixelHeight = Some(400), mmPerPixel = Some(0.28))
  val displayApp2 = OwcCreatorDisplay(pixelWidth = Some(600), pixelHeight = Some(400), mmPerPixel = Some(28))
  val displayApp3 = OwcCreatorDisplay(pixelWidth = Some(600), pixelHeight = Some(400), mmPerPixel = Some(2.235E+02))
  val displayApp3_1: OwcCreatorDisplay = displayApp3.copy(pixelWidth = Some(700))

  val category1 = OwcCategory(uuid = UUID.randomUUID(), scheme = Some("view-groups"), term = "sac_add", label = Some("Informative Layers"))
  val category2 = OwcCategory(uuid = UUID.randomUUID(), scheme = "search-domain".toOption(), term = "uncertainty", label = Some("Uncertainty of Models"))
  val category3 = OwcCategory(uuid = UUID.randomUUID(), scheme = "glossary".toOption(), term = "uncertainty", label = Some("margin of error of a measurement"))

  val category3_1: OwcCategory = category3.copy(label = Some("Margin of Error of Measurements"))

  val link1 = OwcLink(
    href = new URL("http://www.opengis.net/spec/owc-atom/1.0/req/core"),
    mimeType = None,
    lang = None,
    title = Some("This file is compliant with version 1.0 of OGC Context"),
    length = None,
    rel = "profile",
    uuid = UUID.randomUUID())


  val link2 = OwcLink(
    href = new URL("http://portal.smart-project.info/context/smart-sac.owc.json"),
    mimeType = Some("application/json"),
    lang = None,
    title = None,
    length = None,
    rel = "via",
    uuid = UUID.randomUUID())

  val link3 = OwcLink(
    href = new URL("http://portal.smart-project.info/fs/images/nz_m.png"),
    mimeType = Some("image/png"),
    lang = None,
    title = None,
    length = None,
    rel = "icon",
    uuid = UUID.randomUUID())

  val link3_1: OwcLink = link3.copy(title = Some("New Zealand Flag"))

  val xmlContent1: String =
    """<my_srf:RoadCollection gml:id="ID_ROADS1" xsi:schemaLocation="http://www.opengis.net/gml/3.2
      | http://schemas.opengis.net/gml/3.2.1/gml.xsd http://www.opengis.net/owc/1.0/examples/gml/1 road.xsd"
      | xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:gml="http://www.opengis.net/gml/3.2"
      | xmlns:my_srf="http://www.opengis.net/owc/1.0/examples/example1">
      | <my_srf:road><my_srf:Road gml:id="ID_ROAD1">
      | <my_srf:position><gml:LineString gml:id="ID_LINEROAD1">300 200</gml:pos><gml:pos>350 222</gml:pos>
      | </gml:LineString></my_srf:position>
      | <my_srf:width>4.1</my_srf:width><my_srf:name>M30</my_srf:name></my_srf:Road></my_srf:road>
      |</my_srf:RoadCollection>""".stripMargin

  val owccontent1 = OwcContent(
    mimeType = "application/gml+xml",
    url = Some(new URL("http://data.roads.wherever.com/wfs?service=WFS&request=GetFeature&typename=my_srf:RoadCollection")),
    title = Some("ID_ROADS1:M30"),
    content = None,
    uuid = UUID.fromString("65e36b29-6f2f-4cc2-8d0e-921bce565111")
  )

  val owccontent2 = OwcContent(
    mimeType = "application/gml+xml",
    url = Some(new URL("http://data.roads.wherever.com/wfs?service=WFS&request=GetFeature&typename=my_srf:RoadCollection")),
    title = Some("ID_ROADS1:M30"),
    content = Some(xmlContent1),
    uuid = UUID.fromString("65e36b29-6f2f-4cc2-8d0e-921bce565222")
  )

  val owccontent2_1: OwcContent = owccontent2.copy(title = Some("ID_ROADS1:M30 Updated"))

  val abstrakt = "SLD Cook Book: Simple Line extracted from http://docs.geoserver.org/latest/en/user/_downloads/line_simpleline.sld"

  val sldContent: String =
    """<StyledLayerDescriptor version="1.0.0"
      | xmlns="http://www.opengis.net/sld" xmlns:ogc="http://www.opengis.net/ogc"
      | xmlns:xlink="http://www.w3.org/1999/xlink"
      | xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      | xsi:schemaLocation="http://www.opengis.net/sld
      |   ../../../sld/1.1/StyledLayerDescriptor.xsd">
      |<NamedLayer><Name>Simple Line</Name>
      |<UserStyle><Title>SLD Cook Book: Simple Line</Title>
      |<FeatureTypeStyle><Rule><LineSymbolizer><Stroke>
      |<CssParameter name="stroke">#000000</CssParameter>
      |<CssParameter name="strokewidth">3</CssParameter></Stroke></LineSymbolizer></Rule></FeatureTypeStyle>
      |</UserStyle></NamedLayer></StyledLayerDescriptor>""".stripMargin

  val style1 = OwcStyleSet(
    name = "Simple Line",
    title = "SLD Cook Book: Simple Line",
    abstrakt = abstrakt.toOption(),
    default = Some(true),
    legendUrl = Some(new URL("http://docs.geoserver.org/latest/en/user/_images/line_simpleline1.png")),
    content = None,
    uuid = UUID.fromString("65e36b29-6f2f-4cc2-8d0e-921bce565333")
  )

  val style2 = OwcStyleSet(
    name = "Simple Line",
    title = "SLD Cook Book: Simple Line",
    abstrakt = abstrakt.toOption(),
    default = Some(true),
    legendUrl = Some(new URL("http://docs.geoserver.org/latest/en/user/_images/line_simpleline1.png")),
    content = Some(owccontent1.copy(uuid = UUID.fromString("65e36b29-6f2f-4cc2-8d0e-921bce565444")))
  )

  val style3 = OwcStyleSet(
    name = "Simple Line",
    title = "SLD Cook Book: Simple Line",
    abstrakt = abstrakt.toOption(),
    default = Some(true),
    legendUrl = Some(new URL("http://docs.geoserver.org/latest/en/user/_images/line_simpleline1.png")),
    content = Some(owccontent1.copy(content = Some(sldContent),
      uuid = UUID.fromString("65e36b29-6f2f-4cc2-8d0e-921bce565555"))),
    uuid = UUID.fromString("65e36b29-6f2f-4cc2-8d0e-921bce565666")
  )

  val style3_1: OwcStyleSet = style3.copy(content = Some(owccontent1.copy(content = Some(xmlContent1))))

  val operation1 = OwcOperation(
    code = "GetCapabilities",
    method = "GET",
    mimeType = Some("application/xml"),
    requestUrl = new URL("https://data.linz.govt.nz/services;key=a8fb9bcd52684b7abe14dd4664ce9df9/wms?VERSION=1.3.0&REQUEST=GetCapabilities"),
    request = None,
    result = None)

  val operation2 = OwcOperation(
    code = "GetMap",
    method = "GET",
    mimeType = Some("image/png"),
    requestUrl = new URL("https://data.linz.govt.nz/services;key=a8fb9bcd52684b7abe14dd4664ce9df9/wms?VERSION=1.3&REQUEST=GetMap&SRS=EPSG:4326&BBOX=168,-45,182,-33&WIDTH=800&HEIGHT=600&LAYERS=layer-767&FORMAT=image/png&TRANSPARENT=TRUE&EXCEPTIONS=application/vnd.ogc.se_xml"),
    request = None,
    result = None)

  val operation3 = OwcOperation(
    code = "GetFeature",
    method = "GET",
    mimeType = Some("application/xml"),
    requestUrl = new URL("http://portal.smart-project.info/geoserver/wfs?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature"),
    request = None,
    result = Some(owccontent1.copy(uuid = UUID.fromString("65e36b29-6f2f-4cc2-8d0e-921bce565777"))))

  val operation3_1: OwcOperation = operation3.copy(mimeType = Some("application/geojson"))

  val operation4 = OwcOperation(
    code = "GetRecordById",
    method = "POST",
    mimeType = Some("application/xml"),
    requestUrl = new URL("http://portal.smart-project.info/pycsw/csw"),
    request = Some(OwcContent(
      "application/xml",
      None,
      None,
      Some(
        """<csw:GetRecordById xmlns:csw="http://www.opengis.net/cat/csw/2.0.2"
          |xmlns:gmd="http://www.isotc211.org/2005/gmd/" xmlns:gml="http://www.opengis.net/gml"
          |xmlns:ogc="http://www.opengis.net/ogc" xmlns:gco="http://www.isotc211.org/2005/gco"
          |xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          |outputFormat="application/xml" outputSchema="http://www.isotc211.org/2005/gmd"
          |service="CSW" version="2.0.2">
          |<csw:Id>urn:uuid:1f542dbe-a35d-46d7-9dff-64004226d21c-nz_aquifers</csw:Id>
          |<csw:ElementSetName>full</csw:ElementSetName>
          |</csw:GetRecordById>""".stripMargin),
      uuid = UUID.fromString("65e36b29-6f2f-4cc2-8d0e-921bce565888"))
    ),
    result = None,
    uuid = UUID.fromString("65e36b29-6f2f-4cc2-8d0e-921bce565999"))

  val operation5 = OwcOperation(
    code = "GetCapabilities",
    method = "GET",
    mimeType = Some("application/xml"),
    requestUrl = new URL("http://portal.smart-project.info/gs-smart/wfs?service=wfs&AcceptVersions=2.0.0&REQUEST=GetCapabilities"),
    request = None,
    result = None)

  val operation6 = OwcOperation(
    code = "GetFeature",
    method = "GET",
    mimeType = Some("application/xml"),
    requestUrl = new URL("http://portal.smart-project.info/gs-smart/wfs?service=wfs&version=2.0.0&request=GetFeature&typename=gwml2:GW_ManagementArea&count=1"),
    request = None,
    result = None)

  val offering1 = OwcOffering(
    uuid = UUID.randomUUID(),
    code = OwcOfferingType.WMS.code,
    operations = List(operation1.copy(uuid = UUID.randomUUID()), operation2.copy(uuid = UUID.randomUUID())),
    contents = List(owccontent1.copy(uuid = UUID.fromString("f811bf08-a158-452b-8c18-3e43c3fdceee"))),
    styles = List(style1.copy(uuid = UUID.randomUUID()),
      style2.copy(
        uuid = UUID.fromString("f811bf08-a158-452b-8c18-3e43c3fdcfff"),
        content = Some(style2.content.get.copy(uuid = UUID.fromString("f811bf08-a158-452b-8c18-3e43c3fdcbbb")))))
  )

  val offering2 = OwcOffering(
    uuid = UUID.randomUUID(),
    code = OwcOfferingType.CSW.code,
    operations = List(operation3.copy(uuid = UUID.randomUUID()), operation4.copy(uuid = UUID.randomUUID())),
    contents = List(owccontent2.copy(uuid = UUID.fromString("f811bf08-a158-452b-8c18-3e43c3fdc000"))),
    styles = List()
  )

  val offering2_1: OwcOffering = offering2.copy(styles = List(style1.copy(uuid = UUID.randomUUID())))

  val offering3 = OwcOffering(
    uuid = UUID.randomUUID(),
    code = OwcOfferingType.WFS.code,
    operations = List(operation5.copy(uuid = UUID.randomUUID()), operation6.copy(uuid = UUID.randomUUID())),
    contents = List(),
    styles = List()
  )

  val offering4 = OwcOffering(
    uuid = UUID.randomUUID(),
    code = OwcOfferingType.CSW.code,
    operations = List(operation1.copy(uuid = UUID.randomUUID()), operation4.copy(
      uuid = UUID.randomUUID(),
      request = Some(operation4.request.get.copy(uuid = UUID.randomUUID())))),
    contents = List(),
    styles = List()
  )

  val owcResource1 = OwcResource(
    id = new URL("http://portal.smart-project.info/context/resource/smart-sac"),
    title = "NZ DTM 100x100",
    subtitle = Some("Some Bla"),
    updateDate = testTime,
    author = List(author2.copy(uuid = UUID.randomUUID())),
    publisher = Some("GNS Science"),
    rights = Some("CC BY SA 4.0 NZ"),
    geospatialExtent = Some(world),
    temporalExtent = None,
    contentDescription = List(), // links.alternates[] and rel=alternate
    preview = List(link3.copy(uuid = UUID.randomUUID())), // aka links.previews[] and rel=icon (atom)
    contentByRef = List(), // aka links.data[] and rel=enclosure (atom)
    offering = List(offering1.copy(uuid = UUID.randomUUID()), offering2.copy(uuid = UUID.randomUUID())),
    active = Some(true),
    resourceMetadata = List(), // aka links.via[] & rel=via
    keyword = List(category1.copy(uuid = UUID.randomUUID())),
    minScaleDenominator = None,
    maxScaleDenominator = None,
    folder = category1.label)

  val owcResource2 = OwcResource(
    id = new URL("http://portal.smart-project.info/context/resource/smart-sac-demo"),
    title = "NZ SAC Recharge",
    subtitle = Some("Some Bla Recharge"),
    updateDate = testTime,
    author = List(author3.copy(uuid = UUID.randomUUID())),
    publisher = Some("GNS Science"),
    rights = Some("CC BY SA 4.0 NZ"),
    geospatialExtent = Some(world),
    temporalExtent = None,
    contentDescription = List(), // links.alternates[] and rel=alternate
    preview = List(link3.copy(uuid = UUID.randomUUID())), // aka links.previews[] and rel=icon (atom)
    contentByRef = List(), // aka links.data[] and rel=enclosure (atom)
    offering = List(offering3.copy(uuid = UUID.randomUUID()), offering4.copy(uuid = UUID.randomUUID())),
    active = Some(true),
    resourceMetadata = List(), // aka links.via[] & rel=via
    keyword = List(category2.copy(uuid = UUID.randomUUID())),
    minScaleDenominator = None,
    maxScaleDenominator = None,
    folder = Some("/sac/data/upload"))

  val owcResource2_1: OwcResource = owcResource2.copy(
    temporalExtent = Some(List(testTime.minusDays(7), testTime)),
    subtitle = Some("Some Bla Recharge Updated"),
    contentByRef = List(OwcLink(href = new URL("http://data.gns.cri.nz/files/234567-234567-345678.bin"),
      mimeType = Some("application/octet-stream"),
      lang = None,
      title = Some("234567-234567-345678.bin"),
      length = Some(123456),
      rel = "enclosure")))

  val owcContext1 = OwcContext(
    id = new URL("http://portal.smart-project.info/context/smart-sac"),
    areaOfInterest = Some(world),
    specReference = List(OwcProfile.CORE.value.copy(uuid = UUID.randomUUID())), // aka links.profiles[] & rel=profile
    contextMetadata = List(link2.copy(uuid = UUID.randomUUID())), // aka links.via[] & rel=via
    language = "en",
    title = "NZ SAC Recharge Case Study",
    subtitle = Some("Some Bla Recharge and more"),
    updateDate = testTime,
    author = List(author3.copy(uuid = UUID.randomUUID())),
    publisher = Some("GNS Science"),
    creatorApplication = None,
    creatorDisplay = Some(OwcCreatorDisplay(pixelWidth = Some(1024), pixelHeight = Some(860), mmPerPixel = None)),
    rights = Some("CC BY SA 4.0 NZ"),
    timeIntervalOfInterest = None,
    keyword = List(category1.copy(uuid = UUID.randomUUID()), category2.copy(uuid = UUID.randomUUID()), category3.copy(uuid = UUID.randomUUID())),
    resource = List(owcResource1.copy(id = new URL("http://portal.smart-project.info/context/resource/smart-sac_copy")),
      owcResource2.copy(id = new URL("http://portal.smart-project.info/context/resource/smart-sac-demo_copy"))))

  val owcContext2 = OwcContext(
    id = new URL("http://portal.smart-project.info/context/smart-sac2"),
    areaOfInterest = Some(world),
    specReference = List(OwcProfile.CORE.value.copy(uuid = UUID.randomUUID())), // aka links.profiles[] & rel=profile
    contextMetadata = List(link2.copy(uuid = UUID.randomUUID())), // aka links.via[] & rel=via
    language = "en",
    title = "NZ SAC Recharge Case Study 2",
    subtitle = Some("Some Bla Recharge and more"),
    updateDate = testTime,
    author = List(author3.copy(uuid = UUID.randomUUID())),
    publisher = Some("GNS Science"),
    creatorApplication = None,
    creatorDisplay = Some(OwcCreatorDisplay(pixelWidth = Some(1024), pixelHeight = Some(860), mmPerPixel = None)),
    rights = Some("CC BY SA 4.0 NZ"),
    timeIntervalOfInterest = None,
    keyword = List(),
    resource = List())

  def testUser1(cryptPass: String) = User(EmailAddress("test@blubb.com"),
    "local:test@blubb.com",
    "Hans",
    "Wurst",
    cryptPass,
    s"${StatusToken.ACTIVE}:REGCONFIRMED",
    testTime.toZonedDateTime)

  def testUser2(cryptPass: String) = User(EmailAddress("test2@blubb.com"),
    "local:test2@blubb.com",
    "Hans",
    "Wurst",
    cryptPass,
    s"${StatusToken.REGISTERED}:XYZ123",
    testTime.toZonedDateTime)

  def testUser3(cryptPass: String) = User(EmailAddress("testuser@test.com"),
    "local:testuser@test.com",
    "Test",
    "User",
    cryptPass,
    s"${StatusToken.ACTIVE}:REGCONFIRMED",
    ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault()))
}
