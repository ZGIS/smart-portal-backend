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

import anorm.{Column, MetaDataItem, TypeDoesNotMatch}
import info.smart.models.owc100._

package object owc {

  //  implicit val tableOwcDocumentsHasOwcEntries = "owc_feature_types_as_document_has_owc_entries"
  //  implicit val tableOwcEntriesHasOwcOfferings = "owc_feature_types_as_entry_has_owc_offerings"
  //  implicit val tableOwcFeatureTypesHasOwcProperties = "owc_feature_types_has_owc_properties"
  //  implicit val tableOwcFeatureTypesHasOwcOfferings = "owc_feature_types_as_entry_has_owc_offerings"
  //  implicit val tableOwcFeatureTypesAsDocumentHasOwcEntries = "owc_feature_types_as_document_has_owc_entries"
  //  implicit val tableOwcFeatureTypesAsEntryHasOwcOfferings ="owc_feature_types_as_entry_has_owc_offerings"
  //  implicit val tableOwcPropertiesHasOwcAuthors = "owc_properties_has_owc_authors"
  //  implicit val tableOwcPropertiesHasOwcAuthorsAsContributors = "owc_properties_has_owc_authors_as_contributors"
  //  implicit val tableOwcPropertiesHasOwcCategories = "owc_properties_has_owc_categories"
  //  implicit val tableOwcPropertiesHasOwcLinks = "owc_properties_has_owc_links"
  //  implicit val tableOwcOfferingsHasOwcOperations = "owc_offerings_has_owc_operations"

  val tableOwcContextHasOwcResource = "owc_context_has_owc_resources"
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
  implicit val OwcStyleSetEvidence = OwcStyleSet("name", "title", None, None, None, None)

  implicit val OwcOperationEvidence = OwcOperation("GetCapabilties", "GET", None, new URL(GENERIC_OWC_SPEC_URL), None, None)
  implicit val OwcOfferingEvidence = OwcOffering(new URL(GENERIC_OWC_SPEC_URL + "/wms"), List(), List(), List())

  // Custom conversion from JDBC column to Boolean
  implicit def columnToBoolean: Column[Boolean] = {
    Column.nonNull1 { (value, meta) =>
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
}
