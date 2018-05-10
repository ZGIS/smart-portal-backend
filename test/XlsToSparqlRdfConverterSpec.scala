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

import models.rdf._
import org.apache.poi.ss.usermodel.WorkbookFactory

import scala.xml.NodeSeq

class XlsToSparqlRdfConverterSpec extends WithDefaultTest {

  private lazy val categoriesResource1 = this.getClass().getResource("sparql/PortalCategories.xlsx")

  private lazy val researchPgResource1 = this.getClass().getResource("sparql/ResearchPrgrm.xlsx")

  private lazy val skosNgmpResource1 = this.getClass().getResource("sparql/NgmpParams.xlsx")
  private lazy val skosGlossaryResource1 = this.getClass().getResource("sparql/FreshwaterGlossary.xlsx")
  private lazy val skosAwahouResource1 = this.getClass().getResource("sparql/AwahouGlossary.xlsx")
  private lazy val skosPapawaiResource1 = this.getClass().getResource("sparql/MaoriPapawaiLexicon.xlsx")


  "Categories XLSX to RDFS Writers" should {

    "succeed on building" in {

      val converter = new XlsToSparqlRdfConverter

      // val inp = new FileInputStream("workbook.xlsx");
      val inp = new FileInputStream(categoriesResource1.getPath)

      val workbook = WorkbookFactory.create(inp)
      val worksheet = workbook.getSheet("science domain categories")
      val synonyms_sheets = workbook.getSheet("synonyms")

      val rdfCategories = converter.buildCategoriesFromSheet(worksheet, synonyms_sheets)
      val fullRdfString: String = CategoryHolder.toCompleteRdf(rdfCategories)

//      import java.nio.file.{Paths, Files}
//      import java.nio.charset.StandardCharsets
//
//      Files.write(Paths.get("categories.xml"), fullRdfString.getBytes(StandardCharsets.UTF_8))

      val categoriesRdfXmlGen = scala.xml.XML.loadString(fullRdfString)
      categoriesRdfXmlGen.isInstanceOf[NodeSeq] mustBe true
      logger.warn(rdfCategories(3).toRdf)
    }
  }

  "Research PG XLSX to RDF/SKOS Writer" should {

    "succeed on building" in {

      val converter = new XlsToSparqlRdfConverter

      val workbook = WorkbookFactory.create(new File(researchPgResource1.getPath))
      val worksheet = workbook.getSheet("Research programmes")

      val rdfResearchPGs = converter.buildResearchPgFromSheet(worksheet)

      val fullRdfString: String = ResearchPGHolder.toCompleteCollectionRdf(rdfResearchPGs)
//      import java.nio.file.{Paths, Files}
//      import java.nio.charset.StandardCharsets
//
//      Files.write(Paths.get("researchpg.xml"), fullRdfString.getBytes(StandardCharsets.UTF_8))

      val researchPgRdfXmlGen = scala.xml.XML.loadString(fullRdfString)
      logger.warn(rdfResearchPGs.last.toRdf)
      researchPgRdfXmlGen.isInstanceOf[NodeSeq] mustBe true

    }
  }

  "Generic SKOS DC XLSX to RDF/SKOS Writer for NGMP" should {

    "succeed on building" in {

      logger.warn("Generic SKOS DC XLSX to RDF/SKOS Writer for NGMP")
      val converter = new XlsToSparqlRdfConverter

      val workbook = WorkbookFactory.create(new File(skosNgmpResource1.getPath))
      val collectionInfoWorksheet = workbook.getSheet("CollectionInfo")
      val termsWorksheet = workbook.getSheet("Terms")

      val skosCollectionHolder: SimplifiedSkosRdfCollectionHolder = converter
        .buildGenericSkosCollectionHolderFromSheets(
          collectionInfoWorksheet, termsWorksheet)

      val fullRdfString: String = skosCollectionHolder.toCompleteCollectionRdf
      logger.warn(skosCollectionHolder.skosCollection.last.toRdf(skosCollectionHolder))
//      import java.nio.file.{Paths, Files}
//      import java.nio.charset.StandardCharsets
//
//      Files.write(Paths.get("ngmp.xml"), fullRdfString.getBytes(StandardCharsets.UTF_8))

      val researchPgRdfXmlGen = scala.xml.XML.loadString(fullRdfString)
      researchPgRdfXmlGen.isInstanceOf[NodeSeq] mustBe true
    }
  }

