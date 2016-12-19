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

import org.scalatestplus.play.PlaySpec

import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.context.jts.JtsSpatialContext
import org.locationtech.spatial4j.io.ShapeIO
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.libs.json._
import org.locationtech.spatial4j.shape.{Point, Rectangle}
import utils.ClassnameLogger
import play.api.data.validation.ValidationError
import com.vividsolutions.jts.io._
import models.owc._

/**
  * Test Spec for [[RectangleReader]] and [[RectangleWriter]]
  */
class OwcGeometriesSpec extends PlaySpec {

  private lazy val ctx = SpatialContext.GEO
  private lazy val jtsCtx = JtsSpatialContext.GEO
  private lazy val geoJsonReader = ctx.getFormats().getReader(ShapeIO.GeoJSON)
  private lazy val jtsGeoJsonReader = jtsCtx.getFormats().getReader(ShapeIO.GeoJSON)

  "GeoJson reader " should {

    lazy val rectReads = new JtsPolygonReader()
    lazy val rectWrites = new RectangleWriter()

    "parse without errors" in {

    }

    val j1 = Json.parse("""{
               |              "type":"Polygon",
               |              "coordinates":[[[-180.0,-90.0],[-180.0,90.0],[180.0,90.0],[180.0,-90.0],[-180.0,-90.0]]]}""".stripMargin)

    val j2 = Json.parse("""{
               |   "type": "Polygon",
               |   "coordinates": [
               |    [
               |     [
               |      13.05,
               |      47.67
               |     ],
               |     [
               |      13.05,
               |      47.96
               |     ],
               |     [
               |      13.61,
               |      47.96
               |     ],
               |     [
               |      13.61,
               |      47.67
               |     ],
               |     [
               |      13.05,
               |      47.67
               |     ]
               |    ]
               |   ]
               |  }""".stripMargin)

    val j3 = Json.parse("""{
               |        "type": "Polygon",
               |        "coordinates": [
               |          [
               |            [
               |              164,
               |              -50
               |            ],
               |            [
               |              164,
               |              -31
               |            ],
               |            [
               |              182,
               |              -31
               |            ],
               |            [
               |              182,
               |              -50
               |            ],
               |            [
               |              164,
               |              -50
               |            ]
               |          ]
               |        ]
               |      }""".stripMargin)
  }

}
