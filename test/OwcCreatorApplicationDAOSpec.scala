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
  * Test Spec for [[OwcCreatorApplicationDAO]] with [[info.smart.models.owc100.OwcCreatorApplication]]
  */
class OwcCreatorApplicationDAOSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  import scala.language.implicitConversions

  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  "OwcCreatorApplicationDAO" can {

    val demodata = new DemoData

    val creatorApp1 = demodata.creatorApp1
    val creatorApp2 = demodata.creatorApp2
    val creatorApp3 = demodata.creatorApp3
    val creatorApp3_1 = demodata.creatorApp3_1

    "create OwcCreatorApplication with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>

          OwcCreatorApplicationDAO.getAllOwcCreatorApplications.size mustEqual 0

          OwcCreatorApplicationDAO.createOwcCreatorApplication(creatorApp1) must contain(creatorApp1)
          OwcCreatorApplicationDAO.findOwcCreatorApplicationByUuid(creatorApp1.uuid) must contain(creatorApp1)

          OwcCreatorApplicationDAO.createOwcCreatorApplication(creatorApp2) must contain(creatorApp2)
          OwcCreatorApplicationDAO.findOwcCreatorApplicationByUuid(creatorApp2.uuid) must contain(creatorApp2)

          OwcCreatorApplicationDAO.createOwcCreatorApplication(creatorApp3) must contain(creatorApp3)
          OwcCreatorApplicationDAO.findOwcCreatorApplicationByUuid(creatorApp3.uuid) must contain(creatorApp3)

          val thrown = the[java.sql.SQLException] thrownBy OwcCreatorApplicationDAO.createOwcCreatorApplication(creatorApp3)
          thrown.getErrorCode mustEqual 23505
        }
      }
    }

    "update OwcCreatorApplication with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcCreatorApplicationDAO.createOwcCreatorApplication(creatorApp3) must contain(creatorApp3)
          OwcCreatorApplicationDAO.findOwcCreatorApplicationByUuid(creatorApp3.uuid) must contain(creatorApp3)
        }
        database.withTransaction { implicit connection =>
          OwcCreatorApplicationDAO.updateOwcCreatorApplication(creatorApp3_1).get mustEqual creatorApp3_1
          OwcCreatorApplicationDAO.findOwcCreatorApplicationByUuid(creatorApp3_1.uuid) must contain(creatorApp3_1)
        }
      }
    }

    "delete OwcCreatorApplication with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcCreatorApplicationDAO.createOwcCreatorApplication(creatorApp2) must contain(creatorApp2)
          OwcCreatorApplicationDAO.findOwcCreatorApplicationByUuid(creatorApp2.uuid) must contain(creatorApp2)
        }
        database.withTransaction { implicit connection =>
          OwcCreatorApplicationDAO.deleteOwcCreatorApplication(creatorApp2) mustEqual true
          OwcCreatorApplicationDAO.getAllOwcCreatorApplications.size mustEqual 0
        }
      }
    }
  }
}
