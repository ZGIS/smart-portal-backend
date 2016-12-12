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

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID

import com.typesafe.config.ConfigFactory
import models.owc._
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}

/**
  * Test Spec for [[OwcPropertiesDAO]] with [[OwcProperties]], [[OwcAuthor]], [[OwcCategory]] and [[OwcLink]]
  */
class OwcPropertiesSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.testdev.conf"))).build()

  before {

  }

  after {

  }

  "OwcProperties " can {
    lazy val owcResource = this.getClass().getResource("owc/smart-nz.owc.json")

    "handle OwcAuthor with DB" in {
      withTestDatabase { database =>

        val author1 = OwcAuthor(UUID.randomUUID(), "Alex", None, None)
        val author2 = OwcAuthor(UUID.randomUUID(), "Alex K", Some(""), None)
        val author3 = OwcAuthor(UUID.randomUUID(), "Alex Kmoch", Some("a.kmoch@gns.cri.nz"), Some("http://gns.cri.nz"))

        val owcPropsDao = new OwcPropertiesDAO(database)

        owcPropsDao.getAllOwcAuthors.size mustEqual 0
        owcPropsDao.createOwcAuthor(author1) mustEqual Some(author1)
        owcPropsDao.createOwcAuthor(author2) mustEqual Some(author2)
        owcPropsDao.createOwcAuthor(author3) mustEqual Some(author3)

        val thrown = the[java.sql.SQLException] thrownBy owcPropsDao.createOwcAuthor(author3)
        thrown.getErrorCode mustEqual 23505

        val authors = owcPropsDao.findOwcAuthorByName("Alex")
        authors.size mustEqual 1
        authors.headOption.get.email mustBe None

        owcPropsDao.findOwcAuthorByName("Alex Kmoch").headOption.get.uri mustEqual Some("http://gns.cri.nz")

        owcPropsDao.deleteOwcAuthor(author2) mustEqual true

        val author3_1 = OwcAuthor(author3.uuid, author3.name, author3.email, Some("https://www.gns.cri.nz"))
        owcPropsDao.updateOwcAuthor(author3_1).get mustEqual author3_1
        owcPropsDao.findOwcAuthorByName(author3_1.name).headOption.get.uri.get mustEqual "https://www.gns.cri.nz"
      }
    }

    "handle OwcCategory with DB" in {
      withTestDatabase { database =>
        val owcPropsDao = new OwcPropertiesDAO(database)

        val category1 = OwcCategory(UUID.randomUUID(), "view-groups", "sac_add", Some("Informative Layers"))
        val category2 = OwcCategory(UUID.randomUUID(), "search-domain", "uncertainty", Some("Uncertainty of Models"))
        val category3 = OwcCategory(UUID.randomUUID(), "glossary", "uncertainty", Some("margin of error of a measurement"))

        owcPropsDao.getAllOwcCategories.size mustEqual 0
        owcPropsDao.createOwcCategory(category1) mustEqual Some(category1)
        owcPropsDao.createOwcCategory(category2) mustEqual Some(category2)
        owcPropsDao.createOwcCategory(category3) mustEqual Some(category3)

        val thrown = the[java.sql.SQLException] thrownBy owcPropsDao.createOwcCategory(category3)
        thrown.getErrorCode mustEqual 23505

        owcPropsDao.findOwcCategoriesByScheme("view-groups").head mustEqual category1
        owcPropsDao.findOwcCategoriesBySchemeAndTerm("search-domain", "uncertainty").head mustEqual category2
        owcPropsDao.findOwcCategoriesByTerm("uncertainty").size mustBe 2

        val category3_1 = OwcCategory(category3.uuid, category3.scheme, category3.term, Some("Margin of Error of Measurements"))
        owcPropsDao.updateOwcCategory(category3_1) mustEqual Some(category3_1)
        owcPropsDao.findOwcCategoriesBySchemeAndTerm("glossary", "uncertainty").size mustBe 1
        owcPropsDao.findOwcCategoriesBySchemeAndTerm("glossary", "uncertainty").head.label mustEqual category3_1.label

        owcPropsDao.deleteOwcCategory(category3) mustBe true
        owcPropsDao.getAllOwcCategories.size mustEqual 2
      }
    }

    "handle OwcLink with DB" in {
      withTestDatabase { database =>
        val owcPropsDao = new OwcPropertiesDAO(database)

        val link1 = OwcLink(UUID.randomUUID(), "profile", None, "http://www.opengis.net/spec/owc-atom/1.0/req/core", Some("This file is compliant with version 1.0 of OGC Context"))
        val link2 = OwcLink(UUID.randomUUID(), "self", Some("application/json"), "http://portal.smart-project.info/context/smart-sac.owc.json", None)
        val link3 = OwcLink(UUID.randomUUID(), "icon", Some("image/png"), "http://portal.smart-project.info/fs/images/nz_m.png", None)

        owcPropsDao.createOwcLink(link1) mustEqual Some(link1)
        owcPropsDao.createOwcLink(link2) mustEqual Some(link2)
        owcPropsDao.createOwcLink(link3) mustEqual Some(link3)

        val thrown = the[java.sql.SQLException] thrownBy owcPropsDao.createOwcLink(link3)
        thrown.getErrorCode mustEqual 23505

        owcPropsDao.findOwcLinksByHref("http://www.opengis.net/spec/owc-atom/1.0/req/core").size mustBe 1
        owcPropsDao.findOwcLinksByRelAndHref("self", "http://portal.smart-project.info/context/smart-sac.owc.json").size mustBe 1

        val link3_1 = OwcLink(link3.uuid, link3.rel, link3.mimeType, link3.href, Some("New Zealand Flag"))
        owcPropsDao.updateOwcLink(link3_1) mustEqual Some(link3_1)
        owcPropsDao.findOwcLinksByRelAndHref("icon", "http://portal.smart-project.info/fs/images/nz_m.png").size mustBe 1
        owcPropsDao.findOwcLinksByRelAndHref("icon", "http://portal.smart-project.info/fs/images/nz_m.png").headOption.get.title mustEqual Some("New Zealand Flag")

        owcPropsDao.deleteOwcLink(link3) mustBe true
        owcPropsDao.getAllOwcLinks.size mustEqual 2
      }
    }

    "handle OwcProperties with DB" in {
      withTestDatabase { database =>
        val owcPropsDao = new OwcPropertiesDAO(database)

        val link1 = OwcLink(UUID.randomUUID(), "profile", None, "http://www.opengis.net/spec/owc-atom/1.0/req/core", Some("This file is compliant with version 1.0 of OGC Context"))
        val link2 = OwcLink(UUID.randomUUID(), "self", Some("application/json"), "http://portal.smart-project.info/context/smart-sac.owc.json", None)

        val category1 = OwcCategory(UUID.randomUUID(), "view-groups", "sac_add", Some("Informative Layers"))
        val category2 = OwcCategory(UUID.randomUUID(), "search-domain", "uncertainty", Some("Uncertainty of Models"))

        val author1 = OwcAuthor(UUID.randomUUID(), "Alex K", Some(""), None)
        val author2 = OwcAuthor(UUID.randomUUID(), "Alex Kmoch", Some("a.kmoch@gns.cri.nz"), Some("http://gns.cri.nz"))
        val author3 = OwcAuthor(UUID.randomUUID(), "Alex Kmoch 2nd", Some("b.kmoch@gns.cri.nz"), Some("http://gns.cri.nz/1234"))
        val author4 = OwcAuthor(UUID.randomUUID(), "Alexander Kmoch", Some("c.kmoch@gns.cri.nz"), Some("http://gns.cri.nz"))

        val testTime = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())

        val featureProps1 = OwcProperties(
          UUID.randomUUID(),
          "en",
          "NZ DTM 100x100",
          Some("Some Bla"),
          Some(testTime),
          None,
          Some("CC BY SA 4.0 NZ"),
          List(author1),
          List(author2, author3),
          None,
          Some("GNS Science"),
          List(category1, category2),
          List(link1, link2)
        )

        val featureProps2 = OwcProperties(
          UUID.randomUUID(),
          "en",
          "NZ SAC Recharge",
          Some("Some Bla Recharge"),
          Some(testTime),
          None,
          Some("CC BY SA 4.0 NZ"),
          List(author2),
          List(author3, author4),
          None,
          Some("GNS Science"),
          List(category2),
          List(link1, link2)
        )

        owcPropsDao.createOwcProperties(featureProps1) mustEqual Some(featureProps1)
        owcPropsDao.createOwcProperties(featureProps2) mustEqual Some(featureProps2)

        val thrown = the[java.sql.SQLException] thrownBy owcPropsDao.createOwcProperties(featureProps1)
        thrown.getErrorCode mustEqual 23505

        owcPropsDao.getAllOwcProperties.size mustBe 2

        owcPropsDao.findOwcPropertiesByUuid(featureProps1.uuid) mustEqual Some(featureProps1)

        owcPropsDao.deleteOwcProperties(featureProps1) mustEqual true

        owcPropsDao.getAllOwcProperties.size mustBe 1
        owcPropsDao.getAllOwcAuthors.size mustBe 3
        owcPropsDao.getAllOwcCategories.size mustBe 1
        owcPropsDao.getAllOwcLinks.size mustBe 2
      }
    }
  }
}
