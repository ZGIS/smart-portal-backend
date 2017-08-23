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

package services

import java.util
import javax.inject.Inject

import models.metadata.ValidValues
import play.api.Configuration
import play.api.data.validation.ValidationError
import play.api.libs.json.Reads
import utils.ClassnameLogger

/**
  * provides an addition to [[Reads]] for checking valid values against [[MetadataService]]
  */
trait ValidValuesReadsAdditions extends ClassnameLogger {
  var metadataService: MetadataService

  //TODO SR this is not really nice. Especially the error message should contain the value.
  def validValue(topic: String)(implicit reads: Reads[String]): Reads[String] =
    Reads.filter[String](ValidationError(s"not a valid value for ${topic}"))(value => {
      logger.debug(s"looking for '${value}' in ${topic}")
      metadataService.getValidValuesFor(topic).get.values.contains(value)
    })(reads)
}

/**
  * provides access to ValidValues configuration. You need to have `smart.metadata.validValues` configured in
  * your application configuration.
  * @param configuration
  */
class MetadataService @Inject()(configuration: Configuration) extends ClassnameLogger {

  lazy val metadataValidValues: Option[util.List[Configuration]] =
    configuration.getConfigList("smart.metadata.validValues")

  /**
    * returns an Option of ValidValues for a given topic. If topic os not found,
    * it returns None.
    * @param topic
    * @return
    */
  def getValidValuesFor(topic: String): Option[ValidValues] = {
    import scala.collection.JavaConversions._

    logger.debug(s"returning valid values for '${topic}'")
    val configurations = metadataValidValues.get
    val validValuesConf = configurations.filter(conf => conf.getString("for").getOrElse("").equals(topic))
    if (validValuesConf.isEmpty()) {
      logger.warn(s"Could not find ValidValues for ${topic}")
      None
    }
    else {
      val conf = validValuesConf.head
      logger.debug(s"Found: ${validValuesConf.head}")

      Some(ValidValues.parseConfiguration(validValuesConf.head))
    }
  }
}
