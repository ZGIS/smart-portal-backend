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

package models

package object owc {

  implicit val tableOwcDocumentsHasOwcEntries = "owc_feature_types_as_document_has_owc_entries"
  implicit val tableOwcEntriesHasOwcOfferings = "owc_feature_types_as_entry_has_owc_offerings"
  implicit val tableOwcFeatureTypesHasOwcProperties = "owc_feature_types_has_owc_properties"
  implicit val tableOwcPropertiesHasOwcAuthors = "owc_properties_has_owc_authors"
  implicit val tableOwcPropertiesHasOwcAuthorsAsContributors = "owc_properties_has_owc_authors_as_contributors"
  implicit val tableOwcPropertiesHasOwcCategories = "owc_properties_has_owc_categories"
  implicit val tableOwcPropertiesHasOwcLinks = "owc_properties_has_owc_links"
  implicit val tableOwcOfferingsHasOwcOperations = "owc_offerings_has_owc_operations"

  implicit val tableOwcOperations = "owc_operations"
  implicit val tableOwcOfferings = "owc_offerings"
  implicit val tableOwcProperties = "owc_properties"
  implicit val tableOwcLinks = "owc_links"
  implicit val tableOwcCategories = "owc_categories"
  implicit val tableOwcAuthors = "owc_authors"
  implicit val tableOwcFeatureTypes = "owc_feature_types"

}
