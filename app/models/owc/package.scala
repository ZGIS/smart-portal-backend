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

package models

import java.net.URL
import java.time.{OffsetDateTime, ZoneOffset, ZonedDateTime}

import anorm.{Column, MetaDataItem, TypeDoesNotMatch}
import info.smart.models.owc100._

package object owc {

  val tableOwcContextHasOwcResources = "owc_context_has_owc_resources"
  val tableUserHasOwcContextRights = "user_has_owc_context_rights"

  val tableUsers = "users"

  val tableOwcStyleSets = "owc_stylesets"
  val tableOwcContents = "owc_contents"
  val tableOwcOperations = "owc_operations"
  val tableOwcOfferings = "owc_offerings"
  val tableOwcLinks = "owc_links"
  val tableOwcCreatorDisplays = "owc_creator_displays"
  val tableOwcCreatorApplications = "owc_creator_applications"
  val tableOwcCategories = "owc_categories"
  val tableOwcAuthors = "owc_authors"
  val tableOwcResources = "owc_resources"
  val tableOwcContexts = "owc_contexts"

  implicit val OwcAuthorEvidence = OwcAuthor(None, None, None)
  implicit val OwcCategoryEvidence = OwcCategory("term", None, None)
  implicit val OwcLinkEvidence = OwcLink(new URL(GENERIC_OWC_SPEC_URL), None, None, None, None, "rel")
  implicit val OwcContentEvidence = OwcContent("text/plain", None, None, None)
  implicit val OwcCreatorApplicationEvidence = OwcCreatorApplication(None, None, None)
  implicit val OwcCreatorDisplayEvidence = OwcCreatorDisplay(None, None, None)
  implicit val OwcStyleSetEvidence = OwcStyleSet("name", "title", None, None, None, None)

  implicit val OwcOperationEvidence = OwcOperation("GetCapabilties", "GET", None, new URL(GENERIC_OWC_SPEC_URL), None, None)
  implicit val OwcOfferingEvidence = OwcOffering(new URL(GENERIC_OWC_SPEC_URL + "/wms"), List(), List(), List())

  /**
    * Custom conversion from JDBC column to Boolean, might not be needed
    * Anorm should support Booleans for our databases
    *
    * @return
    */
  implicit def columnToBoolean: Column[Boolean] = {
    Column.nonNull { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case bool: Boolean => Right(bool) // Provided-default case
        case bit: Int => Right(bit == 1) // Custom conversion
        case _ => {
          Left(TypeDoesNotMatch(s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to Boolean for column $qualified"))
        }
      }
    }
  }

  /**
    *
    * @return
    */
  implicit def columnToOffset: Column[OffsetDateTime] = {
    Column.nonNull { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case offsetDateTime: OffsetDateTime => Right(offsetDateTime) // default case
        case zonedDateTime: ZonedDateTime => Right(OffsetDateTime.of(zonedDateTime.toLocalDateTime, zonedDateTime.getOffset)) // Custom conversion
        case h2DateTime: org.h2.api.TimestampWithTimeZone => {
          // val offsetMinutes = h2DateTime.getTimeZoneOffsetMins
          val localDateTime = h2DateTime.toLocalDateTime
          Right(OffsetDateTime.of(localDateTime, ZoneOffset.ofHoursMinutesSeconds(0,0,0)))
        } // Custom conversion, H2 time stamp with timezone is experimental
        case sqlTimeStamp: java.sql.Timestamp => {
          val localDateTime = sqlTimeStamp.toLocalDateTime
          Right(OffsetDateTime.of(localDateTime, ZoneOffset.ofHoursMinutesSeconds(0,0,0)))
        } // Custom conversion to omit H2 TimeStamp with TimeZone, for Testing don't use ZoneOffsets
        case _ => {
          Left(TypeDoesNotMatch(s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to OffsetDateTime for column $qualified"))
        }
      }
    }
  }
}
