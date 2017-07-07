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
import java.util.UUID

import com.typesafe.config.ConfigFactory
import info.smart.models.owc100._
import models.owc._
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}

/**
  * Test Spec for [[OwcPropertiesDAO]] with [[OwcAuthor]], [[OwcCategory]] and [[OwcLink]]
  */
class OwcPropertiesDaoSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  before {

  }

  after {

  }

  "OwcPropertiesDao (i.e. authors, keywords, links, content and stylesets)" can {

    "handle OwcAuthor with DB" in {
      withTestDatabase { database =>

        val demodata = new DemoData
        
        val author1 = demodata.author1
        val author2 = demodata.author2
        val author3 = demodata.author3

        // TODO SR these DAO objects are created by DepInj framework normally and we should think about using it in tests also
        val owcPropsDao = new OwcPropertiesDAO(database)

        database.withConnection{ implicit connection =>
          owcPropsDao.getAllOwcAuthors.size mustEqual 0
        }

        owcPropsDao.createOwcAuthor(author1) mustEqual Some(author1)
        owcPropsDao.findOwcAuthorByUuid(author1.uuid) mustEqual Some(author1)

        owcPropsDao.createOwcAuthor(author2) mustEqual Some(author2)
        owcPropsDao.findOwcAuthorByUuid(author2.uuid) mustEqual Some(author2)

        owcPropsDao.createOwcAuthor(author3) mustEqual Some(author3)
        owcPropsDao.findOwcAuthorByUuid(author3.uuid) mustEqual Some(author3)

        val thrown = the[java.sql.SQLException] thrownBy owcPropsDao.createOwcAuthor(author3)
        thrown.getErrorCode mustEqual 23505

//        val authors = owcPropsDao.findOwcAuthorByName("Alex")
//        authors.size mustEqual 1
//        authors.headOption.get.email mustBe None
//
//        owcPropsDao.findOwcAuthorByName("Alex Kmoch").headOption.get.uri mustEqual Some("http://gns.cri.nz")

        owcPropsDao.deleteOwcAuthor(author2) mustEqual true

        val author3_1 = demodata.author3_1
        owcPropsDao.updateOwcAuthor(author3_1).get mustEqual author3_1
        owcPropsDao.findOwcAuthorByUuid(author3_1.uuid).headOption.get.uri.get.toString mustEqual "https://www.gns.cri.nz"
      }
    }

    "handle OwcCategory with DB" in {
      withTestDatabase { database =>
        val owcPropsDao = new OwcPropertiesDAO(database)

        val demodata = new DemoData
        
        val category1 = demodata.category1
        val category2 = demodata.category2
        val category3 = demodata.category3

        owcPropsDao.getAllOwcCategories.size mustEqual 0
        owcPropsDao.createOwcCategory(category1) mustEqual Some(category1)
        owcPropsDao.findOwcCategoriesByUuid(category1.uuid) mustEqual Some(category1)

        owcPropsDao.createOwcCategory(category2) mustEqual Some(category2)
        owcPropsDao.findOwcCategoriesByUuid(category2.uuid) mustEqual Some(category2)

        owcPropsDao.createOwcCategory(category3) mustEqual Some(category3)
        owcPropsDao.findOwcCategoriesByUuid(category3.uuid) mustEqual Some(category3)

        val thrown = the[java.sql.SQLException] thrownBy owcPropsDao.createOwcCategory(category3)
        thrown.getErrorCode mustEqual 23505

//        owcPropsDao.findOwcCategoriesByScheme("view-groups").head mustEqual category1
//        owcPropsDao.findOwcCategoriesBySchemeAndTerm("search-domain", "uncertainty").head mustEqual category2
//        owcPropsDao.findOwcCategoriesByTerm("uncertainty").size mustBe 2

        val category3_1 = demodata.category3_1
        owcPropsDao.updateOwcCategory(category3_1) mustEqual Some(category3_1)
        owcPropsDao.findOwcCategoriesByUuid(category3_1.uuid) mustEqual Some(category3_1)

//        owcPropsDao.findOwcCategoriesBySchemeAndTerm("glossary", "uncertainty").size mustBe 1
//        owcPropsDao.findOwcCategoriesBySchemeAndTerm("glossary", "uncertainty").head.label mustEqual category3_1.label

        owcPropsDao.deleteOwcCategory(category3) mustBe true
        owcPropsDao.getAllOwcCategories.size mustEqual 2
      }
    }

    "handle OwcLink with DB" in {
      withTestDatabase { database =>
        val owcPropsDao = new OwcPropertiesDAO(database)

        val demodata = new DemoData
        
        val link1 = demodata.link1
        val link2 = demodata.link2
        val link3 = demodata.link3

        owcPropsDao.createOwcLink(link1) mustEqual Some(link1)
        owcPropsDao.findOwcLinksByUuid(link1.uuid) mustEqual Some(link1)

        owcPropsDao.createOwcLink(link2) mustEqual Some(link2)
        owcPropsDao.findOwcLinksByUuid(link2.uuid) mustEqual Some(link2)

        owcPropsDao.createOwcLink(link3) mustEqual Some(link3)
        owcPropsDao.findOwcLinksByUuid(link3.uuid) mustEqual Some(link3)

        val thrown = the[java.sql.SQLException] thrownBy owcPropsDao.createOwcLink(link3)
        thrown.getErrorCode mustEqual 23505

        //owcPropsDao.findOwcLinksByHref(new URL("http://www.opengis.net/spec/owc-atom/1.0/req/core")).size mustBe 1
        owcPropsDao.findOwcLinksByUuid(link1.uuid).size mustBe 1

        val link3_1 = demodata.link3_1

        owcPropsDao.updateOwcLink(link3_1) mustEqual Some(link3_1)
        owcPropsDao.findOwcLinksByUuid(link3_1.uuid).get mustEqual link3_1

        //owcPropsDao.findOwcLinksByPropertiesUUID(Some(s"${link1.uuid.toString}:${link2.uuid.toString}")).size mustBe 2
        //owcPropsDao.findOwcLinksByPropertiesUUID(Some("nupnup")).size mustBe 0
        //owcPropsDao.findOwcLinksByPropertiesUUID(Some("nupnup:blubblub")).size mustBe 0

        owcPropsDao.deleteOwcLink(link3) mustBe true
        owcPropsDao.getAllOwcLinks.size mustEqual 2
      }
    }

    "handle OwcContent with DB" in {
      withTestDatabase { database =>
        val owcPropsDao = new OwcPropertiesDAO(database)

        val demodata = new DemoData
        
        val content1 = demodata.owccontent1
        val content2 = demodata.owccontent2

        owcPropsDao.createOwcContent(content1) mustEqual Some(content1)
        owcPropsDao.findOwcContentsByUuid(content1.uuid).get mustEqual content1

        owcPropsDao.createOwcContent(content2) mustEqual Some(content2)
        owcPropsDao.findOwcContentsByUuid(content2.uuid).get mustEqual content2

        owcPropsDao.findOwcContentsByUuid(content1.uuid).size mustBe 1

        import models.owc.OwcContentEvidence
        owcPropsDao.findByPropertiesUUID[OwcContent](Some(s"${content1.uuid.toString}:${content2.uuid.toString}")).size mustBe 2

        //owcPropsDao.findOwcContentsByPropertiesUUID(Some(s"${content1.uuid.toString}:${content2.uuid.toString}")).size mustBe 2
        //owcPropsDao.findOwcContentsByPropertiesUUID(Some("nupnup")).size mustBe 0
        //owcPropsDao.findOwcContentsByPropertiesUUID(Some("nupnup:blubblub")).size mustBe 0

        val owccontent2_1 = demodata.owccontent2_1

        owcPropsDao.updateOwcContent(owccontent2_1) mustEqual Some(owccontent2_1)
        owcPropsDao.findOwcContentsByUuid(owccontent2_1.uuid).get mustEqual owccontent2_1

        owcPropsDao.deleteOwcContent(content1) mustBe true
        owcPropsDao.deleteOwcContent(owccontent2_1) mustBe true
        owcPropsDao.getAllOwcContents.size mustEqual 0
      }
    }

    "handle OwcStyleSet with DB" in {
      withTestDatabase { database =>
        val owcPropsDao = new OwcPropertiesDAO(database)

        val demodata = new DemoData
        
        val style1 = demodata.style1
        val style2 = demodata.style2
        val style3 = demodata.style3

        owcPropsDao.createOwcStyleSet(style1) mustEqual Some(style1)
        owcPropsDao.findOwcStyleSetsByUuid(style1.uuid).get mustEqual style1

        owcPropsDao.createOwcStyleSet(style2) mustEqual Some(style2)
        owcPropsDao.findOwcStyleSetsByUuid(style2.uuid).get mustEqual style2

        owcPropsDao.createOwcStyleSet(style3) mustEqual Some(style3)
        owcPropsDao.findOwcStyleSetsByUuid(style3.uuid).get mustEqual style3

        owcPropsDao.findOwcStyleSetsByUuid(style1.uuid).size mustBe 1

        owcPropsDao.findByPropertiesUUID[OwcStyleSet](Some(s"${style1.uuid.toString}:${style2.uuid.toString}"))(OwcStyleSetEvidence).size mustBe 2

        //owcPropsDao.findOwcStyleSetsByPropertiesUUID(Some(s"${style1.uuid.toString}:${style2.uuid.toString}")).size mustBe 2
        //owcPropsDao.findOwcStyleSetsByPropertiesUUID(Some("nupnup")).size mustBe 0
        //owcPropsDao.findOwcStyleSetsByPropertiesUUID(Some("nupnup:blubblub")).size mustBe 0

        val style3_1 = demodata.style3_1

        owcPropsDao.updateOwcStyleSet(style3_1) mustEqual Some(style3_1)
        owcPropsDao.findOwcStyleSetsByUuid(style3_1.uuid).get mustEqual style3_1

        owcPropsDao.deleteOwcStyleSet(style3_1) mustBe true
        owcPropsDao.getAllOwcStyleSets.size mustEqual 2
      }
    }


//    "handle OwcProperties with DB" in {
//      withTestDatabase { database =>
//        val owcPropsDao = new OwcPropertiesDAO(database, new OwcOfferingDAO(database))
//
//        val link1 = OwcLink(UUID.randomUUID(), "profile", None, "http://www.opengis.net/spec/owc-atom/1.0/req/core", Some("This file is compliant with version 1.0 of OGC Context"))
//        val link2 = OwcLink(UUID.randomUUID(), "self", Some("application/json"), "http://portal.smart-project.info/context/smart-sac.owc.json", None)
//
//        val category1 = OwcCategory(UUID.randomUUID(), "view-groups", "sac_add", Some("Informative Layers"))
//        val category2 = OwcCategory(UUID.randomUUID(), "search-domain", "uncertainty", Some("Uncertainty of Models"))
//
//        val author1 = OwcAuthor(UUID.randomUUID(), "Alex K", Some(""), None)
//        val author2 = OwcAuthor(UUID.randomUUID(), "Alex Kmoch", Some("a.kmoch@gns.cri.nz"), Some("http://gns.cri.nz"))
//        val author3 = OwcAuthor(UUID.randomUUID(), "Alex Kmoch 2nd", Some("b.kmoch@gns.cri.nz"), Some("http://gns.cri.nz/1234"))
//        val author4 = OwcAuthor(UUID.randomUUID(), "Alexander Kmoch", Some("c.kmoch@gns.cri.nz"), Some("http://gns.cri.nz"))
//
//        val testTime = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())
//
//        val featureProps1 = OwcProperties(
//          UUID.randomUUID(),
//          "en",
//          "NZ DTM 100x100",
//          Some("Some Bla"),
//          Some(testTime),
//          None,
//          Some("CC BY SA 4.0 NZ"),
//          List(author1),
//          List(author2, author3),
//          None,
//          Some("GNS Science"),
//          List(category1, category2),
//          List(link1, link2)
//        )
//
//        val featureProps2 = OwcProperties(
//          UUID.randomUUID(),
//          "en",
//          "NZ SAC Recharge",
//          Some("Some Bla Recharge"),
//          Some(testTime),
//          None,
//          Some("CC BY SA 4.0 NZ"),
//          List(author2),
//          List(author3, author4),
//          None,
//          Some("GNS Science"),
//          List(category2),
//          List(link1, link2)
//        )
//
//        owcPropsDao.createOwcProperties(featureProps1) mustEqual Some(featureProps1)
//        owcPropsDao.createOwcProperties(featureProps2) mustEqual Some(featureProps2)
//
//        val thrown = the[java.sql.SQLException] thrownBy owcPropsDao.createOwcProperties(featureProps1)
//        thrown.getErrorCode mustEqual 23505
//
//        owcPropsDao.getAllOwcProperties.size mustBe 2
//
//        owcPropsDao.findOwcPropertiesByUuid(featureProps1.uuid) mustEqual Some(featureProps1)
//
//        owcPropsDao.deleteOwcProperties(featureProps1) mustEqual true
//
//        owcPropsDao.getAllOwcProperties.size mustBe 1
//        owcPropsDao.getAllOwcAuthors.size mustBe 3
//        owcPropsDao.getAllOwcCategories.size mustBe 1
//        owcPropsDao.getAllOwcLinks.size mustBe 2
//      }
//    }
//
//    "get OwcProperties with own files" in {
//      pending
//    }
  }
}
