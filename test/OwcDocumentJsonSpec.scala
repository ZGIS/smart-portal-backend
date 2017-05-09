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

import java.net.URL
import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID

import org.locationtech.spatial4j.context.SpatialContext
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import models.owc._
import info.smart.models.owc._
import org.locationtech.spatial4j.io.ShapeIO
import org.locationtech.spatial4j.shape.{Rectangle, SpatialRelation}
import utils.ClassnameLogger

import scala.language.implicitConversions


/**
  * Test Spec for [[OwcDocument]] and co
  */
class OwcDocumentJsonSpec extends PlaySpec with ClassnameLogger {

  private lazy val ctx = SpatialContext.GEO
  private lazy val owcResource1 = this.getClass().getResource("owc/smart-nz.owc.json")
  private lazy val owcResource2 = this.getClass().getResource("owc/smart-sac.owc.json")
  private lazy val owcResource3 = this.getClass().getResource("owc/csw_10entries.owc.json")
  private lazy val owcResource4 = this.getClass().getResource("owc/wps_52north.owc.json")
  private lazy val owcResource5 = this.getClass().getResource("owc/smart-mond.owc.json")

  "JSON writer" should {

    val operation1 = OwcOperation(UUID.randomUUID(), "GetCapabilities", "GET", "application/xml", "https://data.linz.govt.nz/services;key=a8fb9bcd52684b7abe14dd4664ce9df9/wms?VERSION=1.3.0&REQUEST=GetCapabilities", Some(OwcPostRequestConfig(None, None)), Some(OwcRequestResult(None, None)))

    val operation2 = OwcOperation(UUID.randomUUID(), "GetMap", "GET", "image/png", "https://data.linz.govt.nz/services;key=a8fb9bcd52684b7abe14dd4664ce9df9/wms?VERSION=1.3&REQUEST=GetMap&SRS=EPSG:4326&BBOX=168,-45,182,-33&WIDTH=800&HEIGHT=600&LAYERS=layer-767&FORMAT=image/png&TRANSPARENT=TRUE&EXCEPTIONS=application/vnd.ogc.se_xml", None, None)

    val operation3 = OwcOperation(UUID.randomUUID(), "GetCapabilities", "GET", "application/xml", "http://portal.smart-project.info/pycsw/csw?SERVICE=CSW&VERSION=2.0.2&REQUEST=GetCapabilities", None, None)

    val operation4 = OwcOperation(UUID.randomUUID(), "GetRecordById", "POST", "application/xml", "http://portal.smart-project.info/pycsw/csw", Some(OwcPostRequestConfig(
            Some("application/xml"),
            Some("""<csw:GetRecordById xmlns:csw="http://www.opengis.net/cat/csw/2.0.2"
              |xmlns:gmd="http://www.isotc211.org/2005/gmd/" xmlns:gml="http://www.opengis.net/gml"
              |xmlns:ogc="http://www.opengis.net/ogc" xmlns:gco="http://www.isotc211.org/2005/gco"
              |xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              |outputFormat="application/xml" outputSchema="http://www.isotc211.org/2005/gmd"
              |service="CSW" version="2.0.2">
              |<csw:Id>urn:uuid:1f542dbe-a35d-46d7-9dff-64004226d21c-nz_aquifers</csw:Id>
              |<csw:ElementSetName>full</csw:ElementSetName>
              |</csw:GetRecordById>""".stripMargin)
          )), None)

    val operation5 = OwcOperation(UUID.randomUUID(), "GetCapabilities", "GET", "application/xml", "http://portal.smart-project.info/gs-smart/wfs?service=wfs&AcceptVersions=2.0.0&REQUEST=GetCapabilities", None, None)

    val operation6 = OwcOperation(UUID.randomUUID(), "GetFeature", "GET", "application/xml", "http://portal.smart-project.info/gs-smart/wfs?service=wfs&version=2.0.0&request=GetFeature&typename=gwml2:GW_ManagementArea&count=1", None, None)

    val offering1 = WmsOffering(
      UUID.randomUUID(),
      "http://www.opengis.net/spec/owc-geojson/1.0/req/wms",
      List(operation1, operation2),
      List()
    )

    val offering2 = CswOffering(
      UUID.randomUUID(),
      "http://www.opengis.net/spec/owc-geojson/1.0/req/csw",
      List(operation3, operation4),
      List()
    )

    val offering3 = WfsOffering(
      UUID.randomUUID(),
      "http://www.opengis.net/spec/owc-geojson/1.0/req/wms",
      List(operation5, operation6),
      List()
    )

    val offering4 = CswOffering(
      UUID.randomUUID(),
      "http://www.opengis.net/spec/owc-geojson/1.0/req/csw",
      List(operation3, operation4),
      List()
    )

    val link1 = OwcLink(UUID.randomUUID(), "profile", None, "http://www.opengis.net/spec/owc-atom/1.0/req/core", Some("This file is compliant with version 1.0 of OGC Context"))
    val link2 = OwcLink(UUID.randomUUID(), "self", Some("application/json"), "http://portal.smart-project.info/context/smart-sac.owc.json", None)
    val link3 = OwcLink(UUID.randomUUID(), "icon", Some("image/png"), "http://portal.smart-project.info/fs/images/nz_m.png", None)

    val category1 = OwcCategory(UUID.randomUUID(), "view-groups", "sac_add", Some("Informative Layers"))
    val category2 = OwcCategory(UUID.randomUUID(), "view-groups", "sac_geophys", Some("Sel. Geophysics"))
    val category3 = OwcCategory(UUID.randomUUID(), "view-groups", "sac_tracers", Some("Novel Tracers"))

    val category4 = OwcCategory(UUID.randomUUID(), "search-domain", "uncertainty", Some("Uncertainty of Models"))
    val category5 = OwcCategory(UUID.randomUUID(), "search-domain", "water-budget", Some("Water Budget"))

    val author1 = OwcAuthor(UUID.randomUUID(), "Alex K", None, None)
    val author2 = OwcAuthor(UUID.randomUUID(), "Alex Kmoch", Some(""), Some("http://gns.cri.nz"))
    val author3 = OwcAuthor(UUID.randomUUID(), "Alex Kmoch 2nd", Some("b.kmoch@gns.cri.nz"), Some("http://gns.cri.nz/1234"))
    val author4 = OwcAuthor(UUID.randomUUID(), "Alexander Kmoch", Some("c.kmoch@gns.cri.nz"), Some("http://gns.cri.nz"))

    val testTime1 = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())
    val testTime2 = ZonedDateTime.parse("2012-02-21T11:58:23Z")

