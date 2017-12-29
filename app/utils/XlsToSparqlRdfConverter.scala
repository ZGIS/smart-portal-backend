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

package utils

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

import controllers.appTimeZone
import org.apache.poi.ss.usermodel.{Cell, Sheet}

import scala.util.Try
import scala.util.matching.Regex

/**
  * encapsulates functionality to load in Categories definition XLSX
  * in order to produce the SPARQL RDF/XML output for update in Vocab server Jena Fuseki
  */
class XlsToSparqlRdfConverter extends ClassnameLogger {

  // val regMatcher = "item_name\\s\\((?i:[\\d-\\w]+)\\)".r
  val regMatcher: Regex = "(?i:[\\d-\\w]+)".r

  /**
    * Maori macrons
    *
    * @param input
    * @param formCharset
    * @param toCharset
    * @return
    */
  def transformEncoding(input: String, formCharset: String, toCharset: String): String = {
    new String(input.getBytes(formCharset), toCharset)
  }

  /**
    * reading cell values from a sheet is not as straightforward apparently
    *
    * @param row
    * @param cell
    * @param sheet
    * @return
    */
  def getCellValueAsStringOption(row: Int, cell: Int, sheet: Sheet): Option[String] = {
    val theRow = sheet.getRow(row)
    if (theRow != null) {

      val lastCell = if (theRow.getLastCellNum < theRow.getPhysicalNumberOfCells) {
        theRow.getLastCellNum
      } else {
        theRow.getPhysicalNumberOfCells
      }
      logger.trace(s"getCellValueAsStringOption.lastCell: ${lastCell}")
      if (lastCell > cell) {
        logger.trace(s"getCellValueAsStringOption => row: $row, cell: $cell, sheet: ${sheet.getSheetName}," +
          s"getCellType: ${sheet.getRow(row).getCell(cell).getCellType}")
        sheet.getRow(row).getCell(cell).getCellType match {
          case Cell.CELL_TYPE_NUMERIC => val value = Try {
            sheet.getRow(row).getCell(cell).getNumericCellValue.toString
          }.toOption
            logger.trace(s"CELL_TYPE_NUMERIC: $value")
            value
          case Cell.CELL_TYPE_STRING => val value = Try {
            val stringValue = sheet.getRow(row).getCell(cell).getRichStringCellValue.getString
            if (stringValue == null) "" else stringValue
          }.toOption
            logger.trace(s"CELL_TYPE_STRING: $value")
            value
          case Cell.CELL_TYPE_BLANK => logger.debug(s"getCellValueAsStringOption cell type => row: $row, cell: $cell, sheet: ${sheet.getSheetName}," +
            "getCellType: CELL_TYPE_BLANK")
            None
          case _ => logger.error(s"getCellValueAsStringOption cell type => row: $row, cell: $cell, sheet: ${sheet.getSheetName}," +
            "unexpected error get cell type")
            None
        }
      } else {
        logger.debug(s"beyond cell range of row => row: $row, cell: $cell, sheet: ${sheet.getSheetName}," +
          s"getLastCellNum: ${sheet.getRow(row).getLastCellNum}")
        None
      }
    } else {
      logger.debug("reaching into the void")
      None
    }
  }

