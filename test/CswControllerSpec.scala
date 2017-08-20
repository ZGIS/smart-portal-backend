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
import akka.util.Timeout
import controllers.routes
import org.scalatestplus.play.OneAppPerTest
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.duration._

class CswControllerSpec extends WithDefaultTestFullAppAndDatabase with Results {

  // override test application with mock modules if needed/easier
  implicit lazy val materializer: Materializer = app.materializer

  "CswController" should {
    "return validValues JSON for 'topicCategories'" in {
      // val topicCategories = route(app, FakeRequest(GET, "/api/v1/csw/get-valid-values-for/topicCategory"))
      val Some(topicCategories) = route(app, FakeRequest(routes.CswController.getValidValuesFor("topicCategory")))
      status(topicCategories) must be(OK)

      val jsonTopicCategories = contentAsJson(topicCategories)
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