    lazy val world = ctx.getShapeFactory().rect(-180.0, 180.0, -90.0, 90.0)

    val featureProps1 = OwcProperties(
      UUID.randomUUID(),
      "en",
      "NZ DTM 100x100",
      Some("Some Bla"),
      Some(testTime2),
      None,
      Some("CC BY SA 4.0 NZ"),
      List(author1),
      List(author2, author3),
      None,
      Some("GNS Science"),
      List(category1, category4),
      List(link3)
    )

    val featureProps2 = OwcProperties(
      UUID.randomUUID(),
      "en",
      "NZ SAC Recharge",
      Some("Some Bla Recharge"),
      Some(testTime1),
      None,
      Some("CC BY SA 4.0 NZ"),
      List(author2),
      List(author3, author4),
      None,
      Some("GNS Science"),
      List(category2, category5),
      List()
    )

    val documentProps1 = OwcProperties(
      UUID.randomUUID(),
      "en",
      "NZ SAC Recharge Case Study",
      Some("Some Bla Recharge and more"),
      Some(testTime2),
      None,
      Some("CC BY SA 4.0 NZ"),
      List(author1),
      List(author3, author4),
      None,
      Some("GNS Science"),
      List(category1, category2, category3, category4, category5),
      List(link1, link2, link3)
    )

    "encode OwcAuthors" in {
      Json.toJson(author1) mustEqual Json.parse("""{"name": "Alex K"}""")
      author1.toJson mustEqual Json.parse("""{"name": "Alex K"}""")
    }

    "encode OwcCategories" in {
      category3.toJson mustEqual Json.parse("""{"scheme": "view-groups","term": "sac_tracers","label": "Novel Tracers"}""")
      category4.toJson mustEqual Json.parse("""{"scheme": "search-domain","term": "uncertainty","label": "Uncertainty of Models"}""")
    }


    "encode OwcLinks" in {
      link1.toJson mustEqual Json.parse("""{"rel": "profile","href": "http://www.opengis.net/spec/owc-atom/1.0/req/core", "title": "This file is compliant with version 1.0 of OGC Context"}""")
      link2.toJson mustEqual Json.parse("""{"rel": "self", "type": "application/json", "href": "http://portal.smart-project.info/context/smart-sac.owc.json"}""")
    }

