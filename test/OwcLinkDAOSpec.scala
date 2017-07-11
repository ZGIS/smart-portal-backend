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
import models.owc._
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}

/**
  * Test Spec for [[OwcLinkDAO]] with [[info.smart.models.owc100.OwcLink]]
  */
class OwcLinkDAOSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  import scala.language.implicitConversions
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  "OwcLinkDAO" can {

    val demodata = new DemoData

    val link1 = demodata.link1
    val link2 = demodata.link2
    val link3 = demodata.link3
    val link3_1 = demodata.link3_1

    "create OwcLink with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>

          OwcLinkDAO.createOwcLink(link1) mustEqual Some(link1)
          OwcLinkDAO.findOwcLinkByUuid(link1.uuid) mustEqual Some(link1)

          OwcLinkDAO.createOwcLink(link2) mustEqual Some(link2)
          OwcLinkDAO.findOwcLinkByUuid(link2.uuid) mustEqual Some(link2)

          OwcLinkDAO.createOwcLink(link3) mustEqual Some(link3)
          OwcLinkDAO.findOwcLinkByUuid(link3.uuid) mustEqual Some(link3)

          val thrown = the[java.sql.SQLException] thrownBy OwcLinkDAO.createOwcLink(link3)
          thrown.getErrorCode mustEqual 23505

          //OwcLinkDAO.findOwcLinksByHref(new URL("http://www.opengis.net/spec/owc-atom/1.0/req/core")).size mustBe 1
          OwcLinkDAO.findOwcLinkByUuid(link1.uuid).size mustBe 1
          OwcLinkDAO.getAllOwcLinks.size mustEqual 3

        }
      }
    }

    "update OwcLink with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcLinkDAO.createOwcLink(link3) must contain (link3)
          OwcLinkDAO.findOwcLinkByUuid(link3.uuid) must contain (link3)
        }
        database.withTransaction { implicit connection =>
          OwcLinkDAO.updateOwcLink(link3_1) must contain (link3_1)
        }
        database.withConnection { implicit connection =>
          OwcLinkDAO.findOwcLinkByUuid(link3_1.uuid) must contain (link3_1)
          //OwcLinkDAO.findOwcLinksByPropertiesUUID(Some(s"${link1.uuid.toString}:${link2.uuid.toString}")).size mustBe 2
          //OwcLinkDAO.findOwcLinksByPropertiesUUID(Some("nupnup")).size mustBe 0
          //OwcLinkDAO.findOwcLinksByPropertiesUUID(Some("nupnup:blubblub")).size mustBe 0
        }
      }
    }

    "delete OwcLink with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcLinkDAO.createOwcLink(link3) must contain (link3)
          OwcLinkDAO.findOwcLinkByUuid(link3.uuid) must contain (link3)
        }
        database.withConnection { implicit connection =>
          OwcLinkDAO.deleteOwcLink(link3) mustBe true
          OwcLinkDAO.getAllOwcLinks.size mustEqual 0
        }
      }
    }
  }
}