  /**
    * finds synonym ffor a list of main keywords of a category, uses lookup sheet
    *
    * @param keywords_list
    * @param synonymSheet
    * @return
    */
  def findSynonymsForKeywords(keywords_list: List[String], synonymSheet: org.apache.poi.ss.usermodel.Sheet): List[String] = {
    logger.trace(s"findSynonymsForKeywords => keywords_list: ${keywords_list.mkString("::")}, synonymSheet: ${synonymSheet.getSheetName}")
    val buf = scala.collection.mutable.ArrayBuffer.empty[String]
    keywords_list.foreach { keyword =>
      logger.trace(s"findSynonymsForKeywords.foreach keyword: $keyword")
      val lastRow = if (synonymSheet.getLastRowNum < synonymSheet.getPhysicalNumberOfRows) synonymSheet.getLastRowNum else synonymSheet.getPhysicalNumberOfRows
      logger.warn(s"synonymsheet.lastRow: ${lastRow}")
      for (i <- 1 to lastRow) {
        if (synonymSheet.getRow(i).getLastCellNum > 1) {
          logger.trace(s"findSynonymsForKeywords.foreach synonymSheet.getLastRowNum: ${synonymSheet.getLastRowNum}")
          if (getCellValueAsStringOption(i, 0, synonymSheet).isDefined) {
            logger.trace(s"findSynonymsForKeywords.foreach getCellValueAsString($i, 0, ${synonymSheet.getLastRowNum}): " +
              s"${getCellValueAsStringOption(i, 0, synonymSheet)}")
            if (getCellValueAsStringOption(i, 0, synonymSheet).get.equalsIgnoreCase(keyword)) {
              getCellValueAsStringOption(i, 1, synonymSheet).get.split(",").map(kw => kw.trim.toLowerCase).foreach(lkw => buf += lkw)
            }
          }
        } else {
          logger.debug(s"beyond cell range of row => row: $i, cell: 1, sheet: ${synonymSheet.getSheetName}," +
            s"getLastCellNum: ${synonymSheet.getRow(i).getLastCellNum}")
        }
      }
    }
    val resultBuf = buf.toList
    logger.debug(s"findSynonymsForKeywords => results buf: ${resultBuf.mkString("::")}")
    resultBuf
  }

  /**
    * reads in an XLSX Sheet with categories definition and a keywords lookup sheet and produces a list of Categories
    * encapsulated in [[CategoryHolder]] to be dropped as RDF/XML eventually
    *
    * @param domainSheet
    * @param synonymSheet
    * @return
    */
  def buildCategoriesFromSheet(domainSheet: org.apache.poi.ss.usermodel.Sheet,
                               synonymSheet: org.apache.poi.ss.usermodel.Sheet): List[CategoryHolder] = {
    val buf = scala.collection.mutable.ListBuffer.empty[CategoryHolder]
    val lastParent = scala.collection.mutable.ListBuffer.empty[String]
    val parentDefault = "main"
    lastParent += parentDefault
    val lastRow = if (domainSheet.getLastRowNum < domainSheet.getPhysicalNumberOfRows) domainSheet.getLastRowNum else domainSheet.getPhysicalNumberOfRows
    logger.warn(s"domainSheet.lastRow: ${lastRow}")
    for (i <- 0 to lastRow) {
      val cellValue = getCellValueAsStringOption(i, 0, domainSheet)
      logger.trace(s"domainSheet.getRow($i).getCell(0).getCell: $cellValue")
      if (cellValue.isDefined) {
        val hierarchy_number = getCellValueAsStringOption(i, 0, domainSheet).get
        val item_name = getCellValueAsStringOption(i, 1, domainSheet).getOrElse("")
        logger.trace("row(+1) " + i + " hierarchy_number " + hierarchy_number + " item_name " + item_name)
        if (hierarchy_number.equalsIgnoreCase("hierarchy_number")) {
          if (!item_name.equalsIgnoreCase("item_name")) {
            if (regMatcher.findFirstIn(item_name).isDefined) {
              // val plist = regMatcher.findAllIn(item_name)
              // lastParent += plist.toSeq.mkString("")
              val plist = regMatcher.findAllIn(item_name).toList
              logger.trace(s"regMatcher.findAllIn($item_name): ${plist.mkString("::")}")
              lastParent += plist(1)
            }
          }
        } else {
          val lastParentValue = lastParent.last.trim
          logger.trace(s"lastParentValue: ${lastParentValue}")
          try {
            logger.trace(s"before cell $i 4, after lastParentValue $lastParentValue")
            val description = getCellValueAsStringOption(i, 4, domainSheet).getOrElse("")
            logger.trace(s"before cell $i 5, after description $description")
            val trimmed = getCellValueAsStringOption(i, 5, domainSheet).getOrElse("").split(",").map(_.trim)
            val keywords_low = trimmed.map(_.toLowerCase).toList
            val synonyms_low = findSynonymsForKeywords(keywords_low, synonymSheet)
            val keyword_content = keywords_low ++ synonyms_low
            logger.trace(s"before cell $i 6, after keyword_content ${keyword_content.mkString("::")}")
            val icon = getCellValueAsStringOption(i, 6, domainSheet).getOrElse("")
            logger.trace(s"before cell $i 7, after icon $icon")
            val bg_icon = getCellValueAsStringOption(i, 7, domainSheet).getOrElse("")
            logger.trace(s"before cell $i 8, after bg_icon $bg_icon")
            val query_string = getCellValueAsStringOption(i, 8, domainSheet).getOrElse("")
            logger.trace(s"before cell $i categoryClass, after query_string $query_string")
            val categoryClass = if (lastParentValue.equalsIgnoreCase("main")) {
              "MainCategory"
            } else {
              "ChildCategory"
            }
            logger.trace(s"before cat number $i")
            val cat = CategoryHolder(
              hierarchyNumber = hierarchy_number,
              id = i,
              parent = lastParentValue,
              itemName = item_name,
              description = description,
              keywordContent = keyword_content,
              queryString = query_string,
              icon = icon,
              bgIcon = bg_icon,
              categoryClass = categoryClass)
            logger.trace(s"after cat number $i")
            logger.debug(cat.toRdf)
            buf += cat

          } catch {
            case ex: Exception => logger.error(s"new CategoryHolder error on row $i: ${ex.getMessage} ${ex.getStackTrace.mkString("\n")}")
          }
        }
      }
    }
    logger.info(s"domainSheet.lastRow: ${lastRow}")
    val resultBuf = buf.toList
    logger.info(s"buildCategoriesFromSheet => results buf(${resultBuf.length}): ${resultBuf.map(kw => kw.hierarchyNumber).mkString("::")}")
    resultBuf
  }

