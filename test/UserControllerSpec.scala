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
import controllers.routes
import org.specs2.mock.Mockito
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._

class UserControllerSpec extends WithDefaultTestFullAppAndDatabase with Results with Mockito {

  before {
    // here can go customisation
  }

  after {
    // here can go customisation
  }

  implicit lazy val materializer: Materializer = app.materializer

  lazy val PROFILENOPASS = """{"email":"alex@example.com","accountSubject":"local:alex@example.com","firstname":"Alex","lastname":"K"}"""
  lazy val LOGIN = """{"email":"alex@example.com","password":"testpass123"}"""
  lazy val FULLPROFILE = """{"email":"alex@example.com","accountSubject":"local:alex@example.com","firstname":"Alex","lastname":"K","password":"testpass123"}"""

  "UserController" when {

    "send 404 on a bad request and GETs at POST endpoint" in {
      route(app, FakeRequest(GET, "/api/v1/login/gconnect")).map(status(_)) mustBe Some(NOT_FOUND)
    }

    "send 401 on a unauthorized request" in {
      route(app, FakeRequest(GET, "/api/v1/users/self")).map(status(_)) mustBe Some(UNAUTHORIZED)
      route(app, FakeRequest(POST, "/api/v1/login/gconnect").withJsonBody(Json.parse(LOGIN))).map(
        status(_)) mustBe Some(BAD_REQUEST)
      route(app, FakeRequest(GET, "/api/v1/logout/gdisconnect")).map(status(_)) mustBe Some(UNAUTHORIZED)
    }

    "send 415 unsupported media type when JSON is required but not provided" in {
      route(app, FakeRequest(POST, "/api/v1/users/register")).map(status(_)) mustBe Some(UNSUPPORTED_MEDIA_TYPE)
      route(app, FakeRequest(GET, "/api/v1/users/delete/testuser")).map(status(_)) mustBe Some(NOT_FOUND)
    }

    "return preflight option headers" ignore {

      val preflight3 = route(app, FakeRequest(OPTIONS, "/api/v1/users/register")).get

      val preflightHeaders = headers(preflight3)
      status(preflight3) mustBe OK

      preflightHeaders must contain key "Access-Control-Allow-Origin"
      preflightHeaders must contain key "Allow"
      preflightHeaders must contain key "Access-Control-Allow-Methods"
      preflightHeaders must contain key "Access-Control-Allow-Headers"
      preflightHeaders must contain key "Access-Control-Allow-Credentials"

      headers(preflight3).get(
        "Access-Control-Allow-Headers").get mustBe "Origin, X-Requested-With, Content-Type, Accept, Referer, User-Agent, Authorization, X-XSRF-TOKEN, Cache-Control, Pragma, Date"
    }

    // POST  /api/v1/login/gconnect       controllers.UserController.gconnect
    "request to gconnect" in {
      (pending)

      // behaviour for mocked underlying collections service when controller calls injected service functions
      // mockCollectionsService.getOwcContextsForUserAndId(Some("authuser"), Some("fakeContextId")) returns Seq[OwcContext]()

      val testRequest1 = FakeRequest(routes.UserController.gconnect())
        .withHeaders("Content-Type" -> "application/json")
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")
        .withJsonBody(Json.parse("""{"user": "testuserid"}"""))

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      println(Json.stringify(contentAsJson(response)))

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // POST  /api/v1/login/oauth2callback       controllers.UserController.oauth2callback
    "request to oauth2callback" in {
      (pending)

      // behaviour for mocked underlying collections service when controller calls injected service functions
      // mockCollectionsService.getOwcContextsForUserAndId(Some("authuser"), Some("fakeContextId")) returns Seq[OwcContext]()

      val testRequest1 = FakeRequest(routes.UserController.oauth2callback())
        .withHeaders("Content-Type" -> "application/json")
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")
        .withJsonBody(Json.parse("""{"status": "granted"}"""))

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      println(Json.stringify(contentAsJson(response)))

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // GET   /api/v1/logout/gdisconnect   controllers.UserController.gdisconnect
    "request to gdisconnect" in {
      (pending)

      // behaviour for mocked underlying collections service when controller calls injected service functions
      // mockCollectionsService.getOwcContextsForUserAndId(Some("authuser"), Some("fakeContextId")) returns Seq[OwcContext]()

      val testRequest1 = FakeRequest(routes.UserController.gdisconnect())
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      println(Json.stringify(contentAsJson(response)))

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // # Users API
    // GET   /api/v1/users/self     controllers.UserController.userSelf
    "request to userSelf" in {
      (pending)

      // behaviour for mocked underlying collections service when controller calls injected service functions
      // mockCollectionsService.getOwcContextsForUserAndId(Some("authuser"), Some("fakeContextId")) returns Seq[OwcContext]()

      val testRequest1 = FakeRequest(routes.UserController.userSelf())
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      println(Json.stringify(contentAsJson(response)))

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // GET   /api/v1/users/profile/:email       controllers.UserController.getProfile(email: String)
    "request to getProfile(email)" in {
      (pending)

      // behaviour for mocked underlying collections service when controller calls injected service functions
      // mockCollectionsService.getOwcContextsForUserAndId(Some("authuser"), Some("fakeContextId")) returns Seq[OwcContext]()

      val testRequest1 = FakeRequest(routes.UserController.getProfile("testuser@gmail.com"))
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      println(Json.stringify(contentAsJson(response)))

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // POST  /api/v1/users/update   controllers.UserController.updateProfile
    "request to updateProfile" in {
      (pending)

      // behaviour for mocked underlying collections service when controller calls injected service functions
      // mockCollectionsService.getOwcContextsForUserAndId(Some("authuser"), Some("fakeContextId")) returns Seq[OwcContext]()

      val testRequest1 = FakeRequest(routes.UserController.updateProfile())
        .withHeaders("Content-Type" -> "application/json")
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")
        .withJsonBody(Json.parse(PROFILENOPASS))

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      println(Json.stringify(contentAsJson(response)))

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // POST  /api/v1/users/updatepass     controllers.UserController.updatePassword
    "request to updatePassword" in {
      (pending)

      // behaviour for mocked underlying collections service when controller calls injected service functions
      // mockCollectionsService.getOwcContextsForUserAndId(Some("authuser"), Some("fakeContextId")) returns Seq[OwcContext]()

      val testRequest1 = FakeRequest(routes.UserController.updatePassword())
        .withHeaders("Content-Type" -> "application/json")
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")
        .withJsonBody(Json.parse(LOGIN))

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      println(Json.stringify(contentAsJson(response)))

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // POST  /api/v1/users/resetpass      controllers.UserController.resetPasswordRequest
    "request to resetPasswordRequest" in {
      (pending)

      // behaviour for mocked underlying collections service when controller calls injected service functions
      // mockCollectionsService.getOwcContextsForUserAndId(Some("authuser"), Some("fakeContextId")) returns Seq[OwcContext]()

      val testRequest1 = FakeRequest(routes.UserController.resetPasswordRequest())
        .withHeaders("Content-Type" -> "application/json")
        .withFormUrlEncodedBody("username" -> "alex")

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      println(Json.stringify(contentAsJson(response)))

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // POST  /api/v1/users/resetpass/:linkId    controllers.UserController.resetPasswordRedeem(linkId: String)
    "request to resetPasswordRedeem(link)" in {
      (pending)

      // behaviour for mocked underlying collections service when controller calls injected service functions
      // mockCollectionsService.getOwcContextsForUserAndId(Some("authuser"), Some("fakeContextId")) returns Seq[OwcContext]()

      val testRequest1 = FakeRequest(routes.UserController.resetPasswordRedeem("link"))
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(Json.parse(LOGIN))

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      println(Json.stringify(contentAsJson(response)))

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // POST  /api/v1/users/register       controllers.UserController.registerUser
    "request to registerUser" in {
      (pending)

      // behaviour for mocked underlying collections service when controller calls injected service functions
      // mockCollectionsService.getOwcContextsForUserAndId(Some("authuser"), Some("fakeContextId")) returns Seq[OwcContext]()

      val testRequest1 = FakeRequest(routes.UserController.registerUser())
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(Json.parse(FULLPROFILE))

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      println(Json.stringify(contentAsJson(response)))

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // GET   /api/v1/users/register/:linkId     controllers.UserController.registerConfirm(linkId: String)
    "request to registerConfirm(link)" in {
      (pending)

      // behaviour for mocked underlying collections service when controller calls injected service functions
      // mockCollectionsService.getOwcContextsForUserAndId(Some("authuser"), Some("fakeContextId")) returns Seq[OwcContext]()

      val testRequest1 = FakeRequest(routes.UserController.registerConfirm("link"))

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      println(Json.stringify(contentAsJson(response)))

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // GET   /api/v1/users/deleteself     controllers.UserController.deleteSelf
    "request to deleteSelf" in {
      (pending)

      // behaviour for mocked underlying collections service when controller calls injected service functions
      // mockCollectionsService.getOwcContextsForUserAndId(Some("authuser"), Some("fakeContextId")) returns Seq[OwcContext]()

      val testRequest1 = FakeRequest(routes.UserController.deleteSelf())
        .withHeaders("Content-Type" -> "application/json")
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      println(Json.stringify(contentAsJson(response)))

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }
  }
}
