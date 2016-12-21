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

import java.time.LocalDate
import java.util.UUID

import com.typesafe.config.ConfigFactory
import models.gmd.{MdMetadata, MdMetadataCitation}
import org.scalatest.GivenWhenThen
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.{Application, Configuration}


/**
  *
  */
class MdMetadataSpec extends PlaySpec with GivenWhenThen with OneAppPerSuite {

  /**
    * custom application config for testing
    */
  override lazy val app: Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.testdev.conf"))).build()

  /**
    * parses a resource into MdMetadata Option
    *
    * @param name name of the resource to parse
    * @return Option[MdMetadata]
    */
  def parsedResource(name: String): Option[MdMetadata] = {
    val mdMetadataJsVal = parsedResourceAsJsValue(name)
    MdMetadata.fromJson(mdMetadataJsVal)
  }

  /**
    * parses a resource into JsValue
    *
    * @param name name of the resource to parse
    * @return JsValue
    */
  def parsedResourceAsJsValue(name: String): JsValue = {
    val resource = this.getClass().getResource(name)
    Json.parse(scala.io.Source.fromURL(resource).getLines.mkString)
  }


  "MdMetadataExtent" should {
    "parse correctly" in (pending)
    /*
            "extent": {
              "description": "World",
              "referenceSystem": "urn:ogc:def:crs:EPSG::4328",
              "mapExtentCoordinates": [
              160.7447453125,
              -44.0115859375,
              188.7818546875,
              -29.6854140625
              ],
              "temporalExtent": ""
            },
    */
  }

  "MdMetadataCitation" when {
    "valid Json is parsed" in {
      val mdMetadataCitation = MdMetadataCitation.fromJson(
        (parsedResourceAsJsValue("gmd/MdMetadataFull.json") \ "citation").get)
      Then("result should be defined")
      mdMetadataCitation mustBe defined

      And("ciDate must contain correct value")
      mdMetadataCitation.get.ciDate mustBe LocalDate.of(2016, 1, 1)

      And("ciDateType must contain correct value")
      mdMetadataCitation.get.ciDateType mustBe "publication"
    }

    "invalid ciDateType is parsed" in {
      val mdMetadataCitation = MdMetadataCitation.fromJson(
        parsedResourceAsJsValue("gmd/MdMetadataCitationInvalidType.json"))
      Then("result must be NONE")
      mdMetadataCitation mustBe None
    }
  }

  "MdMetadataResponsibleParty" should {
    "parse correctly" in (pending)
    /*
    "responsibleParty": {
      "individualName": "Hans Wurst",
      "telephone": "+01 2334 5678910",
      "email": "wurst.hans@test.com",
      "pointOfContact": "publisher",
      "orgName": "Test Org",
      "orgWebLinkage": "http://www.test.com"
    },
    */
  }

  "MdMetadataDistribution" should {
    "parse correctly" in (pending)
    /*
    "distribution": {
      "useLimitation": "I don't know",
      "formatName": "CSW",
      "formatVersion": "web service type",
      "onlineResourceLinkage": "http://www.test.com/?service=CSW&version=2.0.2&request=GetCapabilities"
    }*/
  }

  "MdMetadata" should {
    "parse valid Json document" when {
      "all values are set " in {
        val mdMetadata = parsedResource("gmd/MdMetadataFull.json")

        Then("parsed result must be defined")
        mdMetadata mustBe defined

        And("FileIdentifier must be filled correctly")
        mdMetadata.get.fileIdentifier mustBe "weird-fileIdentifier"

        And("Title must be filled correctly")
        mdMetadata.get.title mustBe "Test Title"

        And("Abstrakt must be filled correctly")
        mdMetadata.get.abstrakt mustBe "This is an abstract abstract"

        And("Keyword List must have two entries")
        mdMetadata.get.keywords.size mustBe 2

        And("TopicCategory must be filled correctly")
        mdMetadata.get.topicCategoryCode mustBe "boundaries"

        And("HierarchyLevelName must be filled correctly")
        mdMetadata.get.hierarchyLevelName mustBe "nonGeographicDataset"

        And("Scale must be filled correctly")
        mdMetadata.get.scale mustBe "1000000"

        And("Extend must be defined")
        //        mdMetadata.get.extent mustBe defined

        And("Citation must be defined")
        mdMetadata.get.citation.isInstanceOf[MdMetadataCitation] mustBe true

        And("lineageStatement must be filled correctly")
        mdMetadata.get.lineageStatement mustBe ""

        And("ResponsibleParty must be defined")
        //        mdMetadata.get.responsibleParty mustBe defined

        And("Distribution must be defined")
        //        mdMetadata.get.distribution mustBe defined
        (pending)
      }

      "fileIdentifier is empty" in {
        val mdMetadata = parsedResource("gmd/MdMetadataFullEmptyFileIdentifier.json")

        Then("parsed result must be defined")
        mdMetadata mustBe defined

        And("generated fileIdentifier must be valid generated UUID")
        mdMetadata.get.fileIdentifier.trim.isEmpty mustBe false
        noException should be thrownBy UUID.fromString(mdMetadata.get.fileIdentifier)
      }

      "fileIdentifier is missing" in {
        val mdMetadata = parsedResource("gmd/MdMetadataFullMissingFileIdentifier.json")

        Then("parsed result must be defined")
        mdMetadata mustBe defined

        And("generated fileIdentifier must be valid generated UUID")
        mdMetadata.get.fileIdentifier.trim.isEmpty mustBe false
        noException should be thrownBy UUID.fromString(mdMetadata.get.fileIdentifier)
      }
    }

    "reproduce parsed document" ignore {
      val mdMetadata = parsedResource("gmd/MdMetadataFull.json")
      mdMetadata.get.toJson() mustBe parsedResourceAsJsValue("gmd/MdMetadataFull.json")
    }
  }
}
