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

import akka.stream.Materializer
import com.google.inject.AbstractModule
import com.typesafe.config.ConfigFactory
import controllers.security.{AuthenticationAction, OptionalAuthenticationAction, UserAction}
import controllers.{CollectionsController, routes}
import info.smart.models.owc100.OwcContext
import models.users.UserSession
import org.scalatest.TestData
import org.scalatestplus.play.OneAppPerTest
import org.specs2.mock._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.api.{Application, Configuration}
import services.{EmailService, OwcCollectionsService, UserService}
import utils.PasswordHashing


class CollectionsControllerSpec extends WithDefaultTest with OneAppPerTest with Results with Mockito {

  // creating mock instances for underlying required services for this controller
  private lazy val mockCollectionsService = mock[OwcCollectionsService].defaultReturn(Seq[OwcContext]())
  private lazy val mockEmailService = mock[EmailService]
  private lazy val mockUserService = mock[UserService].defaultReturn(None)
  private lazy val mockAuthenticationAction = mock[AuthenticationAction]
  private lazy val mockOptionalAuthenticationAction = mock[OptionalAuthenticationAction]
  private lazy val mockUserAction = mock[UserAction]

  // creating "fake" Guice Module to inject mock service instances into test application, with the required dependencies
  class FakeModule extends AbstractModule {
    def configure(): Unit = {
      bind(classOf[PasswordHashing]).asEagerSingleton()
      bind(classOf[UserService]).toInstance(mockUserService)
      bind(classOf[EmailService]).toInstance(mockEmailService)
      bind(classOf[OwcCollectionsService]).toInstance(mockCollectionsService)
    }
  }

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters, incl. fake guice module
  import scala.language.implicitConversions

  implicit override def newAppForTest(testData: TestData): Application = new GuiceApplicationBuilder()
    .overrides(new FakeModule)
    .loadConfig(new Configuration(ConfigFactory.load("application.test.conf")))
    .build()

  // needed for routes execution on tested controller
  implicit lazy val materializer: Materializer = app.materializer

  val demodata = new DemoData

