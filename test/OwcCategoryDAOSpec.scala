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
  * Test Spec for [[OwcCategoryDAO]] with [[info.smart.models.owc100.OwcCategory]]
  */
class OwcCategoryDAOSpec extends WithDefaultTest with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  import scala.language.implicitConversions
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  "OwcCategoryDAO" can {

    val demodata = new DemoData

    val category1 = demodata.category1
    val category2 = demodata.category2
    val category3 = demodata.category3
    val category3_1 = demodata.category3_1

    "create OwcCategory with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>

            OwcCategoryDAO.getAllOwcCategories.size mustEqual 0
            OwcCategoryDAO.createOwcCategory(category1) mustEqual Some(category1)
            OwcCategoryDAO.findOwcCategoryByUuid(category1.uuid) mustEqual Some(category1)

            OwcCategoryDAO.createOwcCategory(category2) mustEqual Some(category2)
            OwcCategoryDAO.findOwcCategoryByUuid(category2.uuid) mustEqual Some(category2)

            OwcCategoryDAO.createOwcCategory(category3) mustEqual Some(category3)
            OwcCategoryDAO.findOwcCategoryByUuid(category3.uuid) mustEqual Some(category3)

            val thrown = the[java.sql.SQLException] thrownBy OwcCategoryDAO.createOwcCategory(category3)
            thrown.getErrorCode mustEqual 23505

          //        OwcCategoryDAO.findOwcCategoriesByScheme("view-groups").head mustEqual category1
          //        OwcCategoryDAO.findOwcCategoriesBySchemeAndTerm("search-domain", "uncertainty").head mustEqual category2
          //        OwcCategoryDAO.findOwcCategoriesByTerm("uncertainty").size mustBe 2

          OwcCategoryDAO.getAllOwcCategories.size mustEqual 3
        }
      }
    }

    "update OwcCategory with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcCategoryDAO.createOwcCategory(category3) mustEqual Some(category3)
          OwcCategoryDAO.findOwcCategoryByUuid(category3.uuid) mustEqual Some(category3)
        }

        database.withTransaction { implicit connection =>
            OwcCategoryDAO.updateOwcCategory(category3_1) mustEqual Some(category3_1)
        }

        database.withConnection { implicit connection =>
          OwcCategoryDAO.findOwcCategoryByUuid(category3_1.uuid) mustEqual Some(category3_1)

          //        OwcCategoryDAO.findOwcCategoriesBySchemeAndTerm("glossary", "uncertainty").size mustBe 1
          //        OwcCategoryDAO.findOwcCategoriesBySchemeAndTerm("glossary", "uncertainty").head.label mustEqual category3_1.label
        }
      }
    }

    "delete OwcCategory with DB" in {
      withTestDatabase { database =>
        database.withConnection { implicit connection =>
          OwcCategoryDAO.createOwcCategory(category3) mustEqual Some(category3)
        }
        database.withTransaction { implicit connection =>
            OwcCategoryDAO.deleteOwcCategory(category3) mustBe true
        }
        database.withConnection { implicit connection =>
            OwcCategoryDAO.getAllOwcCategories.size mustEqual 0
        }
      }
    }
  }
}
