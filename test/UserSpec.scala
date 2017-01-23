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

import com.typesafe.config.ConfigFactory
import models.users._
import org.locationtech.spatial4j.context.SpatialContext
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}
import utils.PasswordHashing

/**
  * Test Spec for [[User]]
  */
class UserSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

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
          "local:test@blubb.com",
          "Hans",
          "Wurst",
          cryptPass,
          s"REGISTERED:$regLinkId",
          testTime)

        val testUser2 = User("test2@blubb.com",
          "local:test2@blubb.com",
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

        // findByAccountSubject
        userDao.findByAccountSubject("local:test@blubb.com") mustEqual Some(testUser1)

        // authenticate
        userDao.authenticate(testUser2.email, testPass) mustEqual Some(testUser2)

        // findUserByEmail
        userDao.findUserByEmail("test2@blubb.com") mustEqual Some(testUser2)

        // findUsersByToken

        // findRegisteredUsersByRegLink
        userDao.findRegisteredUsersByRegLink(regLinkId).size mustEqual 1

        // findRegisteredOnlyUsers
        val regUsers = userDao.findRegisteredOnlyUsers
        regUsers.size mustBe 1
        regUsers.headOption.get.accountSubject mustEqual "local:test@blubb.com"


        // findActiveUsers
        val activeUsers = userDao.findActiveUsers
        activeUsers.size mustBe 1
        activeUsers.headOption.get.accountSubject mustEqual "local:test2@blubb.com"
        activeUsers.headOption.get.email mustEqual "test2@blubb.com"

        // updateNoPass
        val testUser2_1 = User("test2@blubb.com",
          "local:test2@blubb.com",
          "Hans",
          "Wurst-Ebermann",
          cryptPassUpd,
          "ACTIVE:PROFILEUPDATE",
          testTime)

        userDao.updateNoPass(testUser2_1) mustEqual Some(testUser2_1)
        userDao.findByAccountSubject("local:test2@blubb.com").get.lastname mustEqual "Wurst-Ebermann"
        userDao.authenticate("test2@blubb.com", testPass).get.lastname mustEqual "Wurst-Ebermann"
        userDao.authenticate("test2@blubb.com", testPassUpd) mustEqual None

        // updatePassword
        userDao.updatePassword(testUser2_1) mustEqual Some(testUser2_1)
        userDao.findUserByEmail("test2@blubb.com").get.lastname mustEqual "Wurst-Ebermann"
        userDao.authenticate("test2@blubb.com", testPassUpd).get.lastname mustEqual "Wurst-Ebermann"
        userDao.authenticate("test2@blubb.com", testPass) mustEqual None

        // deleteUser
        userDao.deleteUser("test2@blubb.com") mustEqual true
        userDao.findAll.size mustBe 1

      }
    }

  }

}