    "encode OwcProperties" in {
      val jsProperties = Json.parse("""{
                                                  |    "lang": "en",
                                                  |    "title": "NZ DTM 100x100",
                                                  |    "subtitle": "Some Bla",
                                                  |    "updated": "2012-02-21T11:58:23Z",
                                                  |    "rights": "CC BY SA 4.0 NZ",
                                                  |    "authors": [
                                                  |      {
                                                  |        "name": "Alex K"
                                                  |      }
                                                  |    ],
                                                  |    "contributors": [{
                                                  |        "name": "Alex Kmoch",
                                                  |        "email": "",
                                                  |        "uri": "http://gns.cri.nz"
                                                  |      },{
                                                  |        "name": "Alex Kmoch 2nd",
                                                  |        "email": "b.kmoch@gns.cri.nz",
                                                  |        "uri": "http://gns.cri.nz/1234"
                                                  |      }],
                                                  |      "publisher": "GNS Science",
                                                  |    "categories": [
                                                  |      {
                                                  |        "scheme": "view-groups",
                                                  |        "term": "sac_add",
                                                  |        "label": "Informative Layers"
                                                  |      },
                                                  |      {
                                                  |        "scheme": "search-domain",
                                                  |        "term": "uncertainty",
                                                  |        "label": "Uncertainty of Models"
                                                  |      }
                                                  |    ],
                                                  |    "links": [
                                                  |      {
                                                  |        "rel": "icon",
                                                  |        "type": "image/png",
                                                  |        "href": "http://portal.smart-project.info/fs/images/nz_m.png"
                                                  |      }
                                                  |    ]
                                                  |  }""".stripMargin)

      featureProps1.toJson mustEqual jsProperties

      val parsedProp = OwcProperties.parseJson(jsProperties)
      parsedProp.get.title mustEqual featureProps1.title
      parsedProp.get.authors.head.name mustEqual featureProps1.authors.head.name
      parsedProp.get.categories.head.term mustEqual featureProps1.categories.head.term

    }


    "encode OwcOperations" in {
      val jsOperations = Json.parse("""{
                                      |         "code": "GetCapabilities",
                                      |         "method": "GET",
                                      |         "type": "application/xml",
                                      |         "href": "https://data.linz.govt.nz/services;key=a8fb9bcd52684b7abe14dd4664ce9df9/wms?VERSION=1.3.0&REQUEST=GetCapabilities",
                                      |         "request": {},
                                      |         "result": {}
                                      |        }""".stripMargin)


      val parsedOps = OwcOperation.parseJson(jsOperations)
      parsedOps mustBe defined
      parsedOps.get.code mustEqual operation1.code
      parsedOps.get.href mustEqual operation1.href
      parsedOps.get.contentType mustEqual operation1.contentType

      operation1.toJson mustEqual jsOperations
    }


    "encode OwcOfferings" in {
      val jsOffering = offering1.toJson
      val jsCode = (jsOffering \ "code").get
      jsCode.as[String] mustEqual offering1.code

      val offering1BackResult = Json.fromJson[OwcOffering](jsOffering)
      offering1BackResult.get.code mustEqual offering1.code

    }

    "encode OwcEntries" in {
      val owcEntry1 = OwcEntry("http://portal.smart-project.info/context/smart-sac_add-nz-dtm-100x100", None, featureProps1, List(offering1, offering2))
      val owcEntry2 = OwcEntry("http://portal.smart-project.info/context/smart-sac_add-nz_aquifers", Some(world), featureProps2, List(offering3, offering4))

      val jsOwcEntry1 = owcEntry1.toJson

      val jsOwcEntry2impl = owcEntry2.toJson
      val jsOwcEntry2expl = Json.toJson(owcEntry2)

      jsOwcEntry2impl mustEqual jsOwcEntry2expl

      val backResult1 = Json.fromJson[OwcEntry](jsOwcEntry2impl)
      backResult1.isSuccess mustBe true
      backResult1.get.id mustEqual owcEntry2.id
      backResult1.get.bbox.get.relate(owcEntry2.bbox.get) mustBe SpatialRelation.WITHIN
      backResult1.get.properties.title mustEqual owcEntry2.properties.title
      backResult1.get.offerings.head.code mustEqual owcEntry2.offerings.head.code
    }

