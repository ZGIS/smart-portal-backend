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

import java.security.InvalidParameterException
import java.time.{ZoneId, ZonedDateTime}

import akka.stream.Materializer
import com.google.inject.AbstractModule
import com.typesafe.config.ConfigFactory
import controllers.routes
import mockws.MockWS
import models.ErrorResult
import models.db.DatabaseSessionHolder
import models.users.{LoginCredentials, StatusToken, User, UserDAO}
import org.specs2.mock.Mockito
import play.api.db.Database
import play.api.db.evolutions.{ClassLoaderEvolutionsReader, Evolutions}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import services.{EmailService, OwcCollectionsService}
import uk.gov.hmrc.emailaddress.{EmailAddress, PlayJsonFormats}
import utils.PasswordHashing

class HomeControllerSpec extends WithDefaultTestFullAppAndDatabase with Results with Mockito {

  // creating mock instances for underlying required services for this controller
  private lazy val mockCollectionsService = mock[OwcCollectionsService]
  private lazy val mockEmailService = mock[EmailService]

  val mockws = MockWS {
    case (GET, "https://www.google.com/recaptcha/api/siteverify") => Action { Ok(Json.parse("""{ "success": true }""")) }
  }

  // creating "fake" Guice Module to inject mock service instances into test application, with the required dependencies
  class FakeModule extends AbstractModule {
    def configure(): Unit = {
      bind(classOf[PasswordHashing]).asEagerSingleton()
      bind(classOf[DatabaseSessionHolder]).asEagerSingleton()
      bind(classOf[WSClient]).toInstance(mockws)
      bind(classOf[EmailService]).toInstance(mockEmailService)
      bind(classOf[OwcCollectionsService]).toInstance(mockCollectionsService)
    }
  }

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters, incl. fake guice module
  // import scala.language.implicitConversions
  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(new FakeModule)
    .loadConfig(new Configuration(ConfigFactory.load("application.test.conf")))
    .build()

  // needed for routes execution on tested controller
  implicit lazy val materializer: Materializer = app.materializer

  lazy val PROFILENOPASS = """{"email":"alex@example.com","accountSubject":"local:alex@example.com","firstname":"Alex","lastname":"K"}"""
  lazy val LOGIN = """{"email":"alex@example.com","password":"testpass123"}"""
  lazy val FULLPROFILE = """{"email":"alex@example.com","accountSubject":"local:alex@example.com","firstname":"Alex","lastname":"K","password":"testpass123"}"""


  val passwordHashing: PasswordHashing = app.injector.instanceOf[PasswordHashing]

  val injectedSessionHolder: DatabaseSessionHolder = app.injector.instanceOf[DatabaseSessionHolder]
  val database: Database = injectedSessionHolder.db

  before {
    Evolutions.applyEvolutions(database, ClassLoaderEvolutionsReader.forPrefix("testh2db/"))
  }

  "HomeController" when {

    "bad request and GETs at POST endpoint" in {

      Then("send 404")
      route(app, FakeRequest(GET, "/api/v1/login")).map(status(_)) mustBe Some(NOT_FOUND)
    }

    "send 401 on a unauthorized request" in {

      route(app, FakeRequest(POST, "/api/v1/login").withJsonBody(Json.parse(LOGIN))).map(status(_)) mustBe Some(
        UNAUTHORIZED)
    }

    "send 415 unsupported media type when JSON is required but not provided" in {
      route(app, FakeRequest(POST, "/api/v1/login").withTextBody(LOGIN)).map(status(_)) mustBe Some(
        UNSUPPORTED_MEDIA_TYPE)
      route(app,
        FakeRequest(POST, "/api/v1/login") withXmlBody (<email>alex@blub.de</email> <password>testpass123</password>))
        .map(status(_)) mustBe Some(UNSUPPORTED_MEDIA_TYPE)
    }

    "render the index page" in {
      val home = route(app, FakeRequest(GET, "/")).get

      status(home) must be(OK)
      contentType(home) mustBe Some("application/json")
      contentAsJson(home) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")
    }

    "return preflight option headers" ignore {

      val preflight1 = route(app, FakeRequest(OPTIONS, "/api/v1/discovery")).get

      status(preflight1) mustBe OK

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

    }

    // GET controllers.HomeController.index
    "request to index" in {
      val response = route(app, FakeRequest(routes.HomeController.index())).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")
    }

    // GET /api/v1/discovery controllers.HomeController.discovery(fields: Option[String])
    "request to discovery" in {
      val testRequest1 = FakeRequest(routes.HomeController.discovery(None))

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      val js = contentAsJson(response)
      (js \ "appName").asOpt[String] mustBe defined
    }

    // POST /api/v1/login controllers.HomeController.login
    "request to login" in {

      val testPass = "testpass123"
      val testTime = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())
      val cryptPass = passwordHashing.createHash(testPass)

      val testUser = User(EmailAddress("test2@blubb.com"),
        "local:test2@blubb.com",
        "Hans",
        "Wurst",
        cryptPass,
        s"${StatusToken.ACTIVE}:REGCONFIRMED",
        testTime)

      // create a testuser
      injectedSessionHolder.viaConnection { implicit connection =>
        UserDAO.createUser(testUser).getOrElse(throw new InvalidParameterException("couldn't create testuser, this shouldn't happen"))
      }

      implicit val emailWrite = PlayJsonFormats.emailAddressWrites
      implicit val loginWrites = Json.writes[LoginCredentials]
      // val testRequest1 = FakeRequest(POST, routes.HomeController.login().url, FakeHeaders(Seq("Content-Type" -> "application/json")),
      //   AnyContentAsJson(Json.toJson(LoginCredentials(testUser.email, testPass))))

      val testRequest1 = FakeRequest(routes.HomeController.login())
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(Json.toJson(LoginCredentials(testUser.email, testPass)))

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

    }

    // GET /api/v1/recaptcha/validate controllers.HomeController.recaptchaValidate(recaptcaChallenge: String)
    "request to recaptchaValidate" in {

      /*
      fail is ErrorResult {"message":"User email or password wrong.","details":"[\"invalid-input-response\"]"}
      recaptcha JSON responses are like

      {
      "success": true|false,
      "challenge_ts": timestamp,  // timestamp of the challenge load (ISO format yyyy-MM-dd'T'HH:mm:ssZZ)
      "hostname": string,         // the hostname of the site where the reCAPTCHA was solved
      "error-codes": [...]        // optional
      }
      */

      val testRequest1 = FakeRequest(routes.HomeController.recaptchaValidate("XSDFGH45_NHJKUINBECERG45-ERFVRB"))

      val response = route(app, testRequest1).get

      Then("status must be OK (we mock Google Api Ws call)")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")

      Then("error should be available, but this should be put somewhere else where we ca nmock the WSClient")
      val errorJs = contentAsJson(response).validate[ErrorResult].asOpt
      errorJs mustBe defined
      println(Json.stringify(Json.toJson(errorJs.get)))
      // it's funny that ErrorResult also validates for positive {"message": "granted"}
    }
  }
}
