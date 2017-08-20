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

import akka.stream.Materializer
import models.db.SessionHolder
import org.specs2.mock.Mockito
import play.api.db.Database
import play.api.db.evolutions.{ClassLoaderEvolutionsReader, Evolutions}
import play.api.inject.BindingKey
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._

class HomeControllerSpec extends WithDefaultTestFullAppAndDatabase with Results with Mockito {

  // needed for routes execution on tested controller
  implicit lazy val materializer: Materializer = app.materializer

  lazy val PROFILENOPASS = """{"email":"alex@example.com","accountSubject":"local:alex@example.com","firstname":"Alex","lastname":"K"}"""
  lazy val LOGIN = """{"email":"alex@example.com","password":"testpass123"}"""
  lazy val FULLPROFILE = """{"email":"alex@example.com","accountSubject":"local:alex@example.com","firstname":"Alex","lastname":"K","password":"testpass123"}"""


  val injectedSessionHolder: SessionHolder = app.injector.instanceOf[SessionHolder]
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
      route(app, FakeRequest(GET, "/api/v1/logout")).map(status(_)) mustBe Some(UNAUTHORIZED)
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
  }
}
