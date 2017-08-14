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
import com.typesafe.config.ConfigFactory
import controllers.CollectionsController
import info.smart.models.owc100.OwcContext
import org.scalatest.{GivenWhenThen, TestData}
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import org.specs2.mock._
import play.api.cache.CacheApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.api.{Application, Configuration}
import services.{EmailService, OwcCollectionsService}
import utils.{ClassnameLogger, PasswordHashing}

class CollectionsControllerSpec extends PlaySpec with OneAppPerTest with
  GivenWhenThen with Results with ClassnameLogger with Mockito {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  import scala.language.implicitConversions
  implicit override def newAppForTest(testData: TestData): Application = new GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()
  implicit lazy val materializer: Materializer = app.materializer

  "CollectionsController#getCollections" should {
    "be valid" in {

      val mockCache = mock[CacheApi]
      val mockConfiguration = mock[Configuration]
      val mockCollectionsService = mock[OwcCollectionsService]
      val mockEmailService = mock[EmailService]

      mockCollectionsService.getOwcContextsForUserAndId(None, None) returns Seq[OwcContext]()
      val controller = new CollectionsController(
        cacheApi = mockCache,
        config = mockConfiguration,
        emailService = mockEmailService,
        collectionsService = mockCollectionsService,
        passwordHashing = new PasswordHashing(mockConfiguration)
      )

      // val fakeRequest = FakeRequest(POST, "/api/v1/collections", FakeHeaders(), AnyContentAsJson(Json.parse("""[{"id":"1","address":"my address"}]""")))
      val fakeRequest = FakeRequest(GET, "/api/v1/collections")

      val otherResult = controller.getCollections(None).apply(fakeRequest)
      val result = controller.getCollections(None).apply(FakeRequest())
      status(otherResult.run) must be(OK)
      status(result.run) must be(OK)
      val body = contentAsJson(result.run)
    }
  }
}
