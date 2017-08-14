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
import org.scalatestplus.play.OneAppPerTest
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}

/**
  * Test Spec for [[OwcAuthorDAO]] with [[info.smart.models.owc100.OwcAuthor]]
  */
class OwcAuthorDAOSpec extends WithDefaultTest with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  import scala.language.implicitConversions
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  "OwcAuthorDAO" can {

    val demodata = new DemoData
    val author1 = demodata.author1
    val author2 = demodata.author2
    val author3 = demodata.author3
    val author3_1 = demodata.author3_1

    "create OwcAuthor with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcAuthorDAO.getAllOwcAuthors.size mustEqual 0

          OwcAuthorDAO.createOwcAuthor(author1) mustEqual Some(author1)
          OwcAuthorDAO.findOwcAuthorByUuid(author1.uuid) mustEqual Some(author1)

          OwcAuthorDAO.createOwcAuthor(author2) mustEqual Some(author2)
          OwcAuthorDAO.findOwcAuthorByUuid(author2.uuid) mustEqual Some(author2)

          OwcAuthorDAO.createOwcAuthor(author3) mustEqual Some(author3)
          OwcAuthorDAO.findOwcAuthorByUuid(author3.uuid) mustEqual Some(author3)

          val thrown = the[java.sql.SQLException] thrownBy OwcAuthorDAO.createOwcAuthor(author3)
          thrown.getErrorCode mustEqual 23505

          OwcAuthorDAO.getAllOwcAuthors.size mustEqual 3
        }
      }
    }

    "update OwcAuthor with DB" in {
      withTestDatabase { database =>
        database.withTransaction { implicit connection =>
          OwcAuthorDAO.createOwcAuthor(author3) mustEqual Some(author3)
          OwcAuthorDAO.findOwcAuthorByUuid(author3.uuid) mustEqual Some(author3)

          OwcAuthorDAO.updateOwcAuthor(author3_1).get mustEqual author3_1
          OwcAuthorDAO.findOwcAuthorByUuid(author3_1.uuid).headOption.get.uri.get.toString mustEqual "https://www.gns.cri.nz"
        }
      }
    }

    "delete OwcAuthor with DB" in {
      withTestDatabase { database =>
        database.withTransaction { implicit connection =>

          //        val authors = OwcAuthorDAO.findOwcAuthorByName("Alex")
          //        authors.size mustEqual 1
          //        authors.headOption.get.email mustBe None
          //
          //        OwcAuthorDAO.findOwcAuthorByName("Alex Kmoch").headOption.get.uri mustEqual Some("http://gns.cri.nz")

          OwcAuthorDAO.createOwcAuthor(author2) mustEqual Some(author2)
          OwcAuthorDAO.findOwcAuthorByUuid(author2.uuid) mustEqual Some(author2)

          OwcAuthorDAO.deleteOwcAuthor(author2) mustEqual true
          OwcAuthorDAO.getAllOwcAuthors.size mustEqual 0
        }
      }
    }
  }
}
