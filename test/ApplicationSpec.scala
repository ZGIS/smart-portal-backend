/*
 * Copyright (c) 2011-2017 Interfaculty Department of Geoinformatics, University of
 * Salzburg (Z_GIS) & Institute of Geological and Nuclear Sciences Limited (GNS Science)
 * in the SMART Aquifer Characterisation (SAC) programme funded by the New Zealand
 * Ministry of Business, Innovation and Employment (MBIE)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play._
import play.api.db.evolutions._
import play.api.inject.guice._
import play.api.libs.json._
import play.api.test.Helpers._
import play.api.test._
import play.api.{Application, Configuration}



/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  * For more information, consult the wiki.
  */
class ApplicationSpec extends WithDefaultTest with OneAppPerTest with BeforeAndAfter with WithTestDatabase  {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  import scala.language.implicitConversions
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  before {
    // EMPTY
  }

  after {
    // EMPTY
  }

  lazy val PROFILENOPASS = """{"email":"alex@example.com","accountSubject":"local:alex@example.com","firstname":"Alex","lastname":"K"}"""
  lazy val LOGIN = """{"email":"alex@example.com","password":"testpass123"}"""
  lazy val FULLPROFILE = """{"email":"alex@example.com","accountSubject":"local:alex@example.com","firstname":"Alex","lastname":"K","password":"testpass123"}"""

  "Routes" should {
    "send 404 on a bad request and GETs at POST endpoint" in {
      route(app, FakeRequest(GET, "/api/v1/login")).map(status(_)) mustBe Some(NOT_FOUND)
      route(app, FakeRequest(GET, "/api/v1/login/gconnect")).map(status(_)) mustBe Some(NOT_FOUND)
    }

    "send 401 on a unauthorized request" in {
      withTestDatabase { database =>
        Evolutions.applyEvolutions(database, ClassLoaderEvolutionsReader.forPrefix("testh2db/"))
        route(app, FakeRequest(POST, "/api/v1/login").withJsonBody(Json.parse(LOGIN))).map(status(_)) mustBe Some(
          UNAUTHORIZED)
        route(app, FakeRequest(POST, "/api/v1/login/gconnect").withJsonBody(Json.parse(LOGIN))).map(
          status(_)) mustBe Some(BAD_REQUEST)
        route(app, FakeRequest(GET, "/api/v1/users/self")).map(status(_)) mustBe Some(UNAUTHORIZED)
        route(app, FakeRequest(GET, "/api/v1/logout")).map(status(_)) mustBe Some(UNAUTHORIZED)
        route(app, FakeRequest(GET, "/api/v1/logout/gdisconnect")).map(status(_)) mustBe Some(UNAUTHORIZED)
      }
    }

    "send 415 unsupported media type when JSON is required but not provided" in {
      route(app, FakeRequest(POST, "/api/v1/login").withTextBody(LOGIN)).map(status(_)) mustBe Some(
        UNSUPPORTED_MEDIA_TYPE)
      route(app,
        FakeRequest(POST, "/api/v1/login") withXmlBody (<email>alex@blub.de</email> <password>testpass123</password>))
        .map(status(_)) mustBe Some(UNSUPPORTED_MEDIA_TYPE)
      route(app, FakeRequest(POST, "/api/v1/users/register")).map(status(_)) mustBe Some(UNSUPPORTED_MEDIA_TYPE)
      route(app, FakeRequest(GET, "/api/v1/users/delete/testuser")).map(status(_)) mustBe Some(NOT_FOUND)
    }
  }

  "HomeController" should {
    "render the index page" in {
      val home = route(app, FakeRequest(GET, "/")).get

      status(home) mustBe OK
      contentType(home) mustBe Some("application/json")
      contentAsJson(home) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")
    }

    // TODO AK enable Filters module in test guice builder
    "return preflight option headers" ignore {

      val preflight1 = route(app, FakeRequest(OPTIONS, "/api/v1/discovery")).get

      // status(preflight1) mustBe OK

      val preflightHeaders = headers(preflight1)

      preflightHeaders must contain key "Access-Control-Allow-Origin"
      preflightHeaders must contain key "Allow"
      preflightHeaders must contain key "Access-Control-Allow-Methods"
      preflightHeaders must contain key "Access-Control-Allow-Headers"
      preflightHeaders must contain key "Access-Control-Allow-Credentials"

      preflightHeaders.get("Access-Control-Allow-Origin").get mustBe "*"
      preflightHeaders.get("Allow").get mustBe "*"
      preflightHeaders.get("Access-Control-Allow-Methods").get mustBe "GET, POST, OPTIONS"
      preflightHeaders.get(
        "Access-Control-Allow-Headers").get mustBe "Origin, X-Requested-With, Content-Type, Accept, Referer, User-Agent, Authorization, X-XSRF-TOKEN, Cache-Control, Pragma, Date"
      preflightHeaders.get("Access-Control-Allow-Credentials").get mustBe "true"

      val preflight2 = route(app, FakeRequest(OPTIONS, "/api/v1/login")).get
      status(preflight2) mustBe OK
      headers(preflight2).get("Access-Control-Allow-Credentials").get mustBe "true"

      val preflight3 = route(app, FakeRequest(OPTIONS, "/api/v1/users/register")).get
      status(preflight3) mustBe OK
      headers(preflight2).get(
        "Access-Control-Allow-Headers").get mustBe "Origin, X-Requested-With, Content-Type, Accept, Referer, User-Agent, Authorization, X-XSRF-TOKEN, Cache-Control, Pragma, Date"
    }
  }

  "CswController" should {
    "return validValues JSON for 'topicCategories'" in {
      val topicCategories = route(app, FakeRequest(GET, "/api/v1/csw/get-valid-values-for/topicCategory"))
      topicCategories mustBe defined
      status(topicCategories.get) mustBe 200
      val jsonTopicCategories = contentAsJson(topicCategories.get)
      (jsonTopicCategories \ "standardValue").toOption mustBe defined
      (jsonTopicCategories \ "standardValue").as[Int] mustBe 2
      (jsonTopicCategories \ "descriptions").asOpt[List[String]] mustBe None
    }

    "return error on validValues for unknown category" in {
      val topicCategories = route(app, FakeRequest(GET, "/api/v1/csw/get-valid-values-for/bogus"))
      topicCategories mustBe defined
      status(topicCategories.get) mustBe 400
      contentAsString(topicCategories.get) mustBe "{\"message\":\"There are no valid values for 'bogus'\"}"
    }
  }
}
