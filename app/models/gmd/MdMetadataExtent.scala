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

package models.gmd

import javax.inject.Inject

import play.api.libs.json.{JsValue, Json}
import services.{MetadataService, ValidValuesReadsAdditions}

import scala.xml.Node

/**
  * this trait is only needed for dependency injection of [[MetadataService]] into the companion object
  */
trait MdMetadataExtentTrait extends ValidValuesReadsAdditions {
  /* empty */
}

/**
  * Created by steffen on 21.12.16.
  */
case class MdMetadataExtent(val description: String,
                            val referenceSystem: String,
                            val mapExtentCoordinates: List[Float],
                            val temporalExtent: String) extends Jsonable with Xmlable {
  /**
    * creates JsValue from this class
    *
    * @return JsValue
    */
  override def toJson(): JsValue = ??? //Json.toJson(this)


  /**
    * creates JsValue from this class
    *
    * @return JsValue
    */
  override def toXml(): Node = ???
}

object MdMetadataExtent extends MdMetadataExtentTrait with JsonableCompanion[MdMetadataExtent] {
  /**
    * metadataService will be injected. for this to work you need to add
    * `bind(classOf[MdMetadataExtentTrait]).toInstance(MdMetadataExtent)`
    * to your implementation of `com.google.inject.AbstractModule.configure()`
    */
  @Inject() override var metadataService: MetadataService = null

  /**
    * parse object from Json
    *
    * @param json
    * @return Option if parsing error
    */
  override def fromJson(json: JsValue): Option[MdMetadataExtent] = ???
}
