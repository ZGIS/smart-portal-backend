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
  * Test Spec for [[OwcOperationDAO]] with [[info.smart.models.owc100.OwcOperation]]
  */
class OwcOperationDAOSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  import scala.language.implicitConversions

  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  "OwcOperationDAO " can {

    val demodata = new DemoData

    val operation1 = demodata.operation1
    val operation2 = demodata.operation2
    val operation3 = demodata.operation3
    val operation3_1 = demodata.operation3_1
    val operation4 = demodata.operation4

    "create OwcOperation with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcOperationDAO.getAllOwcOperations.size mustEqual 0
          logger.info(s"OwcOperationDAO.getAllOwcOperations.size ${OwcOperationDAO.getAllOwcOperations.size}")
        }

        database.withTransaction { implicit connection =>
          OwcOperationDAO.createOwcOperation(operation1) must contain(operation1)
          OwcOperationDAO.findOwcOperationByUuid(operation1.uuid) must contain(operation1)
          logger.info(s"OwcOperationDAO.getAllOwcOperations.size ${OwcOperationDAO.getAllOwcOperations.size}")
        }

        database.withTransaction { implicit connection =>
          OwcOperationDAO.createOwcOperation(operation2) must contain(operation2)
          OwcOperationDAO.findOwcOperationByUuid(operation2.uuid).get mustEqual operation2
        }

        database.withTransaction { implicit connection =>
          OwcOperationDAO.createOwcOperation(operation3) must contain(operation3)
        }
        database.withConnection { implicit connection =>
          OwcOperationDAO.findOwcOperationByUuid(operation3.uuid) must contain(operation3)
        }

        database.withTransaction { implicit connection =>
          OwcOperationDAO.createOwcOperation(operation4) must contain(operation4)
          OwcOperationDAO.findOwcOperationByUuid(operation4.uuid).get mustEqual operation4
        }

        database.withConnection { implicit connection =>
          OwcOperationDAO.getAllOwcOperations.size mustEqual 4

          val operations = OwcOperationDAO.findOwcOperationByUuid(operation1.uuid)
          operations.size mustEqual 1
          operations.headOption.get.code mustEqual "GetCapabilities"

          OwcOperationDAO.findOwcOperationByCode("GetCapabilities").size mustBe 1
        }
      }
    }

    "won't create OwcOperation with DB when OwcContent UUID already exists" in {
      withTestDatabase { database =>
        database.withTransaction { implicit connection =>
          OwcOperationDAO.createOwcOperation(operation3) must contain(operation3)
        }
        database.withConnection { implicit connection =>
          OwcOperationDAO.findOwcOperationByUuid(operation3.uuid) must contain(operation3)
          OwcOperationDAO.getAllOwcOperations.size mustEqual 1
          OwcContentDAO.getAllOwcContents.size mustEqual 1
        }
        database.withTransaction { implicit connection =>
          OwcOperationDAO.createOwcOperation(operation3) mustBe None
        }
        database.withConnection { implicit connection =>
          OwcOperationDAO.findOwcOperationByUuid(operation3.uuid) must contain(operation3)
          OwcOperationDAO.getAllOwcOperations.size mustEqual 1
          OwcContentDAO.getAllOwcContents.size mustEqual 1
        }
      }
    }

    "update OwcOperation with DB" in {
      withTestDatabase { database =>
        database.withTransaction { implicit connection =>
          OwcOperationDAO.createOwcOperation(operation3) must contain(operation3)
        }
        database.withConnection { implicit connection =>
          OwcOperationDAO.getAllOwcOperations.size mustEqual 1
          OwcContentDAO.getAllOwcContents.size mustEqual 1
          OwcOperationDAO.findOwcOperationByUuid(operation3.uuid) must contain(operation3)
        }
        database.withTransaction { implicit connection =>
          OwcOperationDAO.updateOwcOperation(operation3_1).get mustEqual operation3_1
          OwcOperationDAO.findOwcOperationByUuid(operation3_1.uuid) must contain(operation3_1)
        }
      }
    }

    "delete OwcOperation with DB" in {
      withTestDatabase { database =>
        database.withTransaction { implicit connection =>
          OwcOperationDAO.createOwcOperation(operation2) must contain(operation2)
        }
        database.withTransaction { implicit connection =>
          OwcOperationDAO.deleteOwcOperation(operation2) mustEqual true
        }
        database.withConnection { implicit connection =>
          OwcOperationDAO.getAllOwcOperations.size mustEqual 0
          OwcContentDAO.getAllOwcContents.size mustEqual 0
        }
        database.withTransaction { implicit connection =>
          OwcOperationDAO.createOwcOperation(operation3) must contain(operation3)
        }
        database.withConnection { implicit connection =>
          OwcContentDAO.getAllOwcContents.size mustEqual 1
          OwcOperationDAO.findOwcOperationByUuid(operation3.uuid) must contain(operation3)
        }
        database.withTransaction { implicit connection =>
          OwcOperationDAO.deleteOwcOperation(operation3) mustEqual true
        }
        database.withConnection { implicit connection =>
          OwcOperationDAO.getAllOwcOperations.size mustEqual 0
          OwcContentDAO.getAllOwcContents.size mustEqual 0
        }
      }
    }
  }
}
