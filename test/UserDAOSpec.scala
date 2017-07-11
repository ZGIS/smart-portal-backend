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

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import com.typesafe.config.ConfigFactory
import controllers.{ProfileJs, RegisterJs}
import models.db.SessionHolder
import models.users._
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}
import uk.gov.hmrc.emailaddress.EmailAddress
import utils.PasswordHashing
import play.api.libs.json._

/**
  * Test Spec for [[User]] and [[UserDAO]]
  */
class UserDAOSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  import scala.language.implicitConversions

  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  "UserDAO" can {

    "handle Users with DB" in {
      withTestDatabase { database =>
        // Evolutions.applyEvolutions(database, ClassLoaderEvolutionsReader.forPrefix("testh2db/"))
        val sessionHolder = new SessionHolder(database)
        sessionHolder.viaConnection { implicit connection =>


          val passwordHashing = new PasswordHashing(app.configuration)

          val regLinkId = java.util.UUID.randomUUID().toString
          val testPass = "testpass123"
          val testPassUpd = "testpass12345"
          val testTime = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())
          val cryptPass = passwordHashing.createHash(testPass)
          val cryptPassUpd = passwordHashing.createHash(testPassUpd)

          val testUser1 = User(EmailAddress("test@blubb.com"),
            "local:test@blubb.com",
            "Hans",
            "Wurst",
            cryptPass,
            s"${StatusToken.REGISTERED}:$regLinkId",
            testTime)

          val testUser2 = User(EmailAddress("test2@blubb.com"),
            "local:test2@blubb.com",
            "Hans",
            "Wurst",
            cryptPass,
            s"${StatusToken.ACTIVE}:REGCONFIRMED",
            testTime)

          // create
          UserDAO.createUser(testUser1) mustEqual Some(testUser1)

          // createUser
          UserDAO.createUser(testUser2) mustEqual Some(testUser2)

          UserDAO.getAllUsers.size mustEqual 2

          // findByAccountSubject
          UserDAO.findByAccountSubject("local:test@blubb.com") mustEqual Some(testUser1)

          // findUserByEmailAsString
          passwordHashing.validatePassword(testPass, UserDAO.findUserByEmailAsString(testUser2.email).get.password) mustBe true
          // findUserByEmailAsString
          UserDAO.findUserByEmailAsString("test2@blubb.com") mustEqual Some(testUser2)

          // findUsersByToken
          UserDAO.findUsersByToken(StatusToken.ACTIVE, "%").size mustEqual 1
          UserDAO.findUsersByToken(StatusToken.REGISTERED, "%").size mustEqual 1

          // findRegisteredUsersByRegLink
          UserDAO.findRegisteredUsersWithRegLink(regLinkId).size mustEqual 1

          // findRegisteredOnlyUsers
          val regUsers = UserDAO.findRegisteredOnlyUsers
          regUsers.size mustBe 1
          regUsers.headOption.get.accountSubject mustEqual "local:test@blubb.com"

          // findActiveUsers
          val activeUsers = UserDAO.findActiveUsers
          activeUsers.size mustBe 1
          activeUsers.headOption.get.accountSubject mustEqual "local:test2@blubb.com"
          activeUsers.headOption.get.email mustEqual EmailAddress("test2@blubb.com")



          // updateNoPass
          val emailLinkId = java.util.UUID.randomUUID().toString()
          val testUser2_1 = User(EmailAddress("test2@blubb.com"),
            "local:test2@blubb.com",
            "Hans",
            "Wurst-Ebermann",
            cryptPassUpd,
            s"${StatusToken.EMAILVALIDATION}:$emailLinkId",
            testTime)

          UserDAO.updateNoPass(testUser2_1) mustEqual Some(testUser2_1)
          UserDAO.findByAccountSubject("local:test2@blubb.com").get.lastname mustEqual "Wurst-Ebermann"
          passwordHashing.validatePassword(testPass, UserDAO.findUserByEmailAddress(EmailAddress("test2@blubb.com")).get.password) mustBe true
          passwordHashing.validatePassword(testPassUpd, UserDAO.findUserByEmailAddress(EmailAddress("test2@blubb.com")).get.password) mustBe false

          UserDAO.findEmailValidationRequiredUsersWithRegLink(emailLinkId).size mustEqual 1

          val resetLink = java.util.UUID.randomUUID().toString
          val testUser2_2 = testUser2_1.copy(laststatustoken = s"${StatusToken.PASSWORDRESET}:$resetLink")
          UserDAO.updateNoPass(testUser2_2) mustEqual Some(testUser2_2)
          UserDAO.findUsersByPassResetLink(resetLink).size mustEqual 1

          // updatePassword
          UserDAO.updatePassword(testUser2_1) mustEqual Some(testUser2_1)
          UserDAO.findUserByEmailAddress(EmailAddress("test2@blubb.com")).get.lastname mustEqual "Wurst-Ebermann"
          UserDAO.findUserByEmailAsString("test2@blubb.com").get.lastname mustEqual "Wurst-Ebermann"
          passwordHashing.validatePassword(testPassUpd, UserDAO.findUserByEmailAddress(EmailAddress("test2@blubb.com")).get.password) mustBe true
          passwordHashing.validatePassword(testPass, UserDAO.findUserByEmailAddress(EmailAddress("test2@blubb.com")).get.password) mustBe false



          // deleteUser
          UserDAO.deleteUser("test2@blubb.com") mustEqual true
          UserDAO.getAllUsers.size mustBe 1
          UserDAO.deleteUser(testUser1) mustEqual true
          UserDAO.getAllUsers.size mustBe 0
        }
      }
    }
  }

  "User" can {

    "encode Users required Json" in {

      val passwordHashing = new PasswordHashing(app.configuration)
      val testPass = "testpass123"
      val testPassUpd = "testpass123upd"
      val cryptPass = passwordHashing.createHash(testPass)
      val testTime = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())

      val testUser2 = User(EmailAddress("test2@blubb.com"),
        "local:test2@blubb.com",
        "Hans",
        "Wurst",
        cryptPass,
        s"${StatusToken.ACTIVE}:REGCONFIRMED",
        testTime)

      // User Reads and Writes
      Json.toJson(testUser2)(controllers.userWrites) mustEqual Json.parse(s"""{
                                                                            |"email":"test2@blubb.com",
                                                                            |"accountSubject":"local:test2@blubb.com",
                                                                            |"firstname":"Hans",
                                                                            |"lastname":"Wurst",
                                                                            |"password":"$cryptPass",
                                                                            |"laststatustoken":"${StatusToken.ACTIVE}:REGCONFIRMED",
                                                                            |"laststatuschange":"${testTime.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)}"
                                                                            |}""".stripMargin)

      Json.parse(s"""{
                    |"email":"test2@blubb.com",
                    |"accountSubject":"local:test2@blubb.com",
                    |"firstname":"Hans",
                    |"lastname":"Wurst",
                    |"password":"$cryptPass",
                    |"laststatustoken":"${StatusToken.ACTIVE}:REGCONFIRMED",
                    |"laststatuschange":"${testTime.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)}"
                    |}""".stripMargin).validate[User](controllers.userReads).get mustEqual testUser2

      // ProfileJs Reads and Writes
      testUser2.asProfileJs mustEqual ProfileJs(testUser2.email, testUser2.accountSubject, testUser2.firstname, testUser2.lastname)

      Json.toJson(testUser2.asProfileJs)(controllers.profileJsWrites) mustEqual Json.parse("""{
          |"email":"test2@blubb.com",
          |"accountSubject":"local:test2@blubb.com",
          |"firstname":"Hans",
          |"lastname":"Wurst"
          |}""".stripMargin)

      Json.parse("""{
                   |"email":"test2@blubb.com",
                   |"accountSubject":"local:test2@blubb.com",
                   |"firstname":"Hans",
                   |"lastname":"Wurst"
                   |}""".stripMargin).validate[ProfileJs].get mustEqual testUser2.asProfileJs

      // RegisterJs Reads
      Json.parse(s"""{
                   |"email":"test2@blubb.com",
                   |"accountSubject":"local:test2@blubb.com",
                   |"firstname":"Hans",
                   |"lastname":"Wurst",
                   |"password": "$testPass"
                   |}""".stripMargin).validate[RegisterJs].get mustEqual
        RegisterJs(testUser2.email, testUser2.accountSubject, testUser2.firstname, testUser2.lastname, testPass)

      Json.parse(s"""{
                    |"email":"test2@blubb.com",
                    |"accountSubject":"local:test2@blubb.com",
                    |"firstname":"Hans",
                    |"lastname":"Wurst",
                    |"password": "short"
                    |}""".stripMargin).validate[RegisterJs].isError mustBe true

      // LoginCredentials Reads
      import controllers.LoginCredentialsFromJsonReads
      Json.parse(s"""{
                    |"email":"test2@blubb.com",
                    |"password": "$testPass"
                    |}""".stripMargin).validate[LoginCredentials].get mustEqual
        LoginCredentials(testUser2.email, testPass)

      Json.parse(s"""{
                    |"email":"test2@blubb.com",
                    |"password": "short"
                    |}""".stripMargin).validate[LoginCredentials].isError mustBe true

      // PasswordUpdateCredentials Reads {"email":"alex","oldpassword":"testpass123", "newpassword":"testpass123"}
      import controllers.passwordUpdateCredentialsJsReads
      Json.parse(s"""{
                    |"email":"test2@blubb.com",
                    |"oldpassword": "$testPass",
                    |"newpassword": "$testPassUpd"
                    |}""".stripMargin).validate[PasswordUpdateCredentials].get mustEqual
        PasswordUpdateCredentials(testUser2.email, testPass, testPassUpd)

      Json.parse(s"""{
                    |"email":"test2@blubb.com",
                    |"oldpassword": "$testPass",
                    |"newpassword": "$testPass"
                    |}""".stripMargin).validate[PasswordUpdateCredentials].isError mustBe true

      Json.parse(s"""{
                    |"email":"test2@blubb.com",
                    |"oldpassword": "$testPass",
                    |"newpassword": "short"
                    |}""".stripMargin).validate[PasswordUpdateCredentials].isError mustBe true

      // GAuthCredentials Reads authcode accesstype
      import controllers.GAuthCredentialsFromJsonReads
      Json.parse(s"""{
                    |"authcode": "$testPass",
                    |"accesstype": "LOGIN"
                    |}""".stripMargin).validate[GAuthCredentials].get mustEqual
        GAuthCredentials(testPass, "LOGIN")

      Json.parse(s"""{
                    |"authcode": "$testPass",
                    |"accesstype": "REGISTER"
                    |}""".stripMargin).validate[GAuthCredentials].get mustEqual
        GAuthCredentials(testPass, "REGISTER")

      Json.parse(s"""{
                    |"authcode": "$testPass",
                    |"accesstype": "NOTVALID"
                    |}""".stripMargin).validate[GAuthCredentials].isError mustBe true
    }
  }
}