  /**
    * reads in an XLSX Sheet with research programme definition and produces a list of RDF/SKOS Vocab
    * encapsulated in [[ResearchPGHolder]] to be dropped as RDF/XML eventually
    *
    * @param researchpgSheet
    * @return
    */
  def buildResearchPgFromSheet(researchpgSheet: org.apache.poi.ss.usermodel.Sheet): List[ResearchPGHolder] = {
    val lastRow = if (researchpgSheet.getLastRowNum < researchpgSheet.getPhysicalNumberOfRows) researchpgSheet.getLastRowNum else researchpgSheet.getPhysicalNumberOfRows
    logger.warn(s"last row research pg: $lastRow")
    val buf = scala.collection.mutable.ListBuffer.empty[ResearchPGHolder]
    for (i <- 1 to lastRow) {
      val cellValue = getCellValueAsStringOption(i, 0, researchpgSheet)
      if (cellValue.isDefined) {
        try {

          val titleName = getCellValueAsStringOption(i, 0, researchpgSheet).getOrElse("")
          val abbrev = getCellValueAsStringOption(i, 1, researchpgSheet).getOrElse("")
          val description = getCellValueAsStringOption(i, 2, researchpgSheet).getOrElse("")
          val fundingSource = getCellValueAsStringOption(i, 3, researchpgSheet).getOrElse("")
          val contactPersonName = getCellValueAsStringOption(i, 4, researchpgSheet).getOrElse("")
          val leadOrganisationName = getCellValueAsStringOption(i, 5, researchpgSheet).getOrElse("")
          val linkTo = getCellValueAsStringOption(i, 7, researchpgSheet).getOrElse("")

          val pg = ResearchPGHolder(
            titleName,
            abbrev,
            description,
            fundingSource,
            contactPersonName,
            leadOrganisationName,
            linkTo)
          logger.debug(pg.toRdf)
          buf += pg

        } catch {
          case ex: Exception => logger.error(s"new ResearchPGHolder error on row $i: ${ex.getMessage} ${ex.getStackTrace.mkString("\n")}")
        }
      }
    }
    logger.info(s"researchpgSheet.getLastRowNum: ${researchpgSheet.getLastRowNum}")
    val resultBuf = buf.toList
    logger.info(s"buildResearchPgFromSheet => results buf(${resultBuf.length}): ${resultBuf.map(kw => kw.abbrev).mkString("::")}")
    resultBuf
  }

