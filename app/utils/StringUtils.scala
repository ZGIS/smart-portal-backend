/*
 * Copyright (c) 2011-2017 Interfaculty Department of Geoinformatics, University of Salzburg (Z_GIS)
 * & Institute of Geological and Nuclear Sciences Limited (GNS Science)
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
 *
 *
 */

package utils

import java.util.UUID

import scala.util.{Failure, Success, Try}

/**
  * Adds some convenience functions to Strings
  */
object StringUtils extends ClassnameLogger {

  /**
    * Converts String to Option
    * @param s
    */
  implicit class OptionUuidConverters(val s: String) {

    /**
      * Converts String to Option[UUID]. None when trimmed String is empty or UUID fails.
      *
      * @return Given UUID encapsulated in Option or None if empty after trimming
      */
    def toUuidOption: Option[UUID] = {
      if (s.trim.isEmpty) {
        None
      }
      else {
        Try {
          UUID.fromString(s)
        } match {
          case Success(uuid) => Some(uuid)
          case Failure(ex) => {
            logger.error("Couldn't convert String to UUID", ex)
            None
          }
        }
      }
    }
  }

  /**
    * Converts String to Option
    * @param s
    */
  implicit class OptionConverters(val s: String) {

    /**
      * Converts String to Option. None when trimmed String is empty.
      *
      * @return Given String encapsulated in Option or None if empty after trimming
      */
    def toOption(): Option[String] = {
      if (s.trim.isEmpty) {
        None
      }
      else {
        Some(s)
      }
    }
  }
}
