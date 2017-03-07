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

import models.tvp._
import org.scalatestplus.play.PlaySpec
import utils.ClassnameLogger

import scala.io.Source

class TvpParserWriterSpec extends PlaySpec with ClassnameLogger {

  private lazy val om2Resource1  = this.getClass().getResource("tvp/sos-om2-one-series.xml")
  private lazy val wml2Resource1 = this.getClass().getResource("tvp/sos-wml2-one-series.xml")
  private lazy val om2Resource2  = this.getClass().getResource("tvp/sos-om2-two-series-same-obs-proc-two-features.xml")
  private lazy val wml2Resource2 = this.getClass().getResource("tvp/sos-wml2-two-series-same-obs-proc-two-features.xml")
  private lazy val om2Resource3  = this.getClass().getResource("tvp/sos-om2-mixed-series.xml")
  private lazy val wml2Resource3 = this.getClass().getResource("tvp/sos-wml2-mixed-series.xml")

  "OM2 TVP Parser" should {

    "om2 1 " in {
      val xml = Source.fromURL(om2Resource1)
      val parser = new XmlTvpParser()
      val ts = parser.parseOm2Measurements(xml)
      ts.size mustBe 7

      ts.filter(_.procedure.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/procedure/1679")).size mustBe 7
      ts.filter(_.obsProp.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/phenomenon/1679")).size mustBe 7
      ts.filter(_.foiId.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/feature/68")).size mustBe 7
      ts.filter(_.measUnit.equalsIgnoreCase("m")).size mustBe 7

      ts.exists(_.datetime.equalsIgnoreCase("2013-06-25T00:15:00.000Z")) mustBe true
      ts.exists(_.measValue.equalsIgnoreCase("10.58")) mustBe true
      ts.exists(_.geom.isDefined) mustBe false
    }

    "om2 2" in {
      val xml = Source.fromURL(om2Resource2)
      val parser = new XmlTvpParser()
      val ts = parser.parseOm2Measurements(xml)
      ts.size mustBe 9

      ts.filter(_.procedure.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/procedure/1679")).size mustBe 9
      ts.filter(_.obsProp.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/phenomenon/1679")).size mustBe 9

      ts.filter(_.foiId.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/feature/68")).size mustBe 7
      ts.filter(_.foiId.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/feature/14")).size mustBe 2

      ts.filter(_.measUnit.equalsIgnoreCase("m")).size mustBe 9

      ts.exists(_.datetime.equalsIgnoreCase("2013-06-25T00:15:00.000Z")) mustBe true
      ts.exists(_.measValue.equalsIgnoreCase("10.58")) mustBe true
      ts.exists(_.geom.isDefined) mustBe false
    }

    "om2 3" in {
      val xml = Source.fromURL(om2Resource3)
      val parser = new XmlTvpParser()
      val ts = parser.parseOm2Measurements(xml)
      ts.size mustBe 28

      ts.filter(_.procedure.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/procedure/1679")).size mustBe 10
      ts.filter(_.obsProp.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/phenomenon/1679")).size mustBe 10

      ts.filter(_.procedure.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/procedure/1662")).size mustBe 18
      ts.filter(_.obsProp.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/phenomenon/1662")).size mustBe 18

      ts.filter(_.foiId.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/feature/68")).size mustBe 16
      ts.filter(_.foiId.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/feature/14")).size mustBe 10
      ts.filter(_.foiId.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/feature/44")).size mustBe 2

      ts.filter(_.measUnit.equalsIgnoreCase("m")).size mustBe 10
      ts.filter(_.measUnit.equalsIgnoreCase("B0C")).size mustBe 18

      ts.exists(_.datetime.equalsIgnoreCase("2013-06-25T00:15:00.000Z")) mustBe true
      ts.exists(_.measValue.equalsIgnoreCase("10.58")) mustBe true
      ts.exists(_.geom.isDefined) mustBe false
    }
  }

  "WML2 TVP Parser" should {

    "wml2 1" in {
      val xml = Source.fromURL(wml2Resource1)
      val parser = new XmlTvpParser()
      val ts = parser.parseWml2TvpSeries(xml)
      ts.size mustBe 7

      ts.filter(_.procedure.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/procedure/1679")).size mustBe 7
      ts.filter(_.obsProp.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/phenomenon/1679")).size mustBe 7
      ts.filter(_.foiId.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/feature/68")).size mustBe 7
      ts.filter(_.measUnit.equalsIgnoreCase("m")).size mustBe 7

      ts.exists(_.datetime.equalsIgnoreCase("2013-06-25T00:15:00.000Z")) mustBe true
      ts.exists(_.measValue.equalsIgnoreCase("10.58")) mustBe true
      ts.exists(_.geom.isDefined) mustBe false
    }

    "wml2 2" in {
      val xml = Source.fromURL(wml2Resource2)
      val parser = new XmlTvpParser()
      val ts = parser.parseWml2TvpSeries(xml)
      ts.size mustBe 9

      ts.filter(_.procedure.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/procedure/1679")).size mustBe 9
      ts.filter(_.obsProp.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/phenomenon/1679")).size mustBe 9

      ts.filter(_.foiId.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/feature/68")).size mustBe 7
      ts.filter(_.foiId.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/feature/14")).size mustBe 2

      ts.filter(_.measUnit.equalsIgnoreCase("m")).size mustBe 9

      ts.exists(_.datetime.equalsIgnoreCase("2013-06-25T00:15:00.000Z")) mustBe true
      ts.exists(_.measValue.equalsIgnoreCase("10.58")) mustBe true
      ts.exists(_.geom.isDefined) mustBe false
    }

    "wml3 2" in {
      val xml = Source.fromURL(wml2Resource3)
      val parser = new XmlTvpParser()
      val ts = parser.parseWml2TvpSeries(xml)
      ts.size mustBe 28

      ts.filter(_.procedure.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/procedure/1679")).size mustBe 10
      ts.filter(_.obsProp.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/phenomenon/1679")).size mustBe 10

      ts.filter(_.procedure.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/procedure/1662")).size mustBe 18
      ts.filter(_.obsProp.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/phenomenon/1662")).size mustBe 18

      ts.filter(_.foiId.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/feature/68")).size mustBe 16
      ts.filter(_.foiId.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/feature/14")).size mustBe 10
      ts.filter(_.foiId.equalsIgnoreCase("http://vocab.smart-project.info/ngmp/feature/44")).size mustBe 2

      ts.filter(_.measUnit.equalsIgnoreCase("m")).size mustBe 10
      ts.filter(_.measUnit.equalsIgnoreCase("B0C")).size mustBe 18

      ts.exists(_.datetime.equalsIgnoreCase("2013-06-25T00:15:00.000Z")) mustBe true
      ts.exists(_.measValue.equalsIgnoreCase("10.58")) mustBe true
      ts.exists(_.geom.isDefined) mustBe false
    }

  }

  "TVP JSON Writer" should {

  }

}
