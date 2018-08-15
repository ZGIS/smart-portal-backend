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

import scala.util.matching.Regex

case class SimplifiedSkosRdfHolder(id: String,
                                   values: List[(String, String)]
                                  ) extends ClassnameLogger {

  val languageTagReg: Regex = """(\w+):(\w+)\@(\D\D)""".r

  def toRdf(ssrch: SimplifiedSkosRdfCollectionHolder, vocabUrl: String): String = {
    toRdf(ssrch.collectionIdentifier,
      ssrch.hierarchy,
      ssrch.hierarchyPlural,
      vocabUrl)
  }

  def toRdf(collectionIdentifier: String,
            hierarchy: String,
            hierarchyPlural: String,
            vocabUrl: String): String = {

    val coreElemsText = values.map { tup =>
      tup._1 match {
        case languageTagReg(prefix, term, lang) =>
          s"""<$prefix:$term xml:lang="$lang">${xml.Utility.escape(tup._2)}</$prefix:$term>
           """.stripMargin
        case _ =>
          s"""<${tup._1}>${xml.Utility.escape(tup._2)}</${tup._1}>
           """.stripMargin
      }
    }

    s"""<skos:Concept rdf:about="${vocabUrl}/$collectionIdentifier/$hierarchy/$id">
        ${coreElemsText.mkString("")}
        <skos:inCollection rdf:resource="http://vocab.smart-project.info/collection/$collectionIdentifier/$hierarchyPlural"/>
    </skos:Concept>
      """
  }
}

case class SimplifiedSkosRdfCollectionHolder(
                                              collectionIdentifier: String,
                                              hierarchy: String,
                                              hierarchyPlural: String,
                                              collectionLabel: String,
                                              collectionTitle: String,
                                              collectionDescription: String,
                                              issuedDate: ZonedDateTime,
                                              modifiedDate: ZonedDateTime,
                                              skosCollection: List[SimplifiedSkosRdfHolder]
                                            ) extends ClassnameLogger {

  def toCompleteCollectionRdf(vocabUrl: String): String = {

    rdfSkosDcHeader +
      toRdfCollectionHeader(vocabUrl) +
      skosCollection.map(sc => sc.toRdf(collectionIdentifier, hierarchy, hierarchyPlural, vocabUrl)).mkString("\n") +
      rdfFooter
  }

  def toRdfCollectionHeader(vocabUrl: String): String = {

    s"""<skos:Collection rdf:about="${vocabUrl}/collection/${collectionIdentifier}/${hierarchyPlural}">
        <rdfs:label>${collectionLabel}</rdfs:label>
        <dc:title>${collectionTitle}</dc:title>
        <dc:description>${collectionDescription}</dc:description>
        <dc:creator>
            <foaf:Organization>
                <foaf:name>GNS Science</foaf:name>
            </foaf:Organization>
        </dc:creator>
        <dc:rights>CC-SA-BY-NC 3.0 NZ</dc:rights>
        <dcterms:issued>${issuedDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}</dcterms:issued>
        <dcterms:modified>${modifiedDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}</dcterms:modified>
        ${skosCollection.map(sc => s"<skos:member>http://vocab.smart-project.info/${collectionIdentifier}/${hierarchy}/${sc.id}</skos:member>").mkString("\n")}
    </skos:Collection>"""
  }
}