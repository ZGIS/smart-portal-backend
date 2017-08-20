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
import com.google.inject.AbstractModule
import com.typesafe.config.ConfigFactory
import controllers.{CollectionsController, routes}
import info.smart.models.owc100.OwcContext
import org.scalatest.TestData
import org.scalatestplus.play.OneAppPerTest
import org.specs2.mock._
import play.api.cache.CacheApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.api.{Application, Configuration}
import services.{EmailService, OwcCollectionsService}
import utils.PasswordHashing


class CollectionsControllerSpec extends WithDefaultTest with OneAppPerTest with Results with Mockito {

  // creating mock instances for underlying required services for this controller
  private lazy val mockCollectionsService = mock[OwcCollectionsService]
  private lazy val mockEmailService = mock[EmailService]

  // creating "fake" Guice Module to inject mock service instances into test application, with the required dependencies
  class FakeModule extends AbstractModule {
    def configure(): Unit = {
      bind(classOf[PasswordHashing]).asEagerSingleton()
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

  "CollectionsController" should {
    "getCollections()" in {

      // just to provide stub
      val mockCache = mock[CacheApi]

      // configuration is only used to read application.(test).conf keys, and they are all defended with .getOrElse() if not accessible
      val appConfig = app.configuration

      // behaviour for mocked underlying collections service when controller calls injected service functions
      mockCollectionsService.getOwcContextsForUserAndId(None, None) returns Seq[OwcContext]()

      // explicitely instatiating tested controller
      val controller = new CollectionsController(
        cacheApi = mockCache,
        config = appConfig,
        emailService = mockEmailService,
        collectionsService = mockCollectionsService,
        passwordHashing = new PasswordHashing(appConfig)
      )

      // val fakeRequest = FakeRequest(POST, "/api/v1/collections", FakeHeaders(), AnyContentAsJson(Json.parse("""[{"id":"1","address":"my address"}]""")))
      val fakeRequest = FakeRequest(GET, "/api/v1/collections")

      val otherResult = controller.getCollections(None).apply(fakeRequest)
      val result = controller.getCollections(None).apply(FakeRequest())

      status(otherResult.run) must be(OK)
      status(result.run) must be(OK)
      val jsbody = contentAsJson(result.run)
      println(Json.stringify(jsbody))
      jsbody mustEqual Json.parse("""{"count":0,"collections":[]}""")
      (jsbody \ "count").toOption mustBe defined
      (jsbody \ "count").as[Int] mustBe 0
      (jsbody \ "collections").asOpt[List[OwcContext]] must contain(List())

      // alternative way of calling/testing routes and thus controllers ()without being able to "touch" controller themselves
      val Some(newResult) = route(app, FakeRequest(routes.CollectionsController.getCollections(None)))
      status(newResult) must be(OK)
      contentType(newResult) must contain ("application/json")

      val newBody = contentAsJson(newResult)
      println(Json.stringify(newBody))
      contentAsJson(newResult) mustEqual Json.parse("""{"count":0,"collections":[]}""")

      // TODO now should be evaluated if big FakeModule thing, or explicit controller instances
    }
  }
}
