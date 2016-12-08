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

import anorm.SQL
import com.typesafe.config.ConfigFactory
import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.shape._
import org.scalatest.{BeforeAndAfter, Ignore, TestData}
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.libs.json._
import models._
import play.api.db.evolutions.{ClassLoaderEvolutionsReader, Evolutions}
import play.api.{Application, Configuration}
import play.api.inject.guice.GuiceApplicationBuilder

/**
  * Test Spec for [[OwcDocument]]
  */
class OwcDocumentSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.testdev.conf"))).build()

  before {

  }

  after {

  }

  private lazy val ctx = SpatialContext.GEO

  "OwcDocument " can {
    lazy val owcResource = this.getClass().getResource("owc/smart-nz.owc.json")

    "handle OwcAuthor with DB" in {
      withTestDatabase { database =>

        val author1 = OwcAuthor("Alex", None, None)
        val author2 = OwcAuthor("Alex K", Some(""), None)
        val author3 = OwcAuthor("Alex Kmoch", Some("a.kmoch@gns.cri.nz"), Some("http://gns.cri.nz"))

        val owcDao = new OwcDAO(database)

        owcDao.getAllOwcAuthors.size mustEqual 0
        owcDao.createOwcAuthor(author1) mustEqual Some(author1)
        owcDao.createOwcAuthor(author2) mustEqual Some(author2)
        owcDao.createOwcAuthor(author3) mustEqual Some(author3)

        val authors = owcDao.findOwcAuthorByName("Alex")
        authors.size mustEqual 1
        authors.headOption.get.email mustBe None

        owcDao.findOwcAuthorByName("Alex Kmoch").headOption.get.uri mustEqual Some("http://gns.cri.nz")

        owcDao.deleteOwcAuthor("Alex K") mustEqual true

        val author3_1 = OwcAuthor("Alex Kmoch", Some("a.kmoch@gns.cri.nz"), Some("https://www.gns.cri.nz"))
        owcDao.updateOwcAuthor(author3_1).get mustEqual author3_1
        owcDao.findOwcAuthorByName(author3_1.name).headOption.get.uri.get mustEqual "https://www.gns.cri.nz"

      }
    }

    "handle OwcCategory with DB" in {
      withTestDatabase { database =>
        val owcDao = new OwcDAO(database)

        val category1 = OwcCategory("view-groups", "sac_add", Some("Informative Layers"))
        val category2 = OwcCategory("search-domain", "uncertainty", Some("Uncertainty of Models"))
        val category3 = OwcCategory("glossary", "uncertainty", Some("margin of error of a measurement"))

        owcDao.getAllOwcCategories.size mustEqual 0
        owcDao.createOwcCategory(category1) mustEqual Some(category1)
        owcDao.createOwcCategory(category2) mustEqual Some(category2)
        owcDao.createOwcCategory(category3) mustEqual Some(category3)

        owcDao.findOwcCategoriesByScheme("view-groups").head mustEqual category1
        owcDao.findOwcCategoriesBySchemeAndTerm("search-domain", "uncertainty").head mustEqual category2
        owcDao.findOwcCategoriesByTerm("uncertainty").size mustBe 2

        val category3_1 = OwcCategory("glossary", "uncertainty", Some("Margin of Error of Measurements"))
        owcDao.updateOwcCategory(category3_1) mustEqual Some(category3_1)
        owcDao.findOwcCategoriesBySchemeAndTerm("glossary", "uncertainty").size mustBe 1
        owcDao.findOwcCategoriesBySchemeAndTerm("glossary", "uncertainty").head.label mustEqual category3_1.label

        owcDao.deleteOwcCategory(category3) mustBe true
        owcDao.getAllOwcCategories.size mustEqual 2

      }
    }

    "handle OwcLink with DB" in {
      withTestDatabase { database =>
        val owcDao = new OwcDAO(database)

        val link1 = OwcLink("profile", None, "http://www.opengis.net/spec/owc-atom/1.0/req/core", Some("This file is compliant with version 1.0 of OGC Context"))
        val link2 = OwcLink("self", Some("application/json"), "http://portal.smart-project.info/context/smart-sac.owc.json", None)
        val link3 = OwcLink("icon", Some("image/png"), "http://portal.smart-project.info/fs/images/nz_m.png", None)

        owcDao.createOwcLink(link1) mustEqual Some(link1)
        owcDao.createOwcLink(link2) mustEqual Some(link2)
        owcDao.createOwcLink(link3) mustEqual Some(link3)

        owcDao.findOwcLinksByHref("http://www.opengis.net/spec/owc-atom/1.0/req/core").size mustBe 1
        owcDao.findOwcLinksByRelAndHref("self", "http://portal.smart-project.info/context/smart-sac.owc.json").size mustBe 1

        val link3_1 = OwcLink("icon", Some("image/png"), "http://portal.smart-project.info/fs/images/nz_m.png", Some("New Zealand Flag"))
        owcDao.updateOwcLink(link3_1) mustEqual Some(link3_1)
        owcDao.findOwcLinksByRelAndHref("icon", "http://portal.smart-project.info/fs/images/nz_m.png").size mustBe 1
        owcDao.findOwcLinksByRelAndHref("icon", "http://portal.smart-project.info/fs/images/nz_m.png").headOption.get.title mustEqual Some("New Zealand Flag")

        owcDao.deleteOwcLink(link3) mustBe true
        owcDao.getAllOwcLinks.size mustEqual 2
      }

    }
  }

}
