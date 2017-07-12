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
  * Test Spec for [[OwcContentDAO]] with [[info.smart.models.owc100.OwcContent]]
  */
class OwcContentDAOSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  import scala.language.implicitConversions

  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  "OwcContentDAO" can {

    val demodata = new DemoData

    val content1 = demodata.owccontent1
    val content2 = demodata.owccontent2
    val owccontent2_1 = demodata.owccontent2_1

    "create Owccontents with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcContentDAO.createOwcContent(content1) must contain(content1)
          OwcContentDAO.findOwcContentByUuid(content1.uuid).get mustEqual content1

          OwcContentDAO.createOwcContent(content2) must contain(content2)
          OwcContentDAO.findOwcContentByUuid(content2.uuid).get mustEqual content2

          OwcContentDAO.findOwcContentByUuid(content1.uuid).size mustBe 1
          //        OwcContentDAO.findByPropertiesUUID[OwcContent](Some(s"${content1.uuid.toString}:${content2.uuid.toString}")).size mustBe 2
          //
          //        OwcContentDAO.findByPropertiesUUID[OwcContent](Some(s"${content1.uuid.toString}:${content2.uuid.toString}")).size mustBe 2
          //
          //        OwcContentDAO.findByPropertiesUUID[OwcContent](Some("nupnup")).size mustBe 0
          //        OwcContentDAO.findByPropertiesUUID[OwcContent](Some("nupnup:blubblub")).size mustBe 0

          OwcContentDAO.getAllOwcContents.size mustEqual 2
        }
      }
    }

    "update OwcContents with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcContentDAO.createOwcContent(content2) must contain(content2)
          OwcContentDAO.getAllOwcContents.size mustEqual 1
        }
        database.withTransaction { implicit connection =>
          OwcContentDAO.updateOwcContent(owccontent2_1) must contain(owccontent2_1)
          OwcContentDAO.findOwcContentByUuid(owccontent2_1.uuid).get mustEqual owccontent2_1
        }
      }
    }

    "delete OwcContents with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcContentDAO.createOwcContent(content1) must contain(content1)
          OwcContentDAO.createOwcContent(content2) must contain(content2)
          OwcContentDAO.getAllOwcContents.size mustEqual 2
        }
        database.withTransaction { implicit connection =>
          OwcContentDAO.deleteOwcContent(content1) mustBe true
          OwcContentDAO.deleteOwcContent(content2) mustBe true
          OwcContentDAO.getAllOwcContents.size mustEqual 0
        }
      }
    }
  }
}
