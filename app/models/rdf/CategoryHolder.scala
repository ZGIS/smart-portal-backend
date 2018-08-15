/*
 * Copyright (C) 2011-2017 Interfaculty Department of Geoinformatics, University of
 * Salzburg (Z_GIS) & Institute of Geological and Nuclear Sciences Limited (GNS Science)
 * in the SMART Aquifer Characterisation (SAC) programme funded by the New Zealand
 * Ministry of Business, Innovation and Employment (MBIE) and Department of Geography,
 * University of Tartu, Estonia (UT) under the ETAG Mobilitas Pluss grant No. MOBJD233.
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

package models.rdf

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import utils.ClassnameLogger



/**
  * Domain Science Categories container
  *
  * @param hierarchyNumber
  * @param id
  * @param parent
  * @param itemName
  * @param description
  * @param keywordContent
  * @param queryString
  * @param icon
  * @param bgIcon
  * @param categoryClass
  */
final case class CategoryHolder(hierarchyNumber: String = "",
                          id: Int,
                          parent: String = "main",
                          itemName: String = "",
                          description: String = "",
                          keywordContent: List[String] = List(),
                          queryString: String = "",
                          icon: String = "",
                          bgIcon: String = "",
                          categoryClass: String = "MainCategory") extends ClassnameLogger {
  def toRdf(vocabUrl: String): String = {
    val keywordString = if (keywordContent.nonEmpty) {
      keywordContent.mkString(", ")
    } else {
      ""
    }

    s"""<rdf:Description rdf:about="${vocabUrl}/categories.rdfs#$id"
       |    categories:id="$id"
       |    categories:hierarchy_number="$hierarchyNumber"
       |    categories:parent="$parent"
       |    categories:item_name="$itemName"
       |    categories:description="$description"
       |    categories:keyword_content="$keywordString"
       |    categories:query_string="$queryString"
       |    categories:icon="$icon"
       |    categories:bg_icon="$bgIcon">
       |  <rdf:type rdf:resource="${vocabUrl}/categories.rdfs#$categoryClass"/>
       |</rdf:Description>""".stripMargin
  }
}

/**
  * companion object, currently empty
  */
object CategoryHolder extends ClassnameLogger {

  def toCompleteRdf(skosCollection: List[CategoryHolder],
                    vocabUrl: String,
                    date: String = ZonedDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)): String = {
    val comment = s"""<!-- # Generated $date from Excel GW portal list of icons new structure PortalCategories.xlsx / Worksheet: science domain categories -->"""
    val rdfCategories = skosCollection.map(cat => cat.toRdf(vocabUrl))

    rdfHeader(vocabUrl) +
      "\n" +
      comment +
      "\n" +
      rdfClassdef(vocabUrl) +
      rdfCategories.mkString("\n") +
      rdfFooter
  }

}

