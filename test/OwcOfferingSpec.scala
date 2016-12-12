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

import java.util.UUID

import com.typesafe.config.ConfigFactory
import models.owc._
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}

/**
  * Test Spec for [[OwcOfferingDAO]] with [[OwcOffering]] and [[OwcOperation]]
  */
class OwcOfferingSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.testdev.conf"))).build()

  before {

  }

  after {

  }

  "OwcOffering " can {
    lazy val owcResource = this.getClass().getResource("owc/smart-nz.owc.json")

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

    "handle OwcOperation with DB" in {
      withTestDatabase { database =>

        val owcOfferingDAO = new OwcOfferingDAO(database)

        owcOfferingDAO.getAllOwcOperations.size mustEqual 0
        owcOfferingDAO.createOwcOperation(operation1) mustEqual Some(operation1)
        owcOfferingDAO.createOwcOperation(operation2) mustEqual Some(operation2)
        owcOfferingDAO.createOwcOperation(operation3) mustEqual Some(operation3)
        owcOfferingDAO.createOwcOperation(operation4) mustEqual Some(operation4)
        owcOfferingDAO.getAllOwcOperations.size mustEqual 4

        val thrown = the[java.sql.SQLException] thrownBy owcOfferingDAO.createOwcOperation(operation3)
        thrown.getErrorCode mustEqual 23505

        val operations = owcOfferingDAO.findOwcOperationByUuid(operation1.uuid)
        operations.size mustEqual 1
        operations.headOption.get.code mustEqual "GetCapabilities"

        owcOfferingDAO.findOwcOperationByCode("GetCapabilities").size mustBe 2

        owcOfferingDAO.deleteOwcOperation(operation2) mustEqual true

        val operation3_1 = OwcOperation(
          operation3.uuid,
          operation3.code,
          operation3.method,
          operation3.contentType,
          "https://portal.smart-project.info/pycsw/csw?SERVICE=CSW&VERSION=2.0.2&REQUEST=GetCapabilities",
          None,
          None
        )

        owcOfferingDAO.updateOwcOperation(operation3_1).get mustEqual operation3_1
        owcOfferingDAO.findOwcOperationByUuid(operation3_1.uuid).headOption.get.href mustEqual
          "https://portal.smart-project.info/pycsw/csw?SERVICE=CSW&VERSION=2.0.2&REQUEST=GetCapabilities"

      }
    }

    "handle OwcOfferings with DB" in {
      withTestDatabase { database =>

        val owcOfferingDAO = new OwcOfferingDAO(database)

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

        owcOfferingDAO.getAllOwcOfferings.size mustEqual 0
        owcOfferingDAO.createOwcOffering(offering1) mustEqual Some(offering1)
        owcOfferingDAO.createOwcOffering(offering2) mustEqual Some(offering2)
        owcOfferingDAO.getAllOwcOfferings.size mustEqual 2

        val thrown = the[java.sql.SQLException] thrownBy owcOfferingDAO.createOwcOffering(offering2)
        thrown.getErrorCode mustEqual 23505

        val offerings = owcOfferingDAO.findOwcOfferingByUuid(offering1.uuid)
        offerings.size mustEqual 1
        offerings.headOption.get.code mustEqual "http://www.opengis.net/spec/owc-geojson/1.0/req/wms"

        owcOfferingDAO.findOwcOperationByCode("GetCapabilities").size mustBe 2

        owcOfferingDAO.deleteOwcOffering(offering2) mustEqual true
        owcOfferingDAO.getAllOwcOfferings.size mustEqual 1
        owcOfferingDAO.getAllOwcOperations.size mustEqual 2
      }
    }
  }
}