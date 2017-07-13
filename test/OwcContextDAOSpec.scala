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

import anorm.{SQL, SqlParser}
import com.typesafe.config.ConfigFactory
import info.smart.models.owc100._
import models.db.SessionHolder
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
  import scala.language.implicitConversions

  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  private lazy val ctx = SpatialContext.GEO
  private lazy val owcContextResource1 = this.getClass().getResource("owc100/owc1.geojson")
  private lazy val owcContextResource2 = this.getClass().getResource("owc100/owc2.geojson")
  private lazy val owcContextResource3 = this.getClass().getResource("owc100/owc3.geojson")
  private lazy val owcIngesterResource1 = this.getClass().getResource("owc100/ingester1.owc.geojson")
  private lazy val owcIngesterResource2 = this.getClass().getResource("owc100/ingester_badgeom.owc.geojson")
  private lazy val owcIngesterResource3 = this.getClass().getResource("owc100/ingester_long.owc.geojson")
  private lazy val owcIngesterResource4 = this.getClass().getResource("owc100/ingester_new_scores.owc.geojson")
  private lazy val owcResourceDefaultCollectionWithFiles = this.getClass().getResource("owc100/DefaultCollectionWithFiles100.json")

  val jsonTestCollection1 = scala.io.Source.fromURL(owcContextResource1).getLines.mkString
  val jsonTestCollection2 = scala.io.Source.fromURL(owcContextResource2).getLines.mkString
  val jsonTestCollection3 = scala.io.Source.fromURL(owcContextResource3).getLines.mkString
  val jsonIngesterCollection1 = scala.io.Source.fromURL(owcIngesterResource1).getLines.mkString
  val jsonIngesterCollection2 = scala.io.Source.fromURL(owcIngesterResource2).getLines.mkString
  val jsonIngesterCollection3 = scala.io.Source.fromURL(owcIngesterResource3).getLines.mkString
  val jsonIngesterCollection4 = scala.io.Source.fromURL(owcIngesterResource4).getLines.mkString
  val jsonDefaultFilesCollection = scala.io.Source.fromURL(owcResourceDefaultCollectionWithFiles).getLines.mkString

  "OwcContextDAO " can {

    val demodata = new DemoData
    val owcContext1 = demodata.owcContext1

    "create OwcContexts with DB" in {
      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        val passwordHashing = new PasswordHashing(app.configuration)
        val testUser1 = demodata.testUser1(passwordHashing.createHash("testpass123"))

        sessionHolder.viaConnection { implicit connection =>
          OwcContextDAO.getAllOwcContexts.size mustEqual 0
        }

        sessionHolder.viaTransaction { implicit connection =>
          UserDAO.createUser(testUser1) must contain(testUser1)
          OwcContextDAO.createOwcContext(owcContext1, testUser1, 2, "CUSTOM") must contain(owcContext1)
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcContextDAO.findOwcContextByIdAndUser(owcContext1.id, testUser1) must contain(owcContext1)
          OwcContextDAO.getAllOwcContexts.size mustEqual 1
          OwcResourceDAO.getAllOwcResources.size mustEqual 2

          SQL(
            s"""select owc_context_id from $tableOwcContextHasOwcResources where
               | owc_context_id={owcContextId} and owc_resource_id={owcResourceId}
             """.stripMargin).on(
            'owcContextId -> owcContext1.id.toString,
            'owcResourceId -> owcContext1.resource.head.id.toString
          ).as(
            SqlParser.str("owc_context_id") *
          ).head mustEqual owcContext1.id.toString

          SQL(
            s"""select owc_context_id from $tableUserHasOwcContextRights where
               | owc_context_id={owcContextId} and users_accountsubject={accountsubject}
             """.stripMargin).on(
            'owcContextId -> owcContext1.id.toString,
            'accountsubject -> testUser1.accountSubject
          ).as(
            SqlParser.str("owc_context_id") *
          ).head mustEqual owcContext1.id.toString
        }

        sessionHolder.viaConnection { implicit connection =>
          val documents = OwcContextDAO.findOwcContextsById(owcContext1.id.toString)
          documents.size mustEqual 1
          documents.headOption.get.id mustEqual new URL("http://portal.smart-project.info/context/smart-sac")

          OwcResourceDAO.getAllOwcResources.size mustEqual 2
          OwcOperationDAO.getAllOwcOperations.size mustEqual 8
          OwcContentDAO.getAllOwcContents.size mustEqual 6
          OwcStyleSetDAO.getAllOwcStyleSets.size mustEqual 2
          OwcOfferingDAO.getAllOwcOfferings.size mustEqual 4
          OwcCategoryDAO.getAllOwcCategories.size mustBe 5

          OwcOperationDAO.findOwcOperationByCode("GetCapabilities").size mustBe 3
        }
      }
    }

    "won't create OwcContexts with DB when dependents exist" in {
      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        val passwordHashing = new PasswordHashing(app.configuration)
        val testUser1 = demodata.testUser1(passwordHashing.createHash("testpass123"))

        sessionHolder.viaConnection { implicit connection =>
          OwcContextDAO.getAllOwcContexts.size mustEqual 0
        }

        sessionHolder.viaTransaction { implicit connection =>
          UserDAO.createUser(testUser1) must contain(testUser1)
          OwcContextDAO.createOwcContext(owcContext1, testUser1, 2, "CUSTOM") must contain(owcContext1)
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcContextDAO.createOwcContext(owcContext1, testUser1, 2, "CUSTOM") mustBe None
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcContextDAO.findOwcContextByIdAndUser(owcContext1.id, testUser1) must contain(owcContext1)
          OwcContextDAO.getAllOwcContexts.size mustEqual 1
          OwcResourceDAO.getAllOwcResources.size mustEqual 2
        }

        sessionHolder.viaConnection { implicit connection =>
          val documents = OwcContextDAO.findOwcContextsById(owcContext1.id.toString)
          documents.size mustEqual 1
          documents.headOption.get.id mustEqual new URL("http://portal.smart-project.info/context/smart-sac")

          OwcResourceDAO.getAllOwcResources.size mustEqual 2
          OwcOperationDAO.getAllOwcOperations.size mustEqual 8
          OwcContentDAO.getAllOwcContents.size mustEqual 6
          OwcStyleSetDAO.getAllOwcStyleSets.size mustEqual 2
          OwcOfferingDAO.getAllOwcOfferings.size mustEqual 4
          OwcCategoryDAO.getAllOwcCategories.size mustBe 5

          OwcOperationDAO.findOwcOperationByCode("GetCapabilities").size mustBe 3
        }
      }
    }

    "update OwcContexts with DB" in {
      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        val passwordHashing = new PasswordHashing(app.configuration)
        val testUser1 = demodata.testUser1(passwordHashing.createHash("testpass123"))

        sessionHolder.viaConnection { implicit connection =>
          OwcContextDAO.getAllOwcContexts.size mustEqual 0
        }

        sessionHolder.viaTransaction { implicit connection =>
          UserDAO.createUser(testUser1) must contain(testUser1)
          OwcContextDAO.createOwcContext(owcContext1, testUser1, 2, "CUSTOM") must contain(owcContext1)
          OwcCreatorApplicationDAO.getAllOwcCreatorApplications.size mustBe 0
        }

        sessionHolder.viaTransaction { implicit connection =>
          val owcUpdate = owcContext1.copy()
          OwcContextDAO.updateOwcContext(owcUpdate, testUser1) must contain(owcUpdate)
          OwcCreatorApplicationDAO.getAllOwcCreatorApplications.size mustBe 0
        }

        sessionHolder.viaTransaction { implicit connection =>
          val owcUpdate2 = owcContext1.copy(creatorApplication = Some(OwcCreatorApplication(title = Some("SAC Portal"), uri = None, version = None)))
          OwcContextDAO.updateOwcContext(owcUpdate2, testUser1) must contain(owcUpdate2)
          OwcCreatorApplicationDAO.getAllOwcCreatorApplications.size mustBe 1
        }

        sessionHolder.viaTransaction { implicit connection =>
          val owcUpdate3 = owcContext1.copy(creatorDisplay = Some(OwcCreatorDisplay(pixelWidth = Some(600), pixelHeight = Some(400), mmPerPixel = None)))
          OwcContextDAO.updateOwcContext(owcUpdate3, testUser1) must contain(owcUpdate3)
        }

        sessionHolder.viaConnection { implicit connection =>
          val documents = OwcContextDAO.findOwcContextsById(owcContext1.id.toString)
          documents.size mustEqual 1
          documents.headOption.get.id mustEqual new URL("http://portal.smart-project.info/context/smart-sac")

          OwcResourceDAO.getAllOwcResources.size mustEqual 2
          OwcOperationDAO.getAllOwcOperations.size mustEqual 8
          OwcContentDAO.getAllOwcContents.size mustEqual 6
          OwcStyleSetDAO.getAllOwcStyleSets.size mustEqual 2
          OwcOfferingDAO.getAllOwcOfferings.size mustEqual 4
          OwcCategoryDAO.getAllOwcCategories.size mustBe 5
          OwcCreatorDisplayDAO.getAllOwcCreatorDisplays.size mustBe 1
          OwcCreatorApplicationDAO.getAllOwcCreatorApplications.size mustBe 0

          OwcOperationDAO.findOwcOperationByCode("GetCapabilities").size mustBe 3
        }
      }
    }

    "delete OwcContexts with DB" in {
      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        val passwordHashing = new PasswordHashing(app.configuration)
        val testUser1 = demodata.testUser1(passwordHashing.createHash("testpass123"))

        sessionHolder.viaConnection { implicit connection =>
          OwcContextDAO.getAllOwcContexts.size mustEqual 0
        }

        sessionHolder.viaTransaction { implicit connection =>
          UserDAO.createUser(testUser1) must contain(testUser1)
          OwcContextDAO.createOwcContext(owcContext1, testUser1, 2, "CUSTOM") must contain(owcContext1)
          OwcContextDAO.findOwcContextByIdAndUser(owcContext1.id, testUser1) must contain(owcContext1)
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcContextDAO.deleteOwcContext(owcContext1, testUser1) mustBe true
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcContextDAO.getAllOwcContexts.size mustEqual 0

          SQL(s"""select owc_context_id from $tableUserHasOwcContextRights""").as(
            SqlParser.str("owc_context_id") *
          ).isEmpty mustBe true

          SQL(s"""select owc_context_id from $tableOwcContextHasOwcResources""").as(
            SqlParser.str("owc_context_id") *
          ).isEmpty mustBe true

          OwcResourceDAO.getAllOwcResources.size mustEqual 0
          OwcCategoryDAO.getAllOwcCategories.size mustBe 0
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

    "bulkload OwcContext from JSON into DB" in {
      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        Json.parse(jsonTestCollection1).validate[OwcContext].isSuccess mustBe true
        val owcDoc1 = Json.parse(jsonTestCollection1).validate[OwcContext].get

        Json.parse(jsonTestCollection2).validate[OwcContext].isSuccess mustBe true
        val owcDoc2 = Json.parse(jsonTestCollection2).validate[OwcContext].get

        Json.parse(jsonTestCollection3).validate[OwcContext].isSuccess mustBe true
        val owcDoc3 = Json.parse(jsonTestCollection3).validate[OwcContext].get

        val passwordHashing = new PasswordHashing(app.configuration)
        val cryptPass = passwordHashing.createHash("testpass123")

        val testUser1 = demodata.testUser1(cryptPass)
        val testUser3 = demodata.testUser3(cryptPass)

        sessionHolder.viaTransaction { implicit connection =>
          UserDAO.createUser(testUser1) must contain(testUser1)
          UserDAO.createUser(testUser3) must contain(testUser3)
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcContextDAO.createUsersDefaultOwcContext(owcDoc1, testUser1)
          OwcContextDAO.createCustomOwcContext(owcDoc2, testUser1)
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcContextDAO.createUsersDefaultOwcContext(owcDoc3, testUser3)

        }

        sessionHolder.viaConnection { implicit connection =>
          OwcContextDAO.getAllOwcContexts.size mustEqual 3
          OwcResourceDAO.getAllOwcResources.size mustEqual 3
          OwcCategoryDAO.getAllOwcCategories.size mustBe 7
          OwcOfferingDAO.getAllOwcOfferings.size mustEqual 5
          OwcOperationDAO.getAllOwcOperations.size mustEqual 13
          OwcStyleSetDAO.getAllOwcStyleSets.size mustEqual 2
          OwcLinkDAO.getAllOwcLinks.size mustEqual 9
          OwcAuthorDAO.getAllOwcAuthors.size mustEqual 3
          OwcCreatorDisplayDAO.getAllOwcCreatorDisplays.size mustBe 1
          OwcCreatorApplicationDAO.getAllOwcCreatorApplications.size mustBe 1
          OwcContentDAO.getAllOwcContents.size mustEqual 6
        }
      }
    }

    "Apply GeoJsonFixes for empty rels JSON into DB" in {
      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        Json.parse(jsonTestCollection3).validate[OwcContext].isSuccess mustBe true
        val owcDoc3 = Json.parse(jsonTestCollection3).validate[OwcContext].get

        val passwordHashing = new PasswordHashing(app.configuration)
        val cryptPass = passwordHashing.createHash("testpass123")

        val testUser3 = demodata.testUser3(cryptPass)

        sessionHolder.viaTransaction { implicit connection =>
          UserDAO.createUser(testUser3) must contain(testUser3)
          OwcContextDAO.createUsersDefaultOwcContext(owcDoc3, testUser3)
        }

        sessionHolder.viaConnection { implicit connection =>
          owcDoc3.specReference.head.rel mustEqual "alternate"
          OwcContextDAO.findOwcContextByIdAndUser(owcDoc3.id, testUser3).get.specReference.head.rel mustEqual "profile"
        }
      }
    }

    "parse and load OwcContext from CSW Ingester into DB" in {

      Json.parse(jsonIngesterCollection1).validate[OwcContext].isSuccess mustBe true
      val owcDoc1 = Json.parse(jsonIngesterCollection1).validate[OwcContext].get

      // will not contain any OwcResource https://github.com/ZGIS/smart-owc-geojson/issues/8
      Json.parse(jsonIngesterCollection2).validate[OwcContext].isSuccess mustBe true
      val owcDoc2 = Json.parse(jsonIngesterCollection2).validate[OwcContext].get

      Json.parse(jsonIngesterCollection3).validate[OwcContext].isSuccess mustBe true
      val owcDoc3 = Json.parse(jsonIngesterCollection3).validate[OwcContext].get

      Json.parse(jsonIngesterCollection4).validate[OwcContext].isSuccess mustBe true
      val owcDoc4 = Json.parse(jsonIngesterCollection4).validate[OwcContext].get

      val passwordHashing = new PasswordHashing(app.configuration)
      val cryptPass = passwordHashing.createHash("testpass123")

      val testUser1 = demodata.testUser1(cryptPass)

      withTestDatabase { database =>
        val sessionHolder = new SessionHolder(database)

        sessionHolder.viaTransaction { implicit connection =>
          UserDAO.createUser(testUser1) must contain(testUser1)
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcContextDAO.createUsersDefaultOwcContext(owcDoc1, testUser1) mustBe defined
        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcContextDAO.createCustomOwcContext(owcDoc2, testUser1) mustBe defined
          OwcContextDAO.createCustomOwcContext(owcDoc3, testUser1) mustBe defined
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcContextDAO.findOwcContextByIdAndUser(owcDoc1.id, testUser1) mustBe defined
          val owcFromDb1 = OwcContextDAO.findOwcContextByIdAndUser(owcDoc1.id, testUser1).get
          owcFromDb1.resource.size mustEqual 2

          OwcContextDAO.findOwcContextByIdAndUser(owcDoc2.id, testUser1) mustBe defined
          val owcFromDb2 = OwcContextDAO.findOwcContextByIdAndUser(owcDoc2.id, testUser1).get
          // https://github.com/ZGIS/smart-owc-geojson/issues/8
          // owcFromDb2.resource.size mustEqual 1

          OwcContextDAO.findOwcContextByIdAndUser(owcDoc3.id, testUser1) mustBe defined
          val owcFromDb3 = OwcContextDAO.findOwcContextByIdAndUser(owcDoc3.id, testUser1).get
          owcFromDb3.resource.size mustEqual 21

          OwcContextDAO.getAllOwcContexts.size mustEqual 3
          OwcResourceDAO.getAllOwcResources.size mustEqual 23
          OwcCategoryDAO.getAllOwcCategories.size mustBe 0
          OwcOfferingDAO.getAllOwcOfferings.size mustEqual 23
          OwcOperationDAO.getAllOwcOperations.size mustEqual 46
          OwcStyleSetDAO.getAllOwcStyleSets.size mustEqual 0
          OwcLinkDAO.getAllOwcLinks.size mustEqual 26
          OwcAuthorDAO.getAllOwcAuthors.size mustEqual 23
          OwcCreatorDisplayDAO.getAllOwcCreatorDisplays.size mustBe 0
          OwcCreatorApplicationDAO.getAllOwcCreatorApplications.size mustBe 0
          OwcContentDAO.getAllOwcContents.size mustEqual 0

        }

        sessionHolder.viaTransaction { implicit connection =>
          OwcContextDAO.createCustomOwcContext(owcDoc4, testUser1) mustBe defined
        }

        sessionHolder.viaConnection { implicit connection =>
          OwcContextDAO.findOwcContextByIdAndUser(owcDoc4.id, testUser1) mustBe defined
          val owcFromDb4 = OwcContextDAO.findOwcContextByIdAndUser(owcDoc4.id, testUser1).get
          owcFromDb4.resource.size mustEqual 19
          OwcCreatorApplicationDAO.getAllOwcCreatorApplications.size mustBe 1
        }
      }
    }

    "find users own uploaded files" in {
      withTestDatabase { database =>

        // TODO SR this should be injected probably
        val passwordHashing = new PasswordHashing(app.configuration)
        val cryptPass = passwordHashing.createHash("testpass123")

        val testUser1 = demodata.testUser1(cryptPass)
        val defaultCollection = Json.parse(jsonDefaultFilesCollection).validate[OwcContext].get

        withTestDatabase { database =>
          val sessionHolder = new SessionHolder(database)

          sessionHolder.viaTransaction { implicit connection =>
            UserDAO.createUser(testUser1) must contain(testUser1)
          }

          sessionHolder.viaTransaction { implicit connection =>
            OwcContextDAO.createUsersDefaultOwcContext(defaultCollection, testUser1) mustBe defined
          }

          sessionHolder.viaConnection { implicit connection =>
            OwcContextDAO.findUserDefaultOwcContext(testUser1) mustBe defined
            val owcFromDb1 = OwcContextDAO.findUserDefaultOwcContext(testUser1).get
            owcFromDb1.resource.size mustEqual 2

            val owcDataLinks = owcFromDb1.resource.filter(o => o.contentByRef.nonEmpty).flatMap(o => o.contentByRef)

            owcDataLinks.length mustEqual 2

            owcDataLinks.map(ufp => ufp.title.get).sorted mustEqual ("gksee-2016-09-01_09_30_01.989.jpg" ::
              "gksee-2016-08-31_15_00_05.615.jpg" :: Nil).sorted
          }
        }
      }
    }
  }
}
