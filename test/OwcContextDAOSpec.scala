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
import java.time.{OffsetDateTime, ZoneId}

import anorm.{SQL, SqlParser}
import com.typesafe.config.ConfigFactory
import info.smart.models.owc100._
import models.owc._
import models.users.UserDAO
import org.locationtech.spatial4j.context.SpatialContext
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.{Application, Configuration}
import utils.PasswordHashing

/**
  * Test Spec for [[models.owc.OwcContextDAO]] with [[info.smart.models.owc100.OwcContext]]
  */
class OwcContextDAOSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  before {

  }

  after {

  }

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

  "OwcContextDAO " can {

    "handle OwcContexts with DB" in {
      withTestDatabase { database =>
        /*
          possibly we should wrap in transactions what belongs into transactions
         
          val sessionHolder = new SessionHolder(database)
          sessionHolder.viaConnection { implicit connection => ... }
        */
        implicit val connection = database.getConnection()

        val demodata = new DemoData
        val testTime = OffsetDateTime.now(ZoneId.systemDefault())
        lazy val world = ctx.getShapeFactory().rect(-180.0, 180.0, -90.0, 90.0)

        val owcResource1 = demodata.owcResource1
        val owcResource2 = demodata.owcResource2

        val owcContext1 = demodata.owcContext1

        val passwordHashing = new PasswordHashing(app.configuration)
        val testUser1 = demodata.testUser1(passwordHashing.createHash("testpass123"))


        UserDAO.createUser(testUser1) mustEqual Some(testUser1)

        OwcContextDAO.createOwcContext(owcContext1, testUser1, 2, "CUSTOM") mustEqual Some(owcContext1)

        OwcContextDAO.getAllOwcContexts.size mustEqual 1
        OwcResourceDAO.getAllOwcResources.size mustEqual 2

        val thrown1 = the[java.sql.SQLException] thrownBy OwcContextDAO.createOwcContext(owcContext1, testUser1, 2, "CUSTOM")
        thrown1.getErrorCode mustEqual 23505

        val thrown2 = the[java.sql.SQLException] thrownBy OwcResourceDAO.createOwcResource(owcResource2)
        thrown2.getErrorCode mustEqual 23505

        val documents = OwcContextDAO.findOwcContextsById(owcContext1.id.toString)
        documents.size mustEqual 1
        documents.headOption.get.id mustEqual new URL("http://portal.smart-project.info/context/smart-sac")

        val entries = OwcResourceDAO.findOwcResourceById(owcResource1.id)
        entries.size mustEqual 1
        entries.headOption.get.id mustEqual new URL("http://portal.smart-project.info/context/smart-sac_add-nz-dtm-100x100")

        OwcOfferingDAO.getAllOwcOfferings.size mustEqual 4
        OwcOperationDAO.findOwcOperationByCode("GetCapabilities").size mustBe 3
        OwcCategoryDAO.getAllOwcCategories.size mustBe 5

        OwcContextDAO.deleteOwcContext(owcContext1, testUser1) mustBe true

        SQL(s"""select owc_document_id from $tableUserHasOwcContextRights""").as(
          SqlParser.str("owc_document_id") *
        ).isEmpty mustBe true

        OwcContextDAO.getAllOwcContexts.size mustEqual 0
        OwcResourceDAO.getAllOwcResources.size mustEqual 0
        OwcCategoryDAO.getAllOwcCategories.size mustBe 0
        OwcOfferingDAO.getAllOwcOfferings.size mustEqual 0
      }
    }

    "handle Users having OwcContexts with DB" in {
      withTestDatabase { database =>

        implicit val connection = database.getConnection()
        //        val sessionHolder = new SessionHolder(database)
        //        sessionHolder.viaConnection { implicit connection =>
        //
        //        }

        val demodata = new DemoData

        val owcDoc1 = Json.parse(jsonTestCollection1).validate[OwcContext].get
        val owcDoc2 = Json.parse(jsonTestCollection2).validate[OwcContext].get
        val owcDoc3 = Json.parse(jsonTestCollection3).validate[OwcContext].get
        val owcDoc4 = Json.parse(jsonTestCollection4).validate[OwcContext].get
        val owcDoc5 = Json.parse(jsonTestCollection5).validate[OwcContext].get

        val passwordHashing = new PasswordHashing(app.configuration)

        val cryptPass = passwordHashing.createHash("testpass123")

        val testUser1 = demodata.testUser1(cryptPass)
        val testUser2 = demodata.testUser2(cryptPass)

        UserDAO.createUser(testUser1) mustEqual Some(testUser1)
        UserDAO.createUser(testUser2) mustEqual Some(testUser2)

        OwcContextDAO.createUsersDefaultOwcContext(owcDoc1, testUser1)
        OwcContextDAO.createCustomOwcContext(owcDoc2, testUser1)
        OwcContextDAO.createCustomOwcContext(owcDoc3, testUser1)
        OwcContextDAO.createCustomOwcContext(owcDoc4, testUser1)
        OwcContextDAO.createOwcContext(owcDoc5, testUser2, 2, "CUSTOM")

        OwcContextDAO.getAllOwcContexts.size mustEqual 5
        OwcResourceDAO.getAllOwcResources.size mustEqual 70
        OwcCategoryDAO.getAllOwcCategories.size mustBe 79
        OwcOfferingDAO.getAllOwcOfferings.size mustEqual 139

        SQL(s"""select owc_feature_types_as_document_id from $tableUserHasOwcContextRights""").as(
          SqlParser.str("owc_feature_types_as_document_id") *
        ).size mustBe 5

      }
    }

    "find users own uploaded files" in {
      withTestDatabase { database =>

        implicit val connection = database.getConnection()
        //        val sessionHolder = new SessionHolder(database)
        //        sessionHolder.viaConnection { implicit connection =>
        //
        //        }

        val demodata = new DemoData
        val defaultCollectionRaw = scala.io.Source.fromURL(owcResourceDefaultCollectionWithFiles).getLines.mkString
        val defaultCollection = Json.parse(defaultCollectionRaw).validate[OwcContext].get

        // TODO SR this should be injected probably
        val passwordHashing = new PasswordHashing(app.configuration)

        val testUser = demodata.testUser3(passwordHashing.createHash("testpass123"))
        UserDAO.createUser(testUser) mustEqual Some(testUser)
        OwcContextDAO.createUsersDefaultOwcContext(defaultCollection, testUser)
        //
        //        val ownFiles = OwcContextDAO.findOwcPropertiesForOwcAuthorOwnFiles(testUser.email)
        //        ownFiles.length mustEqual 3
        //
        //        ownFiles.map(ufp => ufp.owcProperties.title).sorted mustEqual
        //          ("gksee-2016-09-01_09_30_01.989.jpg"::
        //          "gksee-2016-08-31_15_00_05.615.jpg"::
        //          "gksee-2016-08-30_11_55_02.411.jpg"::
        //          Nil).sorted
      }
    }
  }
}
