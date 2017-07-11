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
  * Test Spec for [[OwcCreatorDisplayDAO]] with [[info.smart.models.owc100.OwcCreatorDisplay]]
  */
class OwcCreatorDisplayDAOSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  import scala.language.implicitConversions

  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  "OwcCreatorDisplayDAO" can {

    val demodata = new DemoData

    val displayApp1 = demodata.displayApp1
    val displayApp2 = demodata.displayApp2
    val displayApp3 = demodata.displayApp3
    val displayApp3_1 = demodata.displayApp3_1

    "create OwcCreatorDisplay with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcCreatorDisplayDAO.getAllOwcCreatorDisplays.size mustEqual 0

          OwcCreatorDisplayDAO.createOwcCreatorDisplay(displayApp1) must contain(displayApp1)
          OwcCreatorDisplayDAO.findOwcCreatorDisplayByUuid(displayApp1.uuid) must contain(displayApp1)

          OwcCreatorDisplayDAO.createOwcCreatorDisplay(displayApp2) must contain(displayApp2)
          OwcCreatorDisplayDAO.findOwcCreatorDisplayByUuid(displayApp2.uuid) must contain(displayApp2)

          OwcCreatorDisplayDAO.createOwcCreatorDisplay(displayApp3) must contain(displayApp3)
          OwcCreatorDisplayDAO.findOwcCreatorDisplayByUuid(displayApp3.uuid) must contain(displayApp3)

          val thrown = the[java.sql.SQLException] thrownBy OwcCreatorDisplayDAO.createOwcCreatorDisplay(displayApp3)
          thrown.getErrorCode mustEqual 23505

          OwcCreatorDisplayDAO.getAllOwcCreatorDisplays.size mustEqual 3
        }
      }
    }

    "update OwcCreatorDisplay with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcCreatorDisplayDAO.createOwcCreatorDisplay(displayApp3) must contain(displayApp3)
          OwcCreatorDisplayDAO.findOwcCreatorDisplayByUuid(displayApp3.uuid) must contain(displayApp3)
        }

        database.withTransaction { implicit connection =>
          OwcCreatorDisplayDAO.updateOwcCreatorDisplay(displayApp3_1).get mustEqual displayApp3_1
          OwcCreatorDisplayDAO.findOwcCreatorDisplayByUuid(displayApp3_1.uuid) must contain (displayApp3_1)
        }
      }
    }

    "delete OwcCreatorDisplay with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcCreatorDisplayDAO.createOwcCreatorDisplay(displayApp2) must contain(displayApp2)
          OwcCreatorDisplayDAO.findOwcCreatorDisplayByUuid(displayApp2.uuid) must contain(displayApp2)
        }
        database.withTransaction { implicit connection =>
          OwcCreatorDisplayDAO.deleteOwcCreatorDisplay(displayApp2) mustEqual true
          OwcCreatorDisplayDAO.getAllOwcCreatorDisplays.size mustEqual 0
        }
      }
    }
  }
}
