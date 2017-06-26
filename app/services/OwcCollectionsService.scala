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

package services

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID
import javax.inject._

import models.gmd.MdMetadata
import models.owc._
import models.users._
import utils.ClassnameLogger

import info.smart.models.owc._

@Singleton
class OwcCollectionsService @Inject()(userDAO: UserDAO,
                                      owcPropertiesDAO: OwcPropertiesDAO,
                                      owcOfferingDAO: OwcOfferingDAO,
                                      owcContextDAO: OwcContextDAO) extends ClassnameLogger {

  /**
    * get user's default collection
    * @param email
    * @return
    */
  def getUserDefaultOwcDocument(email: String) : Option[OwcDocument] = {
    owcContextDAO.findUserDefaultOwcDocument(email)
  }

  /**
    * get user's own files
    * @param email
    * @return
    */
  def getOwcPropertiesForOwcAuthorOwnFiles(email: String): Seq[UploadedFileProperties] = {
    owcContextDAO.findOwcPropertiesForOwcAuthorOwnFiles(email)
  }

  /**
    * get Owc Documents For optional email And owc doc Id
    *
    * @param authUserOption
    * @param idOption
    * @return
    */
  def getOwcDocumentsForUserAndId(authUserOption: Option[String], idOption: Option[String]): Seq[OwcDocument] = {

    authUserOption.fold {
      // no email provided
      idOption.fold {
        // TODO docs for anonymous, no id provided => all public docs (implies docs must be public)
        owcContextDAO.getAllPublicOwcDocuments
      } {
        // TODO find doc by id for anonymous, only one doc if available (implies doc must be public)
        id => {
          owcContextDAO.findPublicOwcDocumentsById(id).toSeq
        }
      }
    } { authUser => {
      // trying to find a user from provided authuser option
      userDAO.findUserByEmail(authUser).fold {
        logger.warn("Provided user not found.")
        idOption.fold {
          // TODO docs for anonymous, no id provided => all public docs (later maybe check if public)
          owcContextDAO.getAllPublicOwcDocuments
        } {
          // docs for anonymous, but id provided only one doc if available (and only if public)
          id => {
            owcContextDAO.findPublicOwcDocumentsById(id).toSeq
          }
        }
      } { user =>
        // we have a distinct ok user here
        idOption.fold {
          // docs for user, no id provided => all user visible docs
          // TODO technically would be more than "only" publicly visible at some point
          val publicDocs = owcContextDAO.getAllPublicOwcDocuments
          val userDocs = owcContextDAO.findOwcDocumentByUser(user.email)

          publicDocs ++ userDocs
        } {
          // TODO find doc by id for provided user if visible/available (later maybe check constraint)
          id => {
            owcContextDAO.findOwcDocumentByIdAndUser(id, user.email).toSeq
          }
        }
      }
    }
    }
  }

  /**
    * creates the first personal default collection for a user, typically at the stage of user registration
    * @param user
    */
  def createUserDefaultCollection(user: User): Unit = {

    val propsUuid = UUID.randomUUID()
    val link1 = OwcLink(UUID.randomUUID(), "profile", None, "http://www.opengis.net/spec/owc-atom/1.0/req/core", Some("This file is compliant with version 1.0 of OGC Context"))
    val link2 = OwcLink(UUID.randomUUID(), "self", Some("application/json"), s"http://portal.smart-project.info/context/user/${propsUuid.toString}", None)

    val author1 = OwcAuthor(UUID.randomUUID(), s"${user.firstname} ${user.lastname}", Some(user.email), None)

    val defaultOwcProps = OwcProperties(
      propsUuid,
      "en",
      "User Default Collection",
      Some("Your personal collection"),
      Some(ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())),
      None,
      Some("CC BY SA 4.0 NZ"),
      List(author1),
      List(),
      None,
      Some("GNS Science"),
      List(),
      List(link1, link2)
    )

    val defaultOwcDoc = OwcDocument(s"http://portal.smart-project.info/context/user/${propsUuid.toString}",
      None, defaultOwcProps, List())

    val ok = owcContextDAO.createUsersDefaultOwcDocument(defaultOwcDoc, user.email)
    ok match {
      case Some(theDoc) => logger.info(s"created default collection for user ${user.firstname} ${user.lastname}" )
      case _ => logger.error("Something failed miserably")
    }
  }

  /**
    *
    * @param mdMetadata
    * @param email
    * @return
    */
  def addMdEntryToUserDefaultCollection(catalogUrl: String, mdMetadata: MdMetadata, email: String) : Boolean = {

    val cswGetCapaOps = OwcOperation(UUID.randomUUID(),
      "GetCapabilities",
      "GET",
      "application/xml",
      s"$catalogUrl/pycsw/csw?SERVICE=CSW&VERSION=2.0.2&REQUEST=GetCapabilities",
      None, None)

    val cswGetRecordOps = OwcOperation(UUID.randomUUID(),
      "GetRecordById",
      "GET",
      "application/xml",
      s"$catalogUrl/pycsw/csw?request=GetRecordById&service=CSW&version=2.0.2&elementSetName=full&outputSchema=http://www.isotc211.org/2005/gmd&id=${mdMetadata.fileIdentifier}",
      None, None)

    val propsUuid = UUID.randomUUID()
    val updatedTime = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())

    val cswOffering = CswOffering(
      UUID.randomUUID(),
      "http://www.opengis.net/spec/owc-geojson/1.0/req/csw",
      List(cswGetCapaOps, cswGetRecordOps),
      List()
    )

    // self link should concur with EntryId Thhtp URI
    val link1 = OwcLink(UUID.randomUUID(), "self", Some("application/json"), s"http://portal.smart-project.info/context/entry/${propsUuid.toString}", None)
    val defaultCollection = owcContextDAO.findUserDefaultOwcDocument(email)

    val upsertOk = defaultCollection.map{ owcDoc => {
        val author1 = owcDoc.properties.authors.head
        val contributor = OwcAuthor(UUID.randomUUID(),
          mdMetadata.responsibleParty.individualName,
          Some(mdMetadata.responsibleParty.email),
          Some(mdMetadata.responsibleParty.orgWebLinkage))

        // TODO fill up very much a lot of stuff
        val entryProps = OwcProperties(
          propsUuid,
          "en",
          mdMetadata.title,
          Some(mdMetadata.abstrakt),
          Some(updatedTime),
          None,
          Some(mdMetadata.distribution.useLimitation),
          List(author1),
          List(contributor),
          None,
          Some("GNS Science"),
          List(),
          List(link1)
        )
        val owcEntry = OwcEntry("http://portal.smart-project.info/context/entry/" + mdMetadata.fileIdentifier,
          None, entryProps, List(cswOffering))
        val entries = owcDoc.features ++ Seq(owcEntry)
        val newDoc = owcDoc.copy(features = entries)

        owcContextDAO.addOwcEntryToOwcDocument(newDoc, owcEntry, email).isDefined
      }
    }
    upsertOk.getOrElse(false)
  }

  /**
    *
    * @param owcEntry
    * @param email
    * @return
    */
  def addPlainFileEntryToUserDefaultCollection(owcEntry: OwcEntry, email: String) : Boolean = {
    val defaultCollection = owcContextDAO.findUserDefaultOwcDocument(email)

    val upsertOk = defaultCollection.map {
      owcDoc => {
        val author1 = owcDoc.properties.authors.head

        val newProps = owcEntry.properties.copy(authors = owcEntry.properties.authors ++ Seq(author1))
        val newEntry = owcEntry.copy(properties = newProps)
        val entries = owcDoc.features ++ Seq(newEntry)
        val newDoc = owcDoc.copy(features = entries)

        owcContextDAO.addOwcEntryToOwcDocument(newDoc, newEntry, email).isDefined
      }
    }
    upsertOk.getOrElse(false)
  }

  /**
    *
    * @param owcEntry
    * @param email
    * @return
    */
  def addEntryToCollection(owcDocumentId: String, owcEntry: OwcEntry, email: String) : Option[OwcDocument] = {
    val collection = owcContextDAO.findOwcDocumentByIdAndUser(owcDocumentId, email)

    collection.fold {
      logger.warn(s"No usable collection owcdoc id $owcDocumentId found for $email")
      val empty: Option[OwcDocument] = None
      empty
    }{
      owcDoc => {
        val entries = owcDoc.features ++ Seq(owcEntry)
        val newDoc = owcDoc.copy(features = entries)
        owcContextDAO.addOwcEntryToOwcDocument(newDoc, owcEntry, email)
      }
    }
  }

  /**
    *
    * @param owcEntry
    * @param email
    * @return
    */
  def replaceEntryInCollection(owcDocumentId: String, owcEntry: OwcEntry, email: String) : Option[OwcDocument] = {
    val collection = owcContextDAO.findOwcDocumentByIdAndUser(owcDocumentId, email)

    collection.fold {
      logger.warn(s"No usable collection owcdoc id $owcDocumentId found for $email")
      val empty: Option[OwcDocument] = None
      empty
    }{
      owcDoc => {
        // at first filter the entry out of the current collection and then add the updated entry back in
        val entries = owcDoc.features.filterNot( _.id.equalsIgnoreCase(owcEntry.id)) ++ Seq(owcEntry)
        val newDoc = owcDoc.copy(features = entries)
        owcContextDAO.replaceEntryInCollection(newDoc, owcEntry, email)
      }
    }
  }

  def deleteEntryFromCollection(owcDocumentId: String, entryid: String, email: String) : Option[OwcDocument] = {
    val collection = owcContextDAO.findOwcDocumentByIdAndUser(owcDocumentId, email)
    collection.fold {
      logger.warn(s"No usable collection owcdoc id $owcDocumentId found for $email")
      val empty: Option[OwcDocument] = None
      empty
    } {
      owcDoc => {
        // filter the entry out of the current collection
        val entries = owcDoc.features.filterNot( _.id.equalsIgnoreCase(entryid))
        val newDoc = owcDoc.copy(features = entries)
        owcContextDAO.deleteOwcEntryFromOwcDocument(newDoc, entryid, email)
      }
    }
  }

  /**
    *
    * @param owcDocument
    * @param email
    * @return
    */
  def insertCollection(owcDocument: OwcDocument, email: String) = {
    val owcOk = owcContextDAO.createCustomOwcDocument(owcDocument, email)
    owcOk
  }

  /**
    *
    * @param owcDocument
    * @param email
    * @return
    */
  def updateCollectionMetadata(owcDocument: OwcDocument, email: String) : Option[OwcDocument] = {
    logger.error(s"${owcDocument.id} should be updated, but is currently not implemented")
    owcContextDAO.findOwcDocumentByIdAndUser(owcDocument.id, email)
  }
  /**
    *
    * @param owcDocumentId
    * @param email
    * @return
    */
  def deleteCollection(owcDocumentId: String, email: String) : Boolean ={
    val hasOwcDoc = owcContextDAO.findOwcDocumentByIdAndUser(owcDocumentId, email)
    hasOwcDoc.fold{
      false
    } {
      theDoc => {
        owcContextDAO.deleteOwcDocument(theDoc)
      }
    }
  }

}