  val rdfHeader =
    """<?xml version="1.0" encoding="UTF-8"?>
<rdf:RDF xmlns:categories="http://vocab.smart-project.info/categories#"
         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
         xmlns:dc="http://purl.org/dc/elements/1.1/"
         xmlns:xs="http://www.w3.org/2001/XMLSchema">
         """

  val rdfSkosDcHeader =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:dc="http://purl.org/dc/elements/1.1/"
      |         xmlns:dcterms="http://purl.org/dc/terms/" xmlns:foaf="http://xmlns.com/foaf/0.1/"
      |         xmlns:gml="http://www.opengis.net/gml"
      |         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#" xmlns:skos="http://www.w3.org/2004/02/skos/core#"
      |         xmlns:xs="http://www.w3.org/2001/XMLSchema">
      |""".stripMargin

  val rdfClassdef =
    """    <rdf:Description rdf:ID="SacCategory">
        <rdf:type rdf:resource="http://www.w3.org/2000/01/rdf-schema#Class"/>
        <rdfs:label xml:lang="en">category</rdfs:label>
        <rdfs:comment xml:lang="en">the basic category template</rdfs:comment>
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
    </rdf:Description>

    <rdfs:Class rdf:ID="MainCategory">
        <rdfs:subClassOf rdf:resource="#SacCategory"/>
        <rdfs:label xml:lang="en">main category</rdfs:label>
        <rdfs:comment xml:lang="en">a main category</rdfs:comment>
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
    </rdfs:Class>

    <rdfs:Class rdf:ID="ChildCategory">
        <rdfs:subClassOf rdf:resource="#MainCategory"/>
        <rdfs:label xml:lang="en">child category</rdfs:label>
        <rdfs:comment xml:lang="en">a child category</rdfs:comment>
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
    </rdfs:Class>

    <rdf:Property rdf:about="id" rdfs:label="id" rdfs:comment="id">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="hierarchy_number" rdfs:label="hierarchy_number" rdfs:comment="hierarchy_number">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="parent" rdfs:label="parent" rdfs:comment="parent">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="item_name" rdfs:label="item_name" rdfs:comment="item_name">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="description" rdfs:label="description" rdfs:comment="description">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="query_string" rdfs:label="query_string" rdfs:comment="query_string">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="query_string" rdfs:label="query_string" rdfs:comment="keyword_content">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="icon" rdfs:label="icon" rdfs:comment="icon">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="bg_icon" rdfs:label="bg_icon" rdfs:comment="bg_icon">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <!-- need to be a listable property, so that a class has several  -->
    <rdf:Property rdf:about="keyword_content" rdfs:label="keyword_content" rdfs:comment="keyword_content">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>
    """

  val rdfFooter =
    """
</rdf:RDF>
"""


}

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
case class CategoryHolder(hierarchyNumber: String = "",
                          id: Int,
                          parent: String = "main",
                          itemName: String = "",
                          description: String = "",
                          keywordContent: List[String] = List(),
                          queryString: String = "",
                          icon: String = "",
                          bgIcon: String = "",
                          categoryClass: String = "MainCategory") extends ClassnameLogger {
  def toRdf: String = {
    val keywordString = if (keywordContent.nonEmpty) {
      keywordContent.mkString(", ")
    } else {
      ""
    }

    s"""<rdf:Description rdf:about="http://vocab.smart-project.info/categories.rdfs#$id"
       |    categories:id="$id"
       |    categories:hierarchy_number="$hierarchyNumber"
       |    categories:parent="$parent"
       |    categories:item_name="$itemName"
       |    categories:description="$description"
       |    categories:keyword_content="$keywordString"
       |    categories:query_string="$queryString"
       |    categories:icon="$icon"
       |    categories:bg_icon="$bgIcon">
       |  <rdf:type rdf:resource="http://vocab.smart-project.info/categories.rdfs#$categoryClass"/>
       |</rdf:Description>""".stripMargin
  }
}

/**
  * companion object, currently empty
  */
object CategoryHolder extends ClassnameLogger {

}

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
case class ResearchPGHolder(titleName: String,
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

  def toCollectionRdf(skosCollection: List[ResearchPGHolder]): String = {

    val now = ZonedDateTime.now.withZoneSameInstant(ZoneId.of(appTimeZone))
    val date = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

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