  "Generic SKOS DC XLSX to RDF/SKOS Writer for Glossary" should {

    "succeed on building" in {

      val converter = new XlsToSparqlRdfConverter

      val workbook = WorkbookFactory.create(new File(skosGlossaryResource1.getPath))
      val collectionInfoWorksheet = workbook.getSheet("CollectionInfo")
      val termsWorksheet = workbook.getSheet("Terms")

      val skosCollectionHolder: SimplifiedSkosRdfCollectionHolder = converter
        .buildGenericSkosCollectionHolderFromSheets(
          collectionInfoWorksheet, termsWorksheet)

      val fullRdfString: String = skosCollectionHolder.toCompleteCollectionRdf
      logger.warn(skosCollectionHolder.skosCollection.last.toRdf(skosCollectionHolder))
//      import java.nio.file.{Paths, Files}
//      import java.nio.charset.StandardCharsets
//
//      Files.write(Paths.get("glossary.xml"), fullRdfString.getBytes(StandardCharsets.UTF_8))

      val researchPgRdfXmlGen = scala.xml.XML.loadString(fullRdfString)
      researchPgRdfXmlGen.isInstanceOf[NodeSeq] mustBe true
    }
  }

  "Generic SKOS DC XLSX to RDF/SKOS Writer for Awahou" should {

    "succeed on building" in {
      val converter = new XlsToSparqlRdfConverter

      val workbook = WorkbookFactory.create(new File(skosAwahouResource1.getPath))
      val collectionInfoWorksheet = workbook.getSheet("CollectionInfo")
      val termsWorksheet = workbook.getSheet("Terms")

      val skosCollectionHolder: SimplifiedSkosRdfCollectionHolder = converter
        .buildGenericSkosCollectionHolderFromSheets(
          collectionInfoWorksheet, termsWorksheet)

      val fullRdfString: String = skosCollectionHolder.toCompleteCollectionRdf
      logger.warn(skosCollectionHolder.skosCollection.last.toRdf(skosCollectionHolder))
//      import java.nio.file.{Paths, Files}
//      import java.nio.charset.StandardCharsets
//
//      Files.write(Paths.get("awahou.xml"), fullRdfString.getBytes(StandardCharsets.UTF_8))

      val researchPgRdfXmlGen = scala.xml.XML.loadString(fullRdfString)
      researchPgRdfXmlGen.isInstanceOf[NodeSeq] mustBe true
    }
  }

  "Generic SKOS DC XLSX to RDF/SKOS Writer for Papawai" should {

    "succeed on building" in {

      val converter = new XlsToSparqlRdfConverter

      val workbook = WorkbookFactory.create(new File(skosPapawaiResource1.getPath))
      val collectionInfoWorksheet = workbook.getSheet("CollectionInfo")
      val termsWorksheet = workbook.getSheet("Terms")

      val skosCollectionHolder: SimplifiedSkosRdfCollectionHolder = converter
        .buildGenericSkosCollectionHolderFromSheets(
          collectionInfoWorksheet, termsWorksheet)

      val fullRdfString: String = skosCollectionHolder.toCompleteCollectionRdf
      logger.warn(skosCollectionHolder.skosCollection.last.toRdf(skosCollectionHolder))
//      import java.nio.file.{Paths, Files}
//      import java.nio.charset.StandardCharsets
//
//      Files.write(Paths.get("papawai.xml"), fullRdfString.getBytes(StandardCharsets.UTF_8))

      val researchPgRdfXmlGen = scala.xml.XML.loadString(fullRdfString)
      researchPgRdfXmlGen.isInstanceOf[NodeSeq] mustBe true
    }
  }

}
