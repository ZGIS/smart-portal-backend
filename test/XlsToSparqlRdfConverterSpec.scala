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

import java.io.{File, FileInputStream}
import java.security.MessageDigest

import models.rdf._
import org.apache.commons.codec.binary.Hex
import org.apache.poi.ss.usermodel.WorkbookFactory

import scala.xml.NodeSeq

class XlsToSparqlRdfConverterSpec extends WithDefaultTest {

  private lazy val categoriesResource1 = this.getClass().getResource("sparql/PortalCategories.xlsx")

  private lazy val researchPgResource1 = this.getClass().getResource("sparql/ResearchPrgrm.xlsx")

  "Categories XLSX to RDFS Writer" should {

    val converter = new XlsToSparqlRdfConverter

    // val inp = new FileInputStream("workbook.xlsx");
    val inp = new FileInputStream(categoriesResource1.getPath)

    val workbook = WorkbookFactory.create(inp)
    val worksheet = workbook.getSheet("science domain categories")
    val synonyms_sheets = workbook.getSheet("synonyms")

    val rdfCategories = converter.buildCategoriesFromSheet(worksheet, synonyms_sheets)
    val fullRdfString: String = CategoryHolder.toCompleteRdf(rdfCategories)

    val categoriesRdfXmlGen = scala.xml.XML.loadString(fullRdfString)
    categoriesRdfXmlGen.isInstanceOf[NodeSeq] mustBe true
    println(rdfCategories(3).toRdf)
    // println(fullRdfString)

  }

  "Research PG XLSX to RDF/SKOS Writer" should {

    val converter = new XlsToSparqlRdfConverter

    val workbook = WorkbookFactory.create(new File(researchPgResource1.getPath))
    val worksheet = workbook.getSheet("Research programmes")

    val rdfResearchPGs = converter.buildResearchPgFromSheet(worksheet)

    val fullRdfString: String = ResearchPGHolder.toCompleteCollectionRdf(rdfResearchPGs)
    val researchPgRdfXmlGen = scala.xml.XML.loadString(fullRdfString)
    println(rdfResearchPGs.last.toRdf)
    researchPgRdfXmlGen.isInstanceOf[NodeSeq] mustBe true
    // println(fullRdfString)
  }

}
