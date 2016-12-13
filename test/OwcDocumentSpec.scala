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


import java.net.InetAddress
import java.time.{ZoneId, ZonedDateTime}

import anorm.SQL
import com.typesafe.config.ConfigFactory
import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.shape._
import org.scalatest.{BeforeAndAfter, Ignore, TestData}
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.libs.json._
import models._
import models.owc._
import play.api.db.evolutions.{ClassLoaderEvolutionsReader, Evolutions}
import play.api.{Application, Configuration}
import play.api.inject.guice.GuiceApplicationBuilder
import java.util.UUID

/**
  * Test Spec for [[OwcDocumentDAO]] with [[OwcDocument]] and [[OwcEntry]]
  */
class OwcDocumentSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.testdev.conf"))).build()

  before {

  }

  after {

  }

  private lazy val owcResource = this.getClass().getResource("owc/smart-nz.owc.json")
  private lazy val ctx = SpatialContext.GEO

  "OwcDocument " can {

    val operation1 = OwcOperation(
      UUID.randomUUID(),
      "GetCapabilities",
      "GET",
      "application/xml",
      "https://data.linz.govt.nz/services;key=a8fb9bcd52684b7abe14dd4664ce9df9/wms?VERSION=1.3.0&REQUEST=GetCapabilities",
      None,
      None
    )

    val operation2 = OwcOperation(
      UUID.randomUUID(),
      "GetMap",
      "GET",
      "image/png",
      "https://data.linz.govt.nz/services;key=a8fb9bcd52684b7abe14dd4664ce9df9/wms?VERSION=1.3&REQUEST=GetMap&SRS=EPSG:4326&BBOX=168,-45,182,-33&WIDTH=800&HEIGHT=600&LAYERS=layer-767&FORMAT=image/png&TRANSPARENT=TRUE&EXCEPTIONS=application/vnd.ogc.se_xml",
      None,
      None
    )

    val operation3 = OwcOperation(
      UUID.randomUUID(),
      "GetCapabilities",
      "GET",
      "application/xml",
      "http://portal.smart-project.info/pycsw/csw?SERVICE=CSW&VERSION=2.0.2&REQUEST=GetCapabilities",
      None,
      None
    )

    val operation4 = OwcOperation(
      UUID.randomUUID(),
      "GetRecordsById",
      "POST",
      "application/xml",
      "http://portal.smart-project.info/pycsw/csw",
      Some(OwcPostRequestConfig(
        "application/xml",
        """<csw:GetRecordById xmlns:csw="http://www.opengis.net/cat/csw/2.0.2"
          |xmlns:gmd="http://www.isotc211.org/2005/gmd/" xmlns:gml="http://www.opengis.net/gml"
          |xmlns:ogc="http://www.opengis.net/ogc" xmlns:gco="http://www.isotc211.org/2005/gco"
          |xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          |outputFormat="application/xml" outputSchema="http://www.isotc211.org/2005/gmd"
          |service="CSW" version="2.0.2">
          |<csw:Id>urn:uuid:1f542dbe-a35d-46d7-9dff-64004226d21c-nz_aquifers</csw:Id>
          |<csw:ElementSetName>full</csw:ElementSetName>
          |</csw:GetRecordById>""".stripMargin
      )),
      None
    )

    val operation5 = OwcOperation(
      UUID.randomUUID(),
      "GetCapabilities",
      "GET",
      "application/xml",
      "http://portal.smart-project.info/gs-smart/wfs?service=wfs&AcceptVersions=2.0.0&REQUEST=GetCapabilities",
      None,
      None
    )

    val operation6 = OwcOperation(
      UUID.randomUUID(),
      "GetFeature",
      "GET",
      "application/xml",
      "http://portal.smart-project.info/gs-smart/wfs?service=wfs&version=2.0.0&request=GetFeature&typename=gwml2:GW_ManagementArea&count=1",
      None,
      None
    )

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

    val author1 = OwcAuthor(UUID.randomUUID(), "Alex K", Some(""), None)
    val author2 = OwcAuthor(UUID.randomUUID(), "Alex Kmoch", Some("a.kmoch@gns.cri.nz"), Some("http://gns.cri.nz"))
    val author3 = OwcAuthor(UUID.randomUUID(), "Alex Kmoch 2nd", Some("b.kmoch@gns.cri.nz"), Some("http://gns.cri.nz/1234"))
    val author4 = OwcAuthor(UUID.randomUUID(), "Alexander Kmoch", Some("c.kmoch@gns.cri.nz"), Some("http://gns.cri.nz"))

    val testTime = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())
    lazy val world = ctx.getShapeFactory().rect(-180.0, 180.0, -90.0, 90.0)

    val featureProps1 = OwcProperties(
      UUID.randomUUID(),
      "en",
      "NZ DTM 100x100",
      Some("Some Bla"),
      Some(testTime),
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
      Some(testTime),
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
      Some(testTime),
      None,
      Some("CC BY SA 4.0 NZ"),
      List(author1),
      List(author3, author4),
      None,
      Some("GNS Science"),
      List(category1, category2, category3, category4, category5),
      List(link1, link2, link3)
    )

    "handle OwcEntries with DB" in {
      withTestDatabase { database =>

        val owcPropertiesDAO = new OwcPropertiesDAO(database)
        val owcOfferingDAO = new OwcOfferingDAO(database)
        val owcDocumentDAO = new OwcDocumentDAO(database, owcOfferingDAO, owcPropertiesDAO)

        val owcEntry1 = OwcEntry("http://portal.smart-project.info/context/smart-sac", Some(world), featureProps1, List(offering1, offering2))
        val owcEntry2 = OwcEntry("http://portal.smart-project.info/context/smart-sac-demo", Some(world), featureProps2, List(offering3, offering4))

        owcDocumentDAO.getAllOwcEntries.size mustEqual 0

        owcDocumentDAO.createOwcEntry(owcEntry1) mustEqual Some(owcEntry1)
        owcDocumentDAO.createOwcEntry(owcEntry2) mustEqual Some(owcEntry2)
        owcDocumentDAO.getAllOwcEntries.size mustEqual 2

        val thrown = the[java.sql.SQLException] thrownBy owcDocumentDAO.createOwcEntry(owcEntry2)
        thrown.getErrorCode mustEqual 23505

        val entries = owcDocumentDAO.findOwcEntriesById(owcEntry1.id)
        entries.size mustEqual 1
        entries.headOption.get.id mustEqual "http://portal.smart-project.info/context/smart-sac"

        owcOfferingDAO.findOwcOperationByCode("GetCapabilities").size mustBe 3
        owcPropertiesDAO.getAllOwcProperties.size mustBe 2
        owcPropertiesDAO.getAllOwcCategories.size mustBe 4

        owcDocumentDAO.deleteOwcEntry(owcEntry2) mustEqual true
        owcDocumentDAO.getAllOwcEntries.size mustEqual 1
        owcOfferingDAO.getAllOwcOfferings.size mustEqual 2
        owcPropertiesDAO.getAllOwcProperties.size mustBe 1

      }
    }

    "handle OwcDocuments with DB" in {
      withTestDatabase { database =>

        val owcPropertiesDAO = new OwcPropertiesDAO(database)
        val owcOfferingDAO = new OwcOfferingDAO(database)
        val owcDocumentDAO = new OwcDocumentDAO(database, owcOfferingDAO, owcPropertiesDAO)

        val owcEntry1 = OwcEntry("http://portal.smart-project.info/context/smart-sac_add-nz-dtm-100x100", None, featureProps1, List(offering1, offering2))
        val owcEntry2 = OwcEntry("http://portal.smart-project.info/context/smart-sac_add-nz_aquifers", Some(world), featureProps2, List(offering3, offering4))

        val owcDocument1 = OwcDocument("http://portal.smart-project.info/context/smart-sac", Some(world), documentProps1, List(owcEntry1, owcEntry2))

        owcDocumentDAO.createOwcDocument(owcDocument1) mustEqual Some(owcDocument1)

        owcDocumentDAO.getAllOwcDocuments.size mustEqual 1
        owcDocumentDAO.getAllOwcEntries.size mustEqual 2

        val thrown1 = the[java.sql.SQLException] thrownBy owcDocumentDAO.createOwcDocument(owcDocument1)
        thrown1.getErrorCode mustEqual 23505

        val thrown2 = the[java.sql.SQLException] thrownBy owcDocumentDAO.createOwcEntry(owcEntry2)
        thrown2.getErrorCode mustEqual 23505

        val documents = owcDocumentDAO.findOwcDocumentsById(owcDocument1.id)
        documents.size mustEqual 1
        documents.headOption.get.id mustEqual "http://portal.smart-project.info/context/smart-sac"

        val entries = owcDocumentDAO.findOwcEntriesById(owcEntry1.id)
        entries.size mustEqual 1
        entries.headOption.get.id mustEqual "http://portal.smart-project.info/context/smart-sac_add-nz-dtm-100x100"

        owcOfferingDAO.getAllOwcOfferings.size mustEqual 4
        owcOfferingDAO.findOwcOperationByCode("GetCapabilities").size mustBe 3
        owcPropertiesDAO.getAllOwcProperties.size mustBe 3
        owcPropertiesDAO.getAllOwcCategories.size mustBe 5

        owcDocumentDAO.deleteOwcDocument(owcDocument1) mustBe true

        owcDocumentDAO.getAllOwcDocuments.size mustEqual 0
        owcDocumentDAO.getAllOwcEntries.size mustEqual 0
        owcPropertiesDAO.getAllOwcProperties.size mustBe 0
        owcPropertiesDAO.getAllOwcCategories.size mustBe 0
        owcOfferingDAO.getAllOwcOfferings.size mustEqual 0
      }
    }

  }
}
