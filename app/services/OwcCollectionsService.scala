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

import javax.inject._

import models.owc.{OwcDocument, OwcDocumentDAO, OwcOfferingDAO, OwcPropertiesDAO}
import models.users.UserDAO
import utils.ClassnameLogger

@Singleton
class OwcCollectionsService @Inject()(userDAO: UserDAO,
                                      owcPropertiesDAO: OwcPropertiesDAO,
                                      owcOfferingDAO: OwcOfferingDAO,
                                      owcDocumentDAO: OwcDocumentDAO) extends ClassnameLogger {
  logger.error("service starting")

  /**
    * get Owc Documents For optional username And owc doc Id
    *
    * @param authUserOption
    * @param idOption
    * @return
    */
  def getOwcDocumentsForUserAndId(authUserOption: Option[String], idOption: Option[String]) : Seq[OwcDocument] = {

    authUserOption.fold {
      // no username provided
      idOption.fold {
        // TODO docs for anonymous, no id provided => all public docs (later maybe check if public)
        owcDocumentDAO.getAllOwcDocuments
      } {
        // TODO find doc by id for anonymous, only one doc if available (later maybe check if public)
        id => {
          owcDocumentDAO.findOwcDocumentsById(id).toSeq
        }
      }
    } { authUser => {
      // trying to find a user from provided authuser option
      userDAO.findByUsername(authUser).fold {
        logger.warn("Provided user not found.")
        idOption.fold {
          // TODO docs for anonymous, no id provided => all public docs (later maybe check if public)
          owcDocumentDAO.getAllOwcDocuments
        } {
          // docs for anonymous, but id provided only one doc if available (and only if public)
          id => {
            owcDocumentDAO.findOwcDocumentsById(id).toSeq
          }
        }
      } { user =>
        // we have a distinct ok user here
        idOption.fold {
          // docs for user, no id provided => all user visible docs
          // TODO technically would be more than "only" publicly visible at some point
          owcDocumentDAO.getAllOwcDocuments
        } {
          // TODO find doc by id for provided user if visible/available (later maybe check constraint)
          id => {
            owcDocumentDAO.findOwcDocumentsById(id).toSeq
          }
        }
      }
    }
    }
  }

  def addEntryToUserDefaultCollection = ???

  def insertCollection(owcDocument: OwcDocument, username: String) = {
    val owcOk = owcDocumentDAO.createOwcDocument(owcDocument)
    owcOk
  }

  def updateCollection = ???

  def deleteCollection = ???

}
