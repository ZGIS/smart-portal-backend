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

import java.net.{URL, URLEncoder}
import java.time._
import java.util.UUID
import javax.inject._

import info.smart.models.owc100._
import models.db.SessionHolder
import models.gmd.MdMetadata
import models.owc._
import models.users._
import play.api.Configuration
import uk.gov.hmrc.emailaddress.EmailAddress
import utils.{ClassnameLogger, GeoDateParserUtils}
import utils.StringUtils._

import scala.util.{Success, Try}

@Singleton
class OwcCollectionsService @Inject()(sessionHolder: SessionHolder,
                                      config: Configuration) extends ClassnameLogger {

  val configuration: play.api.Configuration = config
  lazy private val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")

  /**
    * get user's default collection
    *
    * @param authUser
    * @return
    */
  def getUserDefaultOwcContext(authUser: String): Option[OwcContext] = {
    sessionHolder.viaConnection(implicit connection =>
      UserDAO.findUserByEmailAsString(authUser).map(
        user =>
          OwcContextDAO.findUserDefaultOwcContext(user))).getOrElse(None)
  }

  /**
    * get user's own files
    *
    * @param authUser
    * @return
    */
  def getOwcLinksForOwcAuthorOwnFiles(authUser: String): Seq[OwcLink] = {

    val userCollection = sessionHolder.viaConnection(implicit connection =>
      UserDAO.findUserByEmailAsString(authUser).map(
        u => OwcContextDAO.findUserDefaultOwcContext(u))).getOrElse(None)
    userCollection.fold {
      logger.warn(s"user ${authUser} doesn't have personal collection")
      Seq[OwcLink]()
    } {
      owcDoc =>
        owcDoc.resource.filter(o => o.contentByRef.nonEmpty).flatMap(o => o.contentByRef)
    }

  }

  /**
    * get Owc Contexts For optional email And owc doc Id
    *
    * @param authUserOption
    * @param owcContextIdOption
    * @return
    */
  def getOwcContextsForUserAndId(authUserOption: Option[String], owcContextIdOption: Option[String]): Seq[OwcContext] = {

    authUserOption.fold {
      // no email provided
      owcContextIdOption.fold {
        // TODO docs for anonymous, no id provided => all public docs (implies docs must be public)
        sessionHolder.viaConnection(implicit connection => OwcContextDAO.getAllPublicOwcContexts)
      } {
        // TODO find doc by id for anonymous, only one doc if available (implies doc must be public)
        owcContextId => {
          sessionHolder.viaConnection(implicit connection => OwcContextDAO.findPublicOwcContextsById(owcContextId).toSeq)
        }
      }
    } { authUser => {
      // trying to find a user from provided authuser option
      sessionHolder.viaConnection(implicit connection =>
        UserDAO.findUserByEmailAsString(authUser).fold {
          logger.warn("Provided user not found.")
          owcContextIdOption.fold {
            // TODO docs for anonymous, no id provided => all public docs (later maybe check if public)
            OwcContextDAO.getAllPublicOwcContexts
          } {
            // docs for anonymous, but id provided only one doc if available (and only if public)
            owcContextId => {
              OwcContextDAO.findPublicOwcContextsById(owcContextId).toSeq
            }
          }
        } { user =>
          // we have a distinct ok user here
          owcContextIdOption.fold {
            // docs for user, no id provided => all user visible docs
            // TODO technically would be more than "only" publicly visible at some point
            val publicDocs = OwcContextDAO.getAllPublicOwcContexts
            val userDocs = OwcContextDAO.findOwcContextsByUser(user)

            publicDocs ++ userDocs
          } {
            // TODO find doc by id for provided user if visible/available (later maybe check constraint)
            owcContextId => {
              OwcContextDAO.findOwcContextByIdAndUser(owcContextId, user).toSeq
            }
          }
        }
      )
    }
    }
  }

  /**
    * creates the first personal default collection for a user, typically at the stage of user registration
    *
    * @param user
    */
  def createUserDefaultCollection(user: User): Option[OwcContext] = {

    val propsUuid = UUID.randomUUID()
    val profileLink = OwcProfile.CORE.newOf

    val author1 = OwcAuthor(Some(s"${user.firstname} ${user.lastname}"), Some(EmailAddress(user.email)), None, UUID.randomUUID())

    val defaultOwcDoc = OwcContext(
      id = new URL(s"http://portal.smart-project.info/context/user/${propsUuid.toString}"),
      areaOfInterest = None,
      specReference = List(profileLink), // aka links.profiles[] & rel=profile
      contextMetadata = List(), // aka links.via[] & rel=via
      language = "en",
      title = "User Default Collection",
      subtitle = Some("Your personal collection"),
      updateDate = OffsetDateTime.now(ZoneId.of(appTimeZone)),
      author = List(author1),
      publisher = Some("GNS Science"),
      creatorApplication = None,
      creatorDisplay = None,
      rights = Some("CC BY SA 4.0 NZ"),
      timeIntervalOfInterest = None,
      keyword = List(),
      resource = List())

    val ok = sessionHolder.viaTransaction(implicit connection =>
      OwcContextDAO.createUsersDefaultOwcContext(defaultOwcDoc, user))
    ok match {
      case Some(theDoc) => {
        logger.info(s"created default collection for user ${user.firstname} ${user.lastname}")
        Some(theDoc)
      }
      case _ => {
        logger.error("Something failed miserably")
        None
      }
    }
  }

  /**
    *
    * @param catalogUrl
    * @param mdMetadata
    * @param authUser
    * @return
    */
  def addMdResourceToUserDefaultCollection(catalogUrl: String, mdMetadata: MdMetadata, authUser: String): Boolean = {

    lazy val bboxFormat = new BboxArrayFormat

    val updatedTime = Try(OffsetDateTime.of(mdMetadata.citation.ciDate, LocalTime.of(12, 0), ZoneOffset.UTC))
      .getOrElse(OffsetDateTime.now(ZoneId.systemDefault()))
    val baseLink = new URL(s"http://portal.smart-project.info/context/resource/${URLEncoder.encode(mdMetadata.fileIdentifier, "UTF-8")}_copy_${UUID.randomUUID().toString}")

    val cswGetCapaOps = OwcOperation(
      code = "GetCapabilities",
      method = "GET",
      mimeType = Some("application/xml"),
      requestUrl = new URL("https://portal.smart-project.info/pycsw/csw?SERVICE=CSW&VERSION=2.0.2&REQUEST=GetCapabilities"))

    val cswGetRecordOps = OwcOperation(
      code = "GetRecordById",
      method = "GET",
      mimeType = Some("application/xml"),
      requestUrl = new URL(s"https://portal.smart-project.info/pycsw/csw?request=GetRecordById&service=CSW&version=2.0.2&elementSetName=full&outputSchema=http://www.isotc211.org/2005/gmd&id=${mdMetadata.fileIdentifier}"))

    val cswOffering = OwcOffering(
      code = OwcOfferingType.CSW.code,
      operations = List(cswGetCapaOps, cswGetRecordOps)
    )

    val viaLink = OwcLink(
      href = baseLink,
      mimeType = Some("application/json"),
      rel = "via")

    val cswLink = OwcLink(
      href = cswGetRecordOps.requestUrl,
      mimeType = cswGetRecordOps.mimeType,
      rel = "via")

    val userLookup = sessionHolder.viaConnection { implicit connection =>
      UserDAO.findUserByEmailAsString(authUser)
    }

    userLookup.fold {
      // user not found, then can't insert, shouldn't happen though
      logger.error("User not found, can't access users collection")
      false
    } {
      user =>
        val collectionLookup = sessionHolder.viaConnection { implicit connection =>
          OwcContextDAO.findUserDefaultOwcContext(user)
        }
        collectionLookup.fold {
          // collection not found, then can't insert, should probably also not happen usually
          logger.error("Collection not found, can't access users collection")
          false
        } {
          owcDoc =>

            val owcResource = OwcResource(
              id = baseLink,
              geospatialExtent = bboxFormat.parseBboxArray(mdMetadata.extent.mapExtentCoordinates).toOption,
              title = mdMetadata.title,
              subtitle = mdMetadata.abstrakt.toOption(),
              updateDate = updatedTime,
              author = List(OwcAuthor(name = mdMetadata.responsibleParty.individualName.toOption(),
                email = parseEmailStringtoEmailAddress(mdMetadata.responsibleParty.email),
                uri = Some(new URL(mdMetadata.responsibleParty.orgWebLinkage)))),
              publisher = mdMetadata.responsibleParty.pointOfContact.toOption(),
              rights = mdMetadata.distribution.useLimitation.toOption(),
              temporalExtent = GeoDateParserUtils.parseOffsetDateString(Some(mdMetadata.extent.temporalExtent)),

              // links.alternates[] and rel=alternate
              contentDescription = List(),

              // aka links.previews[] and rel=icon (atom)
              preview = List(),

              // aka links.data[] and rel=enclosure (atom)
              contentByRef = List(),

              // aka links.via[] & rel=via
              resourceMetadata = List(viaLink, cswLink),
              offering = List(cswOffering),
              minScaleDenominator = Try(mdMetadata.scale.toDouble).toOption,
              maxScaleDenominator = None,
              active = None,
              keyword = mdMetadata.keywords.map(w => OwcCategory(term = w, scheme = None, label = None)),
              folder = None)

            val entries = owcDoc.resource ++ Seq(owcResource)
            val newDoc = owcDoc.copy(resource = entries)

            sessionHolder.viaTransaction { implicit connection =>
              OwcContextDAO.updateOwcContext(newDoc, user).isDefined
            }
        }
    }
  }

  /**
    *
    * @param owcResource
    * @param authUser
    * @return
    */
  def addPlainFileResourceToUserDefaultCollection(owcResource: OwcResource, authUser: String): Boolean = {

    val userLookup = sessionHolder.viaConnection { implicit connection =>
      UserDAO.findUserByEmailAsString(authUser)
    }
    userLookup.fold {
      // user not found, then can't insert, shouldn't happen though
      logger.error("User not found, can't access users collection")
      false
    } {
      user =>
        val collectionLookup = sessionHolder.viaConnection { implicit connection =>
          OwcContextDAO.findUserDefaultOwcContext(user)
        }
        collectionLookup.fold {
          // collection not found, then can't insert, should probably also not happen usually
          logger.error("Collection not found, can't access users collection")
          false
        } {
          owcDoc =>
            val entries = owcDoc.resource ++ Seq(owcResource)
            val newDoc = owcDoc.copy(resource = entries)

            sessionHolder.viaTransaction { implicit connection =>
              OwcContextDAO.updateOwcContext(newDoc, user).isDefined
            }
        }
    }
  }

  /**
    *
    * @param owcContext
    * @param authUser
    * @return
    */
  def insertCollection(owcContext: OwcContext, authUser: String): Option[OwcContext] = {
    sessionHolder.viaTransaction(implicit connection =>
      UserDAO.findUserByEmailAsString(authUser).map(
        user =>
          OwcContextDAO.createCustomOwcContext(owcContext, user))).getOrElse(None)
  }

  /**
    *
    * @param owcContext
    * @param authUser
    * @return
    */
  def updateCollection(owcContext: OwcContext, authUser: String): Option[OwcContext] = {
    sessionHolder.viaTransaction(implicit connection =>
      UserDAO.findUserByEmailAsString(authUser).map(
        user =>
          OwcContextDAO.updateOwcContext(owcContext, user))).getOrElse(None)
  }

  /**
    *
    * @param owcContext
    * @param authUser
    * @return
    */
  def deleteCollection(owcContext: OwcContext, authUser: String): Boolean = {
    sessionHolder.viaConnection(implicit connection =>
      UserDAO.findUserByEmailAsString(authUser).exists {
        user =>
          val hasOwcDoc = OwcContextDAO.findOwcContextByIdAndUser(owcContext.id.toString, user)
          hasOwcDoc.fold {
            false
          } {
            theDoc => {
              OwcContextDAO.deleteOwcContext(owcContext, user)
            }
          }
      })
  }

  /**
    * returns an Option[EmailAddress] object if parsing is successful, for those mdMetadata email joint arrays
    *
    * @param emailString the string that might comprise of or contain an email address
    * @return Option[EmailAddress]
    */
  def parseEmailStringtoEmailAddress(emailString: String): Option[EmailAddress] = {

    if (EmailAddress.isValid(emailString)) {
      Some(EmailAddress(emailString))
    } else {
      if (emailString.contains(",")) {
        val commaFree = emailString.split(",").head.trim

        Try {
          EmailAddress(commaFree)
        } match {
          case Success(e) => Some(e)
          case _ => None
        }
      } else {
        None
      }
    }
  }
}
