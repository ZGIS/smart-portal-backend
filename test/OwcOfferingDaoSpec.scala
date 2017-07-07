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

import com.typesafe.config.ConfigFactory
import info.smart.models.owc100._
import models.owc._
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}

/**
  * Test Spec for [[OwcOfferingDAO]] with [[OwcOffering]] and [[OwcOperation]]
  */
class OwcOfferingDaoSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  before {

  }

  after {

  }

  "OwcOfferingDao " can {

    "handle OwcOperation with DB" in {
      withTestDatabase { database =>

        val owcOfferingDAO = new OwcOfferingDAO(database, new OwcPropertiesDAO(database))

        val demodata = new DemoData

        val operation1 = demodata.operation1
        val operation2 = demodata.operation2
        val operation3 = demodata.operation3
        val operation3_1 = demodata.operation3_1
        val operation4 = demodata.operation4

        owcOfferingDAO.getAllOwcOperations.size mustEqual 0
        logger.info(s"owcOfferingDAO.getAllOwcOperations.size ${owcOfferingDAO.getAllOwcOperations.size}")

        owcOfferingDAO.createOwcOperation(operation1) mustEqual Some(operation1)
        owcOfferingDAO.findOwcOperationByUuid(operation1.uuid).get mustEqual operation1

        logger.info(s"owcOfferingDAO.getAllOwcOperations.size ${owcOfferingDAO.getAllOwcOperations.size}")

        owcOfferingDAO.createOwcOperation(operation2) mustEqual Some(operation2)
        owcOfferingDAO.findOwcOperationByUuid(operation2.uuid).get mustEqual operation2

        owcOfferingDAO.createOwcOperation(operation3) mustEqual Some(operation3)
        owcOfferingDAO.findOwcOperationByUuid(operation3.uuid).get mustEqual operation3

        owcOfferingDAO.createOwcOperation(operation4) mustEqual Some(operation4)
        owcOfferingDAO.findOwcOperationByUuid(operation4.uuid).get mustEqual operation4

        owcOfferingDAO.getAllOwcOperations.size mustEqual 4

        val thrown = the[java.sql.SQLException] thrownBy owcOfferingDAO.createOwcOperation(operation3)
        thrown.getErrorCode mustEqual 23505

        val operations = owcOfferingDAO.findOwcOperationByUuid(operation1.uuid)
        operations.size mustEqual 1
        operations.headOption.get.code mustEqual "GetCapabilities"

        owcOfferingDAO.findOwcOperationByCode("GetCapabilities").size mustBe 2

        owcOfferingDAO.deleteOwcOperation(operation2) mustEqual true

        owcOfferingDAO.updateOwcOperation(operation3_1).get mustEqual operation3_1
        owcOfferingDAO.findOwcOperationByUuid(operation3_1.uuid).get mustEqual operation3_1

        owcOfferingDAO.findOwcOperationByUuid(operation3_1.uuid).headOption.get.requestUrl mustEqual
          new URL("https://portal.smart-project.info/pycsw/csw?SERVICE=CSW&VERSION=2.0.2&REQUEST=GetCapabilities")

      }
    }

    "handle OwcOfferings with DB" in {
      withTestDatabase { database =>

        val demodata = new DemoData

        val owcOfferingDAO = new OwcOfferingDAO(database, new OwcPropertiesDAO(database))

        val offering1 = demodata.offering1
        val offering2 = demodata.offering2
        val offering2_1 = demodata.offering2_1

        owcOfferingDAO.getAllOwcOfferings.size mustEqual 0
        logger.info(s"owcOfferingDAO.getAllOwcOfferings.size ${owcOfferingDAO.getAllOwcOfferings.size}")

        owcOfferingDAO.createOwcOffering(offering1) mustEqual Some(offering1)
        owcOfferingDAO.findOwcOfferingByUuid(offering1.uuid).get mustEqual offering1

        logger.info(s"owcOfferingDAO.getAllOwcOfferings.size ${owcOfferingDAO.getAllOwcOfferings.size}")

        owcOfferingDAO.createOwcOffering(offering2) mustEqual Some(offering2)
        owcOfferingDAO.findOwcOfferingByUuid(offering2.uuid).get mustEqual offering2

        owcOfferingDAO.getAllOwcOfferings.size mustEqual 2

        val thrown = the[java.sql.SQLException] thrownBy owcOfferingDAO.createOwcOffering(offering2)
        thrown.getErrorCode mustEqual 23505

        val offerings = owcOfferingDAO.findOwcOfferingByUuid(offering1.uuid)
        offerings.size mustEqual 1
        offerings.headOption.get.code mustEqual OwcOfferingType.WMS.code

        owcOfferingDAO.findOwcOperationByCode("GetCapabilities").size mustBe 2

        owcOfferingDAO.updateOwcOfferings(offering2_1) mustEqual Some(offering2_1)
        owcOfferingDAO.findOwcOfferingByUuid(offering2_1.uuid).get mustEqual offering2_1

        owcOfferingDAO.deleteOwcOffering(offering2_1) mustEqual true
        owcOfferingDAO.getAllOwcOfferings.size mustEqual 1
        owcOfferingDAO.getAllOwcOperations.size mustEqual 2
      }
    }
  }
}
