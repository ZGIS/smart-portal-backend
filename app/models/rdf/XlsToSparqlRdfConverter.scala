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

import org.apache.poi.ss.usermodel.Row.MissingCellPolicy
import org.apache.poi.ss.usermodel.{Cell, Sheet}
import utils.ClassnameLogger

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

      val lastAccessibleCell: Int = if (theRow.getLastCellNum - 1 <= theRow.getPhysicalNumberOfCells) {
        theRow.getLastCellNum - 1
      } else {
        theRow.getPhysicalNumberOfCells
      }
      logger.trace(s"getCellValueAsStringOption.lastCell: ${lastAccessibleCell + 1} : lastAccessibleCell ${lastAccessibleCell}")
      //      if (lastAccessibleCell >= cell) {
      try {
        theRow.getCell(cell, MissingCellPolicy.RETURN_NULL_AND_BLANK)
      } catch {
        case ex: NullPointerException =>
          logger.warn(s"NullPointerException row $row / $cell: ${ex.getMessage}")
        case ex: IllegalArgumentException =>
          logger.warn(s"IllegalArgumentException row $row / $cell: ${ex.getMessage}")
      }
      if (theRow.getCell(cell, MissingCellPolicy.RETURN_NULL_AND_BLANK) != null) {
        logger.trace(s"getCellValueAsStringOption => row: $row, cell: $cell, sheet: ${sheet.getSheetName}," +
          s"getCellType: ${sheet.getRow(row).getCell(cell).getCellType}")
        sheet.getRow(row).getCell(cell).getCellType match {
          case Cell.CELL_TYPE_NUMERIC => val value = Try {
            sheet.getRow(row).getCell(cell).getNumericCellValue.toString
          }.toOption
            logger.trace(s"CELL_TYPE_NUMERIC: $value")
            value
          case Cell.CELL_TYPE_STRING => val value = Try {
            val stringValue = sheet.getRow(row).getCell(cell).getStringCellValue
            if (stringValue == null) "" else new String(stringValue.getBytes("Windows-1251"), "UTF-8")
          }.toOption
            logger.trace(s"CELL_TYPE_STRING: $value")
            value
          case Cell.CELL_TYPE_BLANK => logger.warn(s"getCellValueAsStringOption cell type => row: $row, cell: $cell, sheet: ${sheet.getSheetName}," +
            "getCellType: CELL_TYPE_BLANK")
            None
          case Cell.CELL_TYPE_FORMULA => logger.warn(s"getCellValueAsStringOption cell type => row: $row, cell: $cell, sheet: ${sheet.getSheetName}," +
            "getCellType: CELL_TYPE_FORMULA")
            None
          case Cell.CELL_TYPE_ERROR => logger.warn(s"getCellValueAsStringOption cell type => row: $row, cell: $cell, sheet: ${sheet.getSheetName}," +
            "getCellType: CELL_TYPE_ERROR")
            None
          case Cell.CELL_TYPE_BOOLEAN => logger.warn(s"getCellValueAsStringOption cell type => row: $row, cell: $cell, sheet: ${sheet.getSheetName}," +
            "getCellType: CELL_TYPE_BOOLEAN")
            None
          case _ => logger.error(s"getCellValueAsStringOption cell type => row: $row, cell: $cell, sheet: ${sheet.getSheetName}," +
            "unexpected error get cell type")
            None
        }
      } else {
        logger.debug(s"void getCell(row: $row, cell: $cell) is null")
        None
      }
      //      } else {
      //        logger.warn(s"beyond cell range of row => row: $row, cell: $cell, sheet: ${sheet.getSheetName}," +
      //          s"getLastCellNum: ${sheet.getRow(row).getLastCellNum}")
      //        None
      //      }
    } else {
      logger.debug("reaching into the void, theRow is null")
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
      logger.debug(s"synonymsheet.lastRow: ${lastRow}")
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
    logger.info(s"domainSheet.lastRow: ${lastRow}")
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
            if (i <= 10 && query_string.isEmpty) {
              logger.warn(s"before cell $i categoryClass, after query_string $query_string")
            } else {
              logger.trace(s"before cell $i categoryClass, after query_string $query_string")
            }
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
    logger.info(s"last row research pg: $lastRow")
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
          case ex: Exception =>
            logger.warn(s"new ResearchPGHolder error on row $i: ${ex.getMessage}")
            logger.debug(s"new ResearchPGHolder error on row $i: ${ex.getMessage} ${ex.getStackTrace.mkString("\n")}")
        }
      }
    }
    logger.info(s"researchpgSheet.getLastRowNum: ${researchpgSheet.getLastRowNum}")
    val resultBuf = buf.toList
    logger.info(s"buildResearchPgFromSheet => results buf(${resultBuf.length}): ${resultBuf.map(kw => kw.abbrev).mkString("::")}")
    resultBuf
  }

  /**
    * reads in an XLSX Sheet with generic terms definition and produces a list of RDF/SKOS Vocab
    * * encapsulated in [[SimplifiedSkosRdfHolder]] to be dropped as RDF/XML eventually
    *
    * @param termsListSheet
    * @return
    */
  def parseSkosDcTermFromSheet(termsListSheet: org.apache.poi.ss.usermodel.Sheet): List[SimplifiedSkosRdfHolder] = {
    val lastRow = if (termsListSheet.getLastRowNum < termsListSheet.getPhysicalNumberOfRows) termsListSheet.getLastRowNum else termsListSheet.getPhysicalNumberOfRows
    val theRow = termsListSheet.getRow(0)

    val lastAccessibleCell: Int = if (theRow.getLastCellNum - 1 <= theRow.getPhysicalNumberOfCells) {
      theRow.getLastCellNum - 1
    } else {
      theRow.getPhysicalNumberOfCells
    }

    logger.info(s"last row skos rdf sheet: $lastRow")
    logger.info(s"last cell skos rdf sheet: $lastAccessibleCell")
    val buf = scala.collection.mutable.ListBuffer.empty[SimplifiedSkosRdfHolder]

    for (i <- 1 to lastRow) {
      val term_id_opt = getCellValueAsStringOption(i, 0, termsListSheet)
      if (term_id_opt.isEmpty) {
        logger.warn(s"term_id_opt row $i / cell 0: opt empty ?!?!")
      }
      term_id_opt.foreach {
        term_id =>
          val corrected_id = if (term_id.endsWith(".0")) term_id.replace(".0", "") else term_id
          // val tup_buf = scala.collection.mutable.ListBuffer.empty[(String, String)]

          val tup_list: List[(String, String)] = (1 to lastAccessibleCell).flatMap { j =>
            val headerTermElement_opt = getCellValueAsStringOption(0, j, termsListSheet)
            if (headerTermElement_opt.isEmpty) {
              logger.warn(s"headerTermElement_opt row $i / cell  $j: opt empty ?!?!")
              None
            }
            headerTermElement_opt.flatMap { hdr =>
              logger.debug(s"hdr row 0 / cell $j : $hdr")
              val termValue_opt = getCellValueAsStringOption(i, j, termsListSheet)
              if (termValue_opt.isEmpty) {
                logger.debug(s"tval row $i / cell $j: opt empty")
                None
              }
              termValue_opt.map { tval =>
                logger.debug(s"tval row $i / cell $j: $tval")
                Tuple2(hdr, tval)
              }
            }
          }.toList
          val ssrh = SimplifiedSkosRdfHolder(id = corrected_id, values = tup_list)
          logger.debug(tup_list.mkString("::"))
          logger.debug(ssrh.toRdf("gen", "term", "terms"))
          buf += ssrh
      }
    }
    logger.info(s"termsListSheet.getLastRowNum: ${termsListSheet.getLastRowNum}")
    logger.info(s"theRow.getLastCellNum: ${theRow.getLastCellNum}")
    val resultBuf = buf.toList
    logger.info(s"parseSkosDcTermFromSheet => results buf(${resultBuf.length}): ${resultBuf.map(kw => kw.id).mkString("::")}")
    resultBuf
  }

  /**
    * reads in an XLSX Sheet with generic terms definition and produces a list of RDF/SKOS Vocab
    * encapsulated in [[SimplifiedSkosRdfCollectionHolder]] to be dropped as RDF/XML eventually
    *
    * @param collectionInfoSheet
    * @param termsListSheet
    * @return
    */
  def buildGenericSkosCollectionHolderFromSheets(collectionInfoSheet: org.apache.poi.ss.usermodel.Sheet,
                                                 termsListSheet: org.apache.poi.ss.usermodel.Sheet): SimplifiedSkosRdfCollectionHolder = {

    val ssrchTry = scala.util.Try {

      val collectionIdentifier = getCellValueAsStringOption(1, 1, collectionInfoSheet).getOrElse("")
      val hierarchy = getCellValueAsStringOption(2, 1, collectionInfoSheet).getOrElse("")
      val hierarchyPlural = getCellValueAsStringOption(3, 1, collectionInfoSheet).getOrElse("")
      val collectionLabel = getCellValueAsStringOption(5, 1, collectionInfoSheet).getOrElse("")
      val collectionTitle = getCellValueAsStringOption(5, 1, collectionInfoSheet).getOrElse("")
      val collectionDescription = getCellValueAsStringOption(6, 1, collectionInfoSheet).getOrElse("")
      val modifiedDateText = getCellValueAsStringOption(7, 1, collectionInfoSheet).getOrElse("")
      val issuedDateText = getCellValueAsStringOption(8, 1, collectionInfoSheet).getOrElse("")

      val skosCollection = parseSkosDcTermFromSheet(termsListSheet)

      SimplifiedSkosRdfCollectionHolder(
        collectionIdentifier,
        hierarchy,
        hierarchyPlural,
        collectionLabel,
        collectionTitle,
        collectionDescription,
        issuedDate = ZonedDateTime.parse(modifiedDateText),
        modifiedDate = ZonedDateTime.parse(issuedDateText),
        skosCollection
      )
    }

    if (ssrchTry.isFailure) {
      val ex = ssrchTry.failed
      logger.error(s"new SimplifiedSkosRdfCollectionHolder error: ${ex.get.getMessage} ${ex.get.getStackTrace.mkString("\n")}")
      throw ex.get
    }

    ssrchTry.get
  }
}
