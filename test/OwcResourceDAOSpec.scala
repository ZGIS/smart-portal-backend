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

import java.time.{OffsetDateTime, ZoneId}

import com.typesafe.config.ConfigFactory
import info.smart.models.owc100.OwcContext
import models.db.SessionHolder
import models.owc._
import org.locationtech.spatial4j.context.SpatialContext
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.{Application, Configuration}

/**
  * Test Spec for [[models.owc.OwcResourceDAO]] with [[info.smart.models.owc100.OwcResource]]
  */
class OwcResourceDAOSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  import scala.language.implicitConversions

  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  private lazy val ctx = SpatialContext.GEO
  private lazy val owcContextResource1 = this.getClass().getResource("owc100/owc1.geojson")
  private lazy val owcContextResource2 = this.getClass().getResource("owc100/owc2.geojson")
  private lazy val owcContextResource3 = this.getClass().getResource("owc100/owc3.geojson")
  private lazy val owcIngesterResource = this.getClass().getResource("owc100/ingester1.owc.geojson")
  private lazy val owcResourceDefaultCollectionWithFiles = this.getClass().getResource("owc100/DefaultCollectionWithFiles100.json")

  val jsonTestCollection1 = scala.io.Source.fromURL(owcContextResource1).getLines.mkString
  val jsonTestCollection2 = scala.io.Source.fromURL(owcContextResource2).getLines.mkString
  val jsonTestCollection3 = scala.io.Source.fromURL(owcContextResource3).getLines.mkString
  val jsonTestCollection4 = scala.io.Source.fromURL(owcIngesterResource).getLines.mkString
  val jsonTestCollection5 = scala.io.Source.fromURL(owcResourceDefaultCollectionWithFiles).getLines.mkString

  "OwcResourceDAO " can {

    val demodata = new DemoData

    val owcResource1 = demodata.owcResource1
    val owcResource2 = demodata.owcResource2
    val owcResource2_1 = demodata.owcResource2_1

    "create OwcResources with DB" in {
      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        sessionHolder.viaConnection { implicit connection =>
          OwcResourceDAO.getAllOwcResources.size mustEqual 0
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcResourceDAO.createOwcResource(owcResource1) must contain(owcResource1)
          OwcResourceDAO.findOwcResourceById(owcResource1.id) must contain(owcResource1)
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcResourceDAO.createOwcResource(owcResource2) must contain(owcResource2)
          OwcResourceDAO.findOwcResourceById(owcResource2.id) must contain(owcResource2)
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcResourceDAO.getAllOwcResources.size mustEqual 2
          OwcOfferingDAO.getAllOwcOfferings.size mustEqual 4
          OwcOperationDAO.getAllOwcOperations.size mustEqual 8
          OwcContentDAO.getAllOwcContents.size mustEqual 6
          OwcStyleSetDAO.getAllOwcStyleSets.size mustEqual 2
        }
      }
    }

    "won't create OwcResources with DB if one of dependencies UUID already exists" in {
      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        sessionHolder.viaTransaction { implicit connection =>

          OwcResourceDAO.createOwcResource(owcResource2) must contain(owcResource2)
          OwcResourceDAO.findOwcResourceById(owcResource2.id) must contain(owcResource2)
        }
        // completely different resource, but one identical category, should not change DB numbers
        val owcResource2_abort = owcResource1.copy(keyword = owcResource2.keyword)
        sessionHolder.viaTransaction { implicit connection =>
          OwcResourceDAO.createOwcResource(owcResource2_abort) mustBe None
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcResourceDAO.getAllOwcResources.size mustEqual 1
          OwcOfferingDAO.getAllOwcOfferings.size mustEqual 2
          OwcOperationDAO.getAllOwcOperations.size mustEqual 4
          OwcContentDAO.getAllOwcContents.size mustEqual 1
          OwcStyleSetDAO.getAllOwcStyleSets.size mustEqual 0
          OwcLinkDAO.getAllOwcLinks.size mustEqual 1
          OwcCategoryDAO.getAllOwcCategories.size mustEqual 1
          OwcAuthorDAO.getAllOwcAuthors.size mustEqual 1
        }
      }
    }

    "update OwcResources with DB" in {

      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        sessionHolder.viaTransaction { implicit connection =>
          OwcResourceDAO.createOwcResource(owcResource2) must contain(owcResource2)
          OwcResourceDAO.findOwcResourceById(owcResource2.id) must contain(owcResource2)
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcOperationDAO.findOwcOperationByCode("GetCapabilities").size mustBe 2
          OwcCategoryDAO.getAllOwcCategories.size mustBe 1
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcResourceDAO.updateOwcResource(owcResource2_1) must contain(owcResource2_1)
          OwcResourceDAO.findOwcResourceById(owcResource2_1.id) must contain(owcResource2_1)
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcOperationDAO.findOwcOperationByCode("GetCapabilities").size mustBe 2
          OwcCategoryDAO.getAllOwcCategories.size mustBe 1
        }
      }
    }

    "delete OwcResources with DB" in {

      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        sessionHolder.viaTransaction { implicit connection =>
          OwcResourceDAO.createOwcResource(owcResource2) must contain(owcResource2)
          OwcResourceDAO.findOwcResourceById(owcResource2.id) must contain(owcResource2)
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcResourceDAO.getAllOwcResources.size mustEqual 1
          OwcOfferingDAO.getAllOwcOfferings.size mustEqual 2
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcResourceDAO.deleteOwcResource(owcResource2) mustEqual true
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcResourceDAO.getAllOwcResources.size mustEqual 0
          OwcOfferingDAO.getAllOwcOfferings.size mustEqual 0
          OwcOperationDAO.getAllOwcOperations.size mustEqual 0
          OwcContentDAO.getAllOwcContents.size mustEqual 0
          OwcStyleSetDAO.getAllOwcStyleSets.size mustEqual 0
          OwcLinkDAO.getAllOwcLinks.size mustEqual 0
          OwcCategoryDAO.getAllOwcCategories.size mustEqual 0
          OwcAuthorDAO.getAllOwcAuthors.size mustEqual 0
        }
      }
    }

    "bulkload OwcResources from JSON into DB" in {

      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        val owcDoc1 = Json.parse(jsonTestCollection1).validate[OwcContext].get
        val owcDoc2 = Json.parse(jsonTestCollection2).validate[OwcContext].get
        val owcDoc3 = Json.parse(jsonTestCollection3).validate[OwcContext].get
        val owcDoc4 = Json.parse(jsonTestCollection4).validate[OwcContext].get
        // val owcDoc5 = Json.parse(jsonIngesterCollection2).validate[OwcContext].get

        val resources = owcDoc1.resource ++
          owcDoc2.resource ++
          owcDoc3.resource ++
          owcDoc4.resource // ++ owcDoc5.resource

        sessionHolder.viaTransaction { implicit connection =>
          resources.map(r => OwcResourceDAO.createOwcResource(r)).count(_.isDefined) mustBe resources.size
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcResourceDAO.getAllOwcResources.size mustEqual 3
          OwcOfferingDAO.getAllOwcOfferings.size mustEqual 5
          OwcOperationDAO.getAllOwcOperations.size mustEqual 11
          OwcContentDAO.getAllOwcContents.size mustEqual 5
          OwcStyleSetDAO.getAllOwcStyleSets.size mustEqual 2
          OwcLinkDAO.getAllOwcLinks.size mustEqual 5
          OwcCategoryDAO.getAllOwcCategories.size mustEqual 2
          OwcAuthorDAO.getAllOwcAuthors.size mustEqual 1
        }
      }
    }
  }
}
