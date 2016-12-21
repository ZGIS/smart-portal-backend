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

package models.metadata

import java.util

import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json._


/**
  * This Class saves valid values for Metadata Editor Comboboxes
  *
  * @param standardValue index of standard value
  * @param values        list of values
  * @param descriptions  optional list of descriptions
  */

// TODO SR For more complicated things (like valid BBOXes or so) value needs to become an
// object See https://github.com/angular/angular/issues/4843#issuecomment-206583992
class ValidValues(
                   val standardValue: Int,
                   val values: List[String],
                   val descriptions: Option[List[String]]
                 ) {
  require(values.nonEmpty, "values list must not be empty")
  require(descriptions.isEmpty || descriptions.get.length == values.length,
    "decriptions list must either be None or same length as values list")
  require(standardValue >= 0 && standardValue < values.length, "standardValue must be within values list length")
  require(values.distinct.size == values.size, "all values must be unique")
  require(descriptions.isEmpty || (descriptions.get.distinct.size == descriptions.get.size),
    "all descriptions must be unique")

}

/**
  * Companion object for [[ValidValues]]
  */
object ValidValues {

  import scala.collection.JavaConversions._

  /**
    * creates a new [[ValidValues]] instance
    *
    * @param standardValue
    * @param values
    * @param descriptions
    * @return
    */
  def apply(standardValue: Int,
            values: List[String],
            descriptions: Option[List[String]]): ValidValues = {
    new ValidValues(standardValue, values, descriptions)
  }

  /**
    *
    * @param arg
    * @return
    */
  def unapply(arg: ValidValues): Option[(Int, List[String], Option[List[String]])] = {
    Some((arg.standardValue, arg.values, arg.descriptions))
  }


  /**
    * Json writer [[Writes]] for [[ValidValues]].
    */
  implicit val validValuesWriter: Writes[ValidValues] = (
    (JsPath \ "standardValue").write[Int] and
      (JsPath \ "values").write[List[String]] and
      (JsPath \ "descriptions").writeNullable[List[String]]
    ) (unlift(ValidValues.unapply))

  /**
    * Json reader [[Reads]] for [[ValidValues]]
    */
  implicit val validValuesReader: Reads[ValidValues] = (
    (JsPath \ "standardValue").read[Int] and
      (JsPath \ "values").read[List[String]] and
      (JsPath \ "descriptions").readNullable[List[String]]
    ) (ValidValues.apply _)


  /**
    * creates a new [[ValidValues]] from [[Configuration]]
    *
    * @param conf
    * @return
    */
  def parseConfiguration(conf: Configuration): ValidValues = {

    val standardValue = conf.getInt("standardValue").getOrElse(-1)
    val values = conf.getStringList("values").getOrElse(new util.ArrayList[String]()).toList

    val descriptions = conf.getStringList("descriptions").map(_.toList)

    ValidValues(standardValue, values, descriptions)
  }
}
