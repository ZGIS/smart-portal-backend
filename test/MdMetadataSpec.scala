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
import models.gmd._
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
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

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


  "MdMetadataExtent" when {
    "valid Json is parsed" in {
      val mdMetadataExtent = MdMetadataExtent.fromJson(
        (parsedResourceAsJsValue("gmd/MdMetadataFull.json") \ "extent").get)
      Then("result must be defined")
      mdMetadataExtent mustBe defined

      And("description must be filled correctly")
      mdMetadataExtent.get.description mustEqual "World"

      And("referenceSystem must be filled correctly")
      mdMetadataExtent.get.referenceSystem mustEqual "urn:ogc:def:crs:EPSG::4328"

      And("mapExtentCoordinates must be filled correctly")
      mdMetadataExtent.get.mapExtentCoordinates mustEqual
        List(160.7447453125, -44.0115859375, 188.7818546875, -29.6854140625)

      And("temporalExtent must be filled correctly")
      mdMetadataExtent.get.temporalExtent mustEqual ""
    }

    "invalid referenceSystem is parsed" in {
      val mdMetadataExtent = MdMetadataExtent.fromJson(
        parsedResourceAsJsValue("gmd/MdMetadataExtentInvalidReferenceSystem.json"))
      Then("result must be NONE")
      mdMetadataExtent mustBe None
    }

    "invalid mapExtentCoordinates is parsed" in {
      val mdMetadataExtent = MdMetadataExtent.fromJson(
        parsedResourceAsJsValue("gmd/MdMetadataExtentInvalidExtentCoordinates.json"))
      Then("result must be NONE")
      mdMetadataExtent mustBe None
    }
  }

  "MdMetadataCitation" when {
    "valid Json is parsed" in {
      val mdMetadataCitation = MdMetadataCitation.fromJson(
        (parsedResourceAsJsValue("gmd/MdMetadataFull.json") \ "citation").get)
      Then("result should be defined")
      mdMetadataCitation mustBe defined

      And("ciDate must contain correct value")
      mdMetadataCitation.get.ciDate mustEqual LocalDate.of(2016, 1, 1)

      And("ciDateType must contain correct value")
      mdMetadataCitation.get.ciDateType mustEqual "publication"
    }

    "invalid ciDateType is parsed" in {
      val mdMetadataCitation = MdMetadataCitation.fromJson(
        parsedResourceAsJsValue("gmd/MdMetadataCitationInvalidType.json"))
      Then("result must be NONE")
      mdMetadataCitation mustBe None
    }
  }

  "MdMetadataResponsibleParty" when {
    "valid Json is parsed" in {
      val mdMetadataResponsibleParty = MdMetadataResponsibleParty.fromJson(
        (parsedResourceAsJsValue("gmd/MdMetadataFull.json") \ "responsibleParty").get)
      Then("result must be defined")
      mdMetadataResponsibleParty mustBe defined

      And("individualName must be filled correctly")
      mdMetadataResponsibleParty.get.individualName mustEqual "Hans Wurst"

      And("telephone must be filled correctly")
      mdMetadataResponsibleParty.get.telephone mustEqual "+01 2334 5678910"

      And("email must be filled correctly")
      mdMetadataResponsibleParty.get.email mustEqual "wurst.hans@test.com"

      And("pointOfContact must be filled correctly")
      mdMetadataResponsibleParty.get.pointOfContact mustEqual "publisher"

      And("orgName must be filled correctly")
      mdMetadataResponsibleParty.get.orgName mustEqual "Test Org"

      And("orgWebLinkage must be filled correctly")
      mdMetadataResponsibleParty.get.orgWebLinkage mustEqual "http://www.test.com"
    }

    "invalid Json is parsed" in {
      (pending)
    }
  }

  "MdMetadataDistribution"  when {
    "valid Json is parsed" in {
      val mdMetadataDistribution = MdMetadataDistribution.fromJson(
        (parsedResourceAsJsValue("gmd/MdMetadataFull.json") \ "distribution").get)
      Then("result must be defined")
      mdMetadataDistribution mustBe defined

      And("useLimitation must be filled correctly")
      mdMetadataDistribution.get.useLimitation mustEqual "I don't know"

      And("formatName must be filled correctly")
      mdMetadataDistribution.get.formatName mustEqual "CSW"

      And("formatVersion must be filled correctly")
      mdMetadataDistribution.get.formatVersion mustEqual "web service type"

      And("onlineResourceLinkage must be filled correctly")
      mdMetadataDistribution.get.onlineResourceLinkage mustEqual "http://www.test.com/?service=CSW&version=2.0.2&request=GetCapabilities"
    }
    "invalid Json is parsed" in {
      (pending)
      /*
      "distribution": {
        "useLimitation": "I don't know",
        "formatName": "CSW",
        "formatVersion": "web service type",
        "onlineResourceLinkage": "http://www.test.com/?service=CSW&version=2.0.2&request=GetCapabilities"
      }*/
    }
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
        mdMetadata.get.title mustEqual "Test Title"

        And("Abstrakt must be filled correctly")
        mdMetadata.get.abstrakt mustEqual "This is an abstract abstract"

        And("Keyword List must have two entries")
        mdMetadata.get.keywords.size mustEqual 2

        And("TopicCategory must be filled correctly")
        mdMetadata.get.topicCategoryCode mustEqual "boundaries"

        And("HierarchyLevelName must be filled correctly")
        mdMetadata.get.hierarchyLevelName mustEqual "nonGeographicDataset"

        And("Scale must be filled correctly")
        mdMetadata.get.scale mustEqual "1000000"

        And("Extend must be defined")
        mdMetadata.get.extent.isInstanceOf[MdMetadataExtent] mustBe true

        And("Citation must be defined")
        mdMetadata.get.citation.isInstanceOf[MdMetadataCitation] mustBe true

        And("lineageStatement must be filled correctly")
        mdMetadata.get.lineageStatement mustEqual ""

        And("ResponsibleParty must be defined")
        mdMetadata.get.responsibleParty.isInstanceOf[MdMetadataResponsibleParty] mustBe true

        And("Distribution must be defined")
        mdMetadata.get.distribution.isInstanceOf[MdMetadataDistribution] mustBe true
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

    "reproduce parsed document" in {
      val mdMetadata = parsedResource("gmd/MdMetadataFull.json")
      mdMetadata.get.toJson() mustEqual parsedResourceAsJsValue("gmd/MdMetadataFull.json")
    }
  }
}
