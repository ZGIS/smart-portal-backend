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
import utils.PasswordHashing

import scala.tools.reflect.WrappedProperties.AccessControl

/**
  * Test Spec for [[User]]
  */
class UserSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.testdev.conf"))).build()

  before {

  }

  after {

  }

  private lazy val ctx = SpatialContext.GEO

  "Users " can {

    "handle Users with DB" in {
      withTestDatabase { database =>
        // Evolutions.applyEvolutions(database, ClassLoaderEvolutionsReader.forPrefix("testh2db/"))

        val passwordHashing = new PasswordHashing(app.configuration)
        val userDao = new UserDAO(database, passwordHashing)

        val regLinkId = java.util.UUID.randomUUID().toString()
        val testPass = "testpass123"
        val testPassUpd = "testpass12345"
        val testTime = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())
        val cryptPass = passwordHashing.createHash(testPass)
        val cryptPassUpd = passwordHashing.createHash(testPassUpd)

        val testUser1 = User("test@blubb.com",
          "test",
          "Hans",
          "Wurst",
          cryptPass,
          s"REGISTERED:$regLinkId",
          testTime)

        val testUser2 = User("test2@blubb.com",
          "test2",
          "Hans",
          "Wurst",
          cryptPass,
          "ACTIVE:REGCONFIRMED",
          testTime)

        // create
        userDao.create(testUser1) mustEqual Some(testUser1)

        // createWithNps
        userDao.create(testUser2) mustEqual Some(testUser2)

        userDao.findAll.size mustEqual 2

        // findByUsername
        userDao.findByUsername("test") mustEqual Some(testUser1)

        // authenticate
        userDao.authenticate(testUser2.username, testPass) mustEqual Some(testUser2)

        // findUserByEmail
        userDao.findUserByEmail("test2@blubb.com") mustEqual Some(testUser2)

        // findUsersByToken

        // findRegisteredUsersByRegLink
        userDao.findRegisteredUsersByRegLink(regLinkId).size mustEqual 1

        // findRegisteredOnlyUsers
        val regUsers = userDao.findRegisteredOnlyUsers
        regUsers.size mustBe 1
        regUsers.headOption.get.username mustEqual "test"


        // findActiveUsers
        val activeUsers = userDao.findActiveUsers
        activeUsers.size mustBe 1
        activeUsers.headOption.get.username mustEqual "test2"

        // updateNoPass
        val testUser2_1 = User("test2@blubb.com",
          "test2",
          "Hans",
          "Wurst-Ebermann",
          cryptPassUpd,
          "ACTIVE:PROFILEUPDATE",
          testTime)

        userDao.updateNoPass(testUser2_1) mustEqual Some(testUser2_1)
        userDao.findByUsername("test2").get.lastname mustEqual "Wurst-Ebermann"
        userDao.authenticate("test2", testPass).get.lastname mustEqual "Wurst-Ebermann"
        userDao.authenticate("test2", testPassUpd) mustEqual None

        // updatePassword
        userDao.updatePassword(testUser2_1) mustEqual Some(testUser2_1)
        userDao.findByUsername("test2").get.lastname mustEqual "Wurst-Ebermann"
        userDao.authenticate("test2", testPassUpd).get.lastname mustEqual "Wurst-Ebermann"
        userDao.authenticate("test2", testPass) mustEqual None

        // deleteUser
        userDao.deleteUser("test2") mustEqual true
        userDao.findAll.size mustBe 1

      }
    }

  }

}
