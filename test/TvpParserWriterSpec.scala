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

import models.tvp.{Om2TvpParser, Wml2TvpParser}
import org.locationtech.spatial4j.context.SpatialContext
import org.scalatestplus.play.PlaySpec
import utils.ClassnameLogger

import scala.io.Source

class TvpParserWriterSpec extends PlaySpec with ClassnameLogger {

  private lazy val ctx = SpatialContext.GEO
  private lazy val om2Resource1  = this.getClass().getResource("tvp/sos-om2-one-series.xml")
  private lazy val wml2Resource1 = this.getClass().getResource("tvp/sos-wml2-one-series.xml")
  private lazy val om2Resource2  = this.getClass().getResource("tvp/sos-om2-two-series-same-obs-proc-two-features.xml")
  private lazy val wml2Resource2 = this.getClass().getResource("tvp/sos-wml2-two-series-same-obs-proc-two-features.xml")
  private lazy val om2Resource3  = this.getClass().getResource("tvp/sos-om2-mixed-series.xml")
  private lazy val wml2Resource3 = this.getClass().getResource("tvp/sos-wml2-mixed-series.xml")

  "OM2 TVP Parser" should {

    "om2 1 " in {
      val xml = Source.fromURL(om2Resource1)
      val parser = new Om2TvpParser()
      val ts = parser.parse(xml)
      // ts.size must be > 0
    }

    "om2 2 " in {
      val xml = Source.fromURL(om2Resource2)
      val parser = new Om2TvpParser()
      val ts = parser.parse(xml)
    }

    "om2 3 " in {
      val xml = Source.fromURL(om2Resource3)
      val parser = new Om2TvpParser()
      val ts = parser.parse(xml)
    }
  }

  "WML2 TVP Parser" should {

    "wml2 1 " in {
      val xml = Source.fromURL(wml2Resource1)
      val parser = new Wml2TvpParser()
      val ts = parser.parse(xml)
    }

    "wml2 2 " in {
      val xml = Source.fromURL(wml2Resource2)
      val parser = new Wml2TvpParser()
      val ts = parser.parse(xml)
    }

    "wml2 3 " in {
      val xml = Source.fromURL(wml2Resource3)
      val parser = new Wml2TvpParser()
      val ts = parser.parse(xml)
    }

  }

  "TVP JSON Writer" should {

  }

}