  "CollectionsController" when {

    "request to getCollections(None)" in {
      (pending)

      Then("create mock components, particular mocked CollectionsServive")

      // behaviour for mocked underlying collections service when controller calls injected service functions
      mockCollectionsService.getOwcContextsForUserAndId(None, None) returns Seq[OwcContext]()
      mockUserService.getUserSessionByToken("sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.", "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.", "Default UA!1.0") returns None

      Then("create the Controller with mock components")

      // explicitely instatiating tested controller
      val controller = new CollectionsController(
        userService = mockUserService,
        emailService = mockEmailService,
        collectionsService = mockCollectionsService,
        authenticationAction = mockAuthenticationAction,
        optionalAuthenticationAction = mockOptionalAuthenticationAction,
        userAction = mockUserAction
      )

      Then("call request on the Controller")

      // behaviour for mocked underlying collections service when controller calls injected service functions
      mockCollectionsService.getOwcContextsForUserAndId(None, Some("fakeContextId")) returns Seq[OwcContext]()


      val fakeRequest = FakeRequest(routes.CollectionsController.getCollections(Some("fakeContextId")))
        .withHeaders("Content-Type" -> "application/json")
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")

      val result = controller.getCollections(None).apply(FakeRequest())
      val otherResult = controller.getCollections(None).apply(fakeRequest)

      Then("response status must be ok and contain zero collections")
      status(otherResult.run) must be(OK)
      status(result.run) must be(OK)
      val jsbody = contentAsJson(result.run)
      println(Json.stringify(jsbody))
      jsbody mustEqual Json.parse("""{"count":0,"collections":[]}""")
      (jsbody \ "count").toOption mustBe defined
      (jsbody \ "count").as[Int] mustBe 0
      (jsbody \ "collections").asOpt[List[OwcContext]] must contain(List())


      // TODO now should be evaluated if big FakeModule thing, or explicit controller instances
    }

    // GET  /api/v1/collections controllers.CollectionsController.getCollections(id: Option[String])
    "request to getCollections(fakeContextId)" in {

      // behaviour for mocked underlying collections service when controller calls injected service functions
      mockCollectionsService.getOwcContextsForUserAndId(None, Some("fakeContextId")) returns Seq[OwcContext]()
      mockCollectionsService.getOwcContextsForUserAndId(Some(demodata.testUser1("xxx")), Some("fakeContextId")) returns Seq(demodata.owcContext1)
      mockUserService.getUserSessionByToken(anyString, anyString, anyString) returns Some(
        UserSession("sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.",
          "Default UA/1.0",
          demodata.testUser1("xxx").email.value,
          "ACTIVE",
          ZonedDateTime.now(ZoneId.of("UTC"))))

      mockUserService.getUserSessionByToken("sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.", "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.", "Default UA/1.0") returns None

      val testRequest1 = FakeRequest(routes.CollectionsController.getCollections(Some("fakeContextId")))
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
      (js \ "count").toOption mustBe defined
    }

    // GET  /api/v1/collections/default controllers.CollectionsController.getPersonalDefaultCollection
    "request to getPersonalDefaultCollection" in {
      (pending)

      // behaviour for mocked underlying collections service when controller calls injected service functions
      mockCollectionsService.getUserDefaultOwcContext(demodata.testUser1("xxx")) returns Some(demodata.owcContext1)

      val testRequest1 = FakeRequest(routes.CollectionsController.getPersonalDefaultCollection())
        .withHeaders("Content-Type" -> "application/json")
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // GET  /api/v1/collections/default/files controllers.CollectionsController.getPersonalFilesFromDefaultCollection
    "request to getPersonalFilesFromDefaultCollection" in {
      (pending)

      val testRequest1 = FakeRequest(routes.CollectionsController.getPersonalFilesFromDefaultCollection())
        .withHeaders("Content-Type" -> "application/json")
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // POST /api/v1/collections controllers.CollectionsController.insertCollection
    "request to insertCollection" in {
      (pending)

      val testRequest1 = FakeRequest(routes.CollectionsController.insertCollection())
        .withHeaders("Content-Type" -> "application/json")
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")
        .withJsonBody(Json.parse("""{"var" -> "param"}"""))

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // POST /api/v1/collections/update controllers.CollectionsController.updateCollection

    "request to updateCollection" in {
      (pending)

      val testRequest1 = FakeRequest(routes.CollectionsController.updateCollection())
        .withHeaders("Content-Type" -> "application/json")
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")
        .withJsonBody(Json.parse("""{"var" -> "param"}"""))

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // GET  /api/v1/collections/delete controllers.CollectionsController.deleteCollection(id: String)
    "request to deleteCollection(fakeContextId)" in {
      (pending)

      val testRequest1 = FakeRequest(routes.CollectionsController.deleteCollection("fakeContextId"))
        .withHeaders("Content-Type" -> "application/json")
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // # experimental, entries add, replace, delete from collections
    // POST /api/v1/collections/entry controllers.CollectionsController.addResourceToCollection(collectionid: String)
    "request to addResourceToCollection(fakeContext)" in {
      (pending)

      val testRequest1 = FakeRequest(routes.CollectionsController.addResourceToCollection("fakeContext"))
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(Json.parse("""{"var" -> "param"}"""))

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // POST /api/v1/collections/entry/replace controllers.CollectionsController.replaceResourceInCollection(collectionid: String)
    "request to replaceResourceInCollection(fakeContextId)" in {
      (pending)

      val testRequest1 = FakeRequest(routes.CollectionsController.replaceResourceInCollection("fakeContextId"))
        .withHeaders("Content-Type" -> "application/json")
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")
        .withJsonBody(Json.parse("""{"var" -> "param"}"""))

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }

    // GET  /api/v1/collections/entry/delete controllers.CollectionsController.deleteResourceFromCollection(collectionid: String, resourceid: String)
    "request to deleteResourceFromCollection(fakeContextId, fakeresourceId)" in {
      (pending)

      val testRequest1 = FakeRequest(routes.CollectionsController.deleteResourceFromCollection("fakeContextId", "fakeResourceId"))
        .withHeaders("Content-Type" -> "application/json")
        .withHeaders("X-XSRF-TOKEN" -> "sv56fb7n8m90pü,mnbtvrchvbn.,bmvn.")

      val response = route(app, testRequest1).get

      Then("status must be OK")
      status(response) must be(OK)

      Then("contentType must be json")
      contentType(response) mustBe Some("application/json")
      // contentAsJson(response) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")

      val js = contentAsJson(response)
      (js \ "message").asOpt[String] mustBe defined
    }
  }
}
