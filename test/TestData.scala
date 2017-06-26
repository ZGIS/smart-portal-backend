import java.net.URL
import java.util.UUID

import info.smart.models.owc100._
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

object TestData {

  val author1 = OwcAuthor(Some("Alex"), None, None, UUID.randomUUID())
  val author2 = OwcAuthor(Some("Alex K"), Some(EmailAddress("a.kmoch@gns.cri.nz")), None, UUID.randomUUID())
  val author3 = OwcAuthor(Some("Alex Kmoch"), Some(EmailAddress("a.kmoch@gns.cri.nz")), Some(new URL("http://gns.cri.nz")), UUID.randomUUID())

  val author3_1 = OwcAuthor(uuid = author3.uuid, name = author3.name, email = author3.email, uri = Some(new URL("https://www.gns.cri.nz")))

  val category1 = OwcCategory(uuid = UUID.randomUUID(), scheme = Some("view-groups"), term = "sac_add", label = Some("Informative Layers"))
  val category2 = OwcCategory(uuid = UUID.randomUUID(), scheme = "search-domain".toOption(), term = "uncertainty", label = Some("Uncertainty of Models"))
  val category3 = OwcCategory(uuid = UUID.randomUUID(), scheme = "glossary".toOption(), term = "uncertainty", label = Some("margin of error of a measurement"))

  val category3_1 = OwcCategory(uuid = category3.uuid, scheme = category3.scheme, term = category3.term, label = Some("Margin of Error of Measurements"))

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
    rel = "self",
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
    uuid = UUID.fromString("b9ea2498-fb32-40ef-91ef-0ba00060fe64"),
    content = None
  )

  val owccontent2 = OwcContent(
    mimeType = "application/gml+xml",
    url = Some(new URL("http://data.roads.wherever.com/wfs?service=WFS&request=GetFeature&typename=my_srf:RoadCollection")),
    title = Some("ID_ROADS1:M30"),
    uuid = UUID.fromString("b9ea2498-fb32-40ef-91ef-0ba00060fe64"),
    content = Some(xmlContent1)
  )

  val owccontent2_1: OwcContent = owccontent2.copy(title = Some("ID_ROADS1:M30 Updated"))

  val abstrakt = "SLD Cook Book: Simple Line extracted from http://docs.geoserver.org/latest/en/user/_downloads/line_simpleline.sld"

  val sldContent: String  =
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
    uuid = UUID.fromString("b9ea2498-fb32-40ef-91ef-0ba00060fe64"),
    content = None
  )

  val style2 = OwcStyleSet(
    name = "Simple Line",
    title = "SLD Cook Book: Simple Line",
    abstrakt = abstrakt.toOption(),
    default = Some(true),
    legendUrl = Some(new URL("http://docs.geoserver.org/latest/en/user/_images/line_simpleline1.png")),
    uuid = UUID.fromString("b9ea2498-fb32-40ef-91ef-0ba00060fe64"),
    content = Some(owccontent1)
  )

  val style3 = OwcStyleSet(
    name = "Simple Line",
    title = "SLD Cook Book: Simple Line",
    abstrakt = abstrakt.toOption(),
    default = Some(true),
    legendUrl = Some(new URL("http://docs.geoserver.org/latest/en/user/_images/line_simpleline1.png")),
    uuid = UUID.fromString("b9ea2498-fb32-40ef-91ef-0ba00060fe64"),
    content = Some(owccontent1.copy(content = Some(sldContent)))
  )

  val style3_1: OwcStyleSet = style3.copy(content = Some(owccontent1.copy(content = Some(xmlContent1))))
}
