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
  * container class for Research programme info
  *
  * @param titleName
  * @param abbrev
  * @param description
  * @param fundingSource
  * @param contactPersonName
  * @param leadOrganisationName
  * @param linkTo
  */
final case class ResearchPGHolder(titleName: String,
                                  abbrev: String,
                                  description: String,
                                  fundingSource: String,
                                  contactPersonName: String,
                                  leadOrganisationName: String,
                                  linkTo: String) extends ClassnameLogger {

  def toRdf: String = {
    s"""<skos:Concept rdf:about="http://vocab.smart-project.info/researchpg/term/$abbrev">
        <skos:label>$abbrev</skos:label>
        <dc:identifier>$abbrev</dc:identifier>
        <dc:title>$titleName</dc:title>
        <dc:type>$fundingSource</dc:type>
        <dc:relation>$linkTo</dc:relation>
        <dc:description>$description</dc:description>
        <dc:creator>$leadOrganisationName</dc:creator>
        <dc:contributor>$contactPersonName</dc:contributor>
        <skos:inCollection rdf:resource="http://vocab.smart-project.info/collection/researchpg/terms"/>
    </skos:Concept>
      """
  }

}

/**
  * companion object
  */
object ResearchPGHolder extends ClassnameLogger {

  /**
    * converter.rdfSkosDcHeader +
    *               ResearchPGHolder.toCollectionRdf(rdfResearchPGs, date) +
    *               rdfResearchPGs.map(pg => pg.toRdf).mkString("\n") +
    *               converter.rdfFooter
    *
    * @param skosCollection
    * @param date
    * @return
    */
  def toCompleteCollectionRdf(skosCollection: List[ResearchPGHolder],
                      date: String = ZonedDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)): String = {

    rdfSkosDcHeader +
      ResearchPGHolder.toRdfCollectionHeader(skosCollection, date) +
      skosCollection.map(pg => pg.toRdf).mkString("\n") +
      rdfFooter
  }

  def toRdfCollectionHeader(skosCollection: List[ResearchPGHolder],
                            date: String = ZonedDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)): String = {

    s"""<skos:Collection rdf:about="http://vocab.smart-project.info/collection/researchpg/terms">
        <rdfs:label>Research programmes</rdfs:label>
        <dc:title>Research programmes</dc:title>
        <dc:description>Research programmes</dc:description>
        <dc:creator>
            <foaf:Organization>
                <foaf:name>GNS Science</foaf:name>
            </foaf:Organization>
        </dc:creator>
        <dc:rights>CC-SA-BY-NC 3.0 NZ</dc:rights>
        <dcterms:issued>2017-11-17T20:55:00.215+13:00</dcterms:issued>
        <dcterms:modified>${date}</dcterms:modified>
        ${skosCollection.map(sc => s"<skos:member>http://vocab.smart-project.info/researchpg/term/${sc.abbrev}</skos:member>").mkString("\n")}
    </skos:Collection>"""
  }
}