    "encode OwcDocuments" in {

      val owcEntry1 = OwcEntry("http://portal.smart-project.info/context/smart-sac_add-nz-dtm-100x100", None, featureProps1, List(offering1, offering2))
      val owcEntry2 = OwcEntry("http://portal.smart-project.info/context/smart-sac_add-nz_aquifers", Some(world), featureProps2, List(offering3, offering4))

      val owcDocument1 = OwcDocument("http://portal.smart-project.info/context/smart-sac", Some(world), documentProps1, List(owcEntry1, owcEntry2))

      val jsOwcDocument1 = owcDocument1.toJson
      // logger.warn(s"owc doc 1: ${Json.stringify(jsOwcDocument1)}")

      val backResult1 = Json.fromJson[OwcDocument](jsOwcDocument1)
      backResult1.isSuccess mustBe true

      backResult1.get.id mustEqual owcDocument1.id
      backResult1.get.bbox.get.relate(owcDocument1.bbox.get) mustBe SpatialRelation.WITHIN

      backResult1.get.properties.title mustEqual owcDocument1.properties.title
      backResult1.get.features.head.id mustEqual owcDocument1.features.head.id
      backResult1.get.features.head.offerings.head.code mustEqual owcDocument1.features.head.offerings.head.code

    }

    "parseWml2TvpSeries full OwcDocuments" in {

      val jsonTestCollection1 = scala.io.Source.fromURL(owcResource1).getLines.mkString
      val jsonTestCollection2 = scala.io.Source.fromURL(owcResource2).getLines.mkString
      val jsonTestCollection3 = scala.io.Source.fromURL(owcResource3).getLines.mkString
      val jsonTestCollection4 = scala.io.Source.fromURL(owcResource4).getLines.mkString
      val jsonTestCollection5 = scala.io.Source.fromURL(owcResource5).getLines.mkString

      val owcDoc1JsResult = OwcDocument.parseJson(jsonTestCollection1)
      owcDoc1JsResult mustBe defined
      val owcDoc1 = owcDoc1JsResult.get
      // Json.stringify(owcDoc1.get.toJson) mustEqual Json.stringify(Json.parseWml2TvpSeries(jsonTestCollection1))
      owcDoc1.id mustEqual "http://portal.smart-project.info/context/smart-nz"
      owcDoc1.features.size mustBe 16

      owcDoc1.features.map( feat => feat.id).filter(id => id.equalsIgnoreCase("http://portal.smart-project.info/context/smart-nz_other-layer-765")).size mustBe 1
      val opsList = owcDoc1.features.map( feat => feat.offerings ).flatten.map( offer => offer.operations).flatten
      opsList.size mustBe 64

      val owcDoc2JsResult = OwcDocument.parseJson(jsonTestCollection2)
      owcDoc2JsResult mustBe defined
      val owcDoc2 = owcDoc2JsResult.get
      owcDoc2.id mustEqual "http://portal.smart-project.info/context/smart-sac"
      owcDoc2.features.size mustBe 17

      owcDoc2.features.map( feat => feat.id).filter(id => id.equalsIgnoreCase("http://portal.smart-project.info/context/smart-sac_add-nz-dtm-100x100")).size mustBe 1
      val offerList = owcDoc1.features.map( feat => feat.offerings ).flatten
      val wms1 = offerList.filter{ offer =>
        offer.code.contains("wms")
      }.size
      val wms2 = offerList.map( offer => offer.operations ).flatten.filter {
        ops =>
          ops.code.equalsIgnoreCase("GetMap")
      }.size
      wms1 mustEqual wms2

      val owcDoc5JsResult = OwcDocument.parseJson(jsonTestCollection5)
      owcDoc5JsResult mustBe defined
      val owcDoc5 = owcDoc5JsResult.get
      owcDoc5.id mustEqual "http://portal.smart-project.info/context/smart-mond"
      owcDoc5.features.size mustBe 33

      owcDoc5.features.map( feat => feat.id).filter(id => id.equalsIgnoreCase("http://portal.smart-project.info/context/smart-mondsee_geol-DEM_BEV_10_wgs84")).size mustBe 1
      val opsList5 = owcDoc5.features.map( feat => feat.offerings ).flatten.map( offer => offer.operations).flatten
      opsList5.size mustBe 132

      opsList5.map {
        ops => ops.href
      }.map( href => new URL(href)).filter( url => url.isInstanceOf[URL]).size mustBe 132

      val owcDoc3 = OwcDocument.parseJson(jsonTestCollection3)
      owcDoc3 mustBe defined
      owcDoc3.get.bbox mustBe None
      owcDoc3.get.features.size mustBe 3
      owcDoc3.get.features.map (feat => feat.offerings ).flatten.map( offs => offs.operations).flatten.size mustBe 18

      val owcDoc4 = OwcDocument.parseJson(jsonTestCollection4)
      owcDoc4 mustBe defined

    }

  }

}
