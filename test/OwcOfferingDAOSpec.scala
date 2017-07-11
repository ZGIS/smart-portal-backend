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

import com.typesafe.config.ConfigFactory
import info.smart.models.owc100._
import models.db.SessionHolder
import models.owc._
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}

/**
  * Test Spec for [[OwcOfferingDAO]] with with [[info.smart.models.owc100.OwcOffering]]
  */
class OwcOfferingDAOSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  import scala.language.implicitConversions
  
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  "OwcOfferingDAO " can {

    val demodata = new DemoData

    val offering1 = demodata.offering1
    val offering2 = demodata.offering2
    val offering2_1 = demodata.offering2_1
    
    "create OwcOfferings with DB" in {
      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        sessionHolder.viaConnection { implicit connection =>
          OwcOfferingDAO.getAllOwcOfferings.size mustEqual 0
          logger.info(s"OwcOfferingDAO.getAllOwcOfferings.size ${OwcOfferingDAO.getAllOwcOfferings.size}")
        }
        sessionHolder.viaTransaction { implicit connection =>
          OwcOfferingDAO.createOwcOffering(offering2) must contain (offering2)
          OwcOfferingDAO.findOwcOfferingByUuid(offering2.uuid) must contain (offering2)
          logger.info(s"OwcOfferingDAO.getAllOwcOfferings.size ${OwcOfferingDAO.getAllOwcOfferings.size}")
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcOfferingDAO.createOwcOffering(offering1) must contain (offering1)
          OwcOfferingDAO.findOwcOfferingByUuid(offering1.uuid) must contain (offering1)
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcOfferingDAO.findByPropertiesUUID[OwcStyleSet](Some(offering1.styles.map(o => o.uuid).mkString(":")))(OwcStyleSetEvidence, connection).size mustBe 2
          OwcOfferingDAO.getAllOwcOfferings.size mustEqual 2
          OwcContentDAO.getAllOwcContents.size mustEqual 5
          OwcStyleSetDAO.getAllOwcStyleSets.size mustEqual 2
          OwcOperationDAO.getAllOwcOperations.size mustEqual 4
        }
      }
    }

    "won't create OwcOfferings with DB when depending OwcContent / StyleSet / Operation UUIDs exist" in {
      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        sessionHolder.viaTransaction { implicit connection =>
          OwcOfferingDAO.createOwcOffering(offering2) must contain (offering2)
          OwcOfferingDAO.findOwcOfferingByUuid(offering2.uuid) must contain (offering2)
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcOfferingDAO.createOwcOffering(offering2) mustBe None
        }

        sessionHolder.viaConnection { implicit connection =>
          val offerings = OwcOfferingDAO.findOwcOfferingByUuid(offering2.uuid)
          offerings.size mustEqual 1
          offerings.headOption.get.code mustEqual OwcOfferingType.CSW.code

          OwcOperationDAO.findOwcOperationByCode("GetFeature").size mustBe 1
        }
      }
    }

    "update OwcOfferings with DB" in {
      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        sessionHolder.viaTransaction { implicit connection =>
          OwcOfferingDAO.createOwcOffering(offering2) must contain (offering2)
          OwcOfferingDAO.findOwcOfferingByUuid(offering2.uuid) must contain (offering2)
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcOfferingDAO.updateOwcOffering(offering2_1) must contain (offering2_1)
          OwcOfferingDAO.findOwcOfferingByUuid(offering2_1.uuid) must contain (offering2_1)
        }
      }
    }

    "delete OwcOfferings with DB" in {
      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        sessionHolder.viaTransaction { implicit connection =>
          OwcOfferingDAO.createOwcOffering(offering2_1) must contain (offering2_1)
          OwcOfferingDAO.findOwcOfferingByUuid(offering2_1.uuid) must contain (offering2_1)
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcOfferingDAO.deleteOwcOffering(offering2_1) mustEqual true
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcOfferingDAO.findOwcOfferingByUuid(offering2_1.uuid) mustBe None
          OwcOfferingDAO.getAllOwcOfferings.size mustEqual 0
          OwcOperationDAO.getAllOwcOperations.size mustEqual 0
          OwcContentDAO.getAllOwcContents.size mustEqual 0
          OwcStyleSetDAO.getAllOwcStyleSets.size mustEqual 0
        }
      }
    }
  }
}
