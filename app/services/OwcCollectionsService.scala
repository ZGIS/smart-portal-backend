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

import info.smart.models.owc100._
import javax.inject._
import models.db.DatabaseSessionHolder
import models.gmd.MdMetadata
import models.owc._
import models.users._
import play.api.libs.json.Json
import uk.gov.hmrc.emailaddress.EmailAddress
import utils.StringUtils._
import utils.{ClassnameLogger, GeoDateParserUtils}

import scala.util.{Success, Try}

@Singleton
class OwcCollectionsService @Inject()(dbSession: DatabaseSessionHolder,
                                      portalConfig: PortalConfig,
                                      userGroupService: UserGroupService) extends ClassnameLogger {

  lazy private val appTimeZone: String = portalConfig.appTimeZone

  /**
    * get user's default collection
    *
    * @param user
    * @return
    */
  def getUserDefaultOwcContext(user: User): Option[OwcContext] = {
    dbSession.viaConnection(implicit connection =>
      OwcContextDAO.findUserDefaultOwcContext(user)
    )
  }

  /**
    * get user's own files
    *
    * @param user
    * @return
    */
  def getOwcLinksForOwcAuthorOwnFiles(user: User): Seq[OwcLink] = {

    val userCollection = dbSession.viaConnection(implicit connection =>
      OwcContextDAO.findUserDefaultOwcContext(user))

    userCollection.fold {
      logger.warn(s"user ${user.email} doesn't have personal collection")
      Seq[OwcLink]()
    } {
      owcDoc =>
        owcDoc.resource.filter(o => o.contentByRef.nonEmpty).flatMap(o => o.contentByRef)
    }

  }

  /**
    * get Owc Contexts For optional email And owc doc Id
    *
    * @param userOption
    * @param owcContextIdOption
    * @return
    */
  def queryOwcContextsForUserAndIdForViewing(userOption: Option[User], owcContextIdOption: Option[String]): Seq[OwcContext] = {

    userOption.fold {
      // no user provided
      owcContextIdOption.fold {
        // TODO docs for anonymous, no id provided => all public docs (implies docs must be public)
        dbSession.viaConnection(implicit connection => OwcContextDAO.getAllPublicOwcContexts)
      } {
        // TODO find doc by id for anonymous, only one doc if available (implies doc must be public)
        owcContextId => {
          dbSession.viaConnection(implicit connection => OwcContextDAO.findPublicOwcContextsById(owcContextId).toSeq)
        }
      }
    } { user =>
      // trying to find for the provided user from option
      dbSession.viaConnection(implicit connection =>
        // we have a distinct ok user here
        owcContextIdOption.fold {
          // docs for user, no id provided => all user visible docs
          // TODO technically would be more than "only" publicly visible at some point
          val publicDocs = OwcContextDAO.getAllPublicOwcContexts
          val userDocs = OwcContextDAO.findOwcContextsByNativeOwner(user)

          val groupDocs: Seq[OwcContext] = userGroupService.getUsersOwnUserGroups(user).flatMap { ug =>
            ug.hasOwcContextsVisibility.filter(v => v.visibility > 0)
              .flatMap(v => OwcContextDAO.findOwcContextsById(v.owc_context_id))
          }

          (publicDocs ++ userDocs ++ groupDocs).distinct
        } {
          // TODO find doc by id for provided user if visible/available (later maybe check constraint)
          owcContextId =>
            val groupDocs: Seq[OwcContext] = userGroupService.getUsersOwnUserGroups(user).flatMap { ug =>
              ug.hasOwcContextsVisibility.filter(v => v.visibility > 0)
                .flatMap(v => OwcContextDAO.findOwcContextsById(v.owc_context_id))
            }
            if (groupDocs.exists(o => o.id.toString.contentEquals(owcContextId))) {
              OwcContextDAO.findOwcContextsById(owcContextId).toSeq
            } else {
              OwcContextDAO.findOwcContextByIdAndNativeOwner(owcContextId, user).toSeq
            }
        }
      )
    }
  }

  /**
    * get OwcContexts For User And optional Id, beware, now also already provides the group access ones
    * must always cross check in GUI if editable or not, will not be allowed by backend anyway, but not immediately visible
    *
    * @param user
    * @param owcContextIdOption
    * @return
    */
  def getOwcContextsForUserAndId(user: User, owcContextIdOption: Option[String]): Seq[OwcContext] = {
    // trying to find for the provided user from option
    dbSession.viaConnection(implicit connection =>
      // we have a distinct ok user here
      owcContextIdOption.fold {
        // docs for user, no id provided => all user visible and relevant docs, basically own and group shared
        val userDocs = OwcContextDAO.findOwcContextsByNativeOwner(user)

        val groupDocs: Seq[OwcContext] = userGroupService.getUsersOwnUserGroups(user).flatMap { ug =>
          ug.hasOwcContextsVisibility.filter(v => v.visibility > 0)
            .flatMap(v => OwcContextDAO.findOwcContextsById(v.owc_context_id))
        }

        (userDocs ++ groupDocs).distinct
      } {
        // TODO find doc by id for provided user if shared or owned (later maybe check constraint)
        owcContextId =>
          val groupDocs: Seq[OwcContext] = userGroupService.getUsersOwnUserGroups(user).flatMap { ug =>
            ug.hasOwcContextsVisibility.filter(v => v.visibility > 0)
              .flatMap(v => OwcContextDAO.findOwcContextsById(v.owc_context_id))
          }
          if (groupDocs.exists(o => o.id.toString.contentEquals(owcContextId))) {
            OwcContextDAO.findOwcContextsById(owcContextId).toSeq
          } else {
            OwcContextDAO.findOwcContextByIdAndNativeOwner(owcContextId, user).toSeq
          }
      }
    )
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
      id = new URL(s"${portalConfig.portalExternalBaseLink}/context/user/${propsUuid.toString}"),
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

    val ok = dbSession.viaTransaction(implicit connection =>
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
    * making it easier to handle creation of new custom collection for user, server-side vs client-side
    *
    * @param user
    * @return
    */
  def createNewCustomCollection(user: User): Option[OwcContext] = {

    val propsUuid = UUID.randomUUID()
    val profileLink = OwcProfile.CORE.newOf

    val author1 = OwcAuthor(Some(s"${user.firstname} ${user.lastname}"), Some(EmailAddress(user.email)), None, UUID.randomUUID())

    val defaultOwcDoc = OwcContext(
      id = new URL(s"${portalConfig.portalExternalBaseLink}/context/document/${propsUuid.toString}"),
      areaOfInterest = None,
      specReference = List(profileLink), // aka links.profiles[] & rel=profile
      contextMetadata = List(), // aka links.via[] & rel=via
      language = "en",
      title = "New Collection",
      subtitle = Some("A new custom collection"),
      updateDate = OffsetDateTime.now(ZoneId.of(appTimeZone)),
      author = List(author1),
      publisher = Some("GNS Science"),
      creatorApplication = None,
      creatorDisplay = None,
      rights = Some("CC BY SA 4.0 NZ"),
      timeIntervalOfInterest = None,
      keyword = List(),
      resource = List())

    val ok = dbSession.viaTransaction(implicit connection =>
      OwcContextDAO.createCustomOwcContext(defaultOwcDoc, user))

    ok match {
      case Some(theDoc) => {
        logger.info(s"created new custom collection for user ${user.firstname} ${user.lastname}")
        Some(theDoc)
      }
      case _ => {
        logger.error("Something failed miserably")
        None
      }
    }

  }

  /**
    * generate the plain MdMetadataa resource for a CSW entry reference
    *
    * @param externalCatalogUrl
    * @param mdMetadata
    * @param userMetaEntry
    * @return
    */
  def generateMdResource(externalCatalogUrl: String, mdMetadata: MdMetadata, userMetaEntry: UserMetaRecord): OwcResource = {

    lazy val bboxFormat = new BboxArrayFormat

    val updatedTime = Try(OffsetDateTime.of(mdMetadata.citation.ciDate, LocalTime.of(12, 0), ZoneOffset.UTC))
      .getOrElse(OffsetDateTime.now(ZoneId.systemDefault()))

    val baseLink = new URL(s"${portalConfig.portalExternalBaseLink}/context/resource/${userMetaEntry.uuid.toString}")

    val cswGetCapaOps = OwcOperation(
      code = "GetCapabilities",
      method = "GET",
      mimeType = Some("application/xml"),
      requestUrl = new URL(s"${portalConfig.portalExternalBaseLink}/pycsw/csw?SERVICE=CSW&VERSION=2.0.2&REQUEST=GetCapabilities"))

    val cswGetRecordOps = OwcOperation(
      code = "GetRecordById",
      method = "GET",
      mimeType = Some("application/xml"),
      requestUrl = new URL(s"${portalConfig.portalExternalBaseLink}/pycsw/csw?request=GetRecordById&service=CSW&version=2.0.2&elementSetName=full&outputSchema=http://www.isotc211.org/2005/gmd&id=${mdMetadata.fileIdentifier}"))

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

    logger.trace(Json.prettyPrint(owcResource.toJson))

    owcResource
  }

  /**
    * add the MdResource OWC to colelction, becoming redundant?
    *
    * @param externalCatalogUrl
    * @param owcResource
    * @param userMetaEntry
    * @return
    */
  def addMdResourceToUserDefaultCollection(externalCatalogUrl: String, owcResource: OwcResource, userMetaEntry: UserMetaRecord): Boolean = {

    val userLookup = dbSession.viaConnection { implicit connection =>
      UserDAO.findByAccountSubject(userMetaEntry.users_accountsubject)
    }

    userLookup.fold {
      // user not found, then can't insert, shouldn't happen though
      logger.error("User not found, can't access users collection")
      false
    } {
      user =>
        val collectionLookup = dbSession.viaConnection { implicit connection =>
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

            logger.trace(Json.prettyPrint(newDoc.toJson))

            dbSession.viaTransaction { implicit connection =>
              OwcContextDAO.updateOwcContext(newDoc, user,
                Vector(OwcContextsRightsMatrix(
                  owcDoc.id.toString,
                  user.accountSubject,
                  user.accountSubject,
                  Seq(),
                  0,
                  2)
                )).isDefined
            }
        }
    }
  }

  def updateMdResourceInUserDefaultCollection(externalCatalogUrl: String, owcResource: OwcResource, userMetaEntry: UserMetaRecord): Boolean = {

    val userLookup = dbSession.viaConnection { implicit connection =>
      UserDAO.findByAccountSubject(userMetaEntry.users_accountsubject)
    }

    userLookup.fold {
      // user not found, then can't insert, shouldn't happen though
      logger.error("User not found, can't access users collection")
      false
    } {
      user =>
        val collectionLookup = dbSession.viaConnection { implicit connection =>
          OwcContextDAO.findUserDefaultOwcContext(user)
        }
        collectionLookup.fold {
          // collection not found, then can't insert, should probably also not happen usually
          logger.error("Collection not found, can't access users collection")
          false
        } {
          owcDoc =>

            // drop out old resource, add new resource, IDs are same, it's basically a replace
            val entries = owcDoc.resource.filterNot(o => o.id.equals(owcResource.id)) ++ Seq(owcResource)
            val newDoc = owcDoc.copy(resource = entries)

            logger.trace(Json.prettyPrint(newDoc.toJson))

            dbSession.viaTransaction { implicit connection =>
              OwcContextDAO.updateOwcContext(newDoc, user, Vector(OwcContextsRightsMatrix(
                owcDoc.id.toString,
                user.accountSubject,
                user.accountSubject,
                Seq(),
                0,
                2)
              )).isDefined
            }
        }
    }
  }

  /**
    *
    * @param userFile
    * @param contentType
    * @param fileSize
    * @return
    */
  def fileMetadata(userFile: UserFile, contentType: Option[String], fileSize: Option[Int]): OwcResource = {

    dbSession.viaConnection { implicit connection =>

      val updatedTime = OffsetDateTime.now(ZoneId.of(appTimeZone))
      val user = UserDAO.findByAccountSubject(userFile.users_accountsubject)
      val email = user.map(u => EmailAddress(u.email))
      val owcAuthor = user.map(u => OwcAuthor(name = s"${u.firstname} ${u.lastname}".toOption(), email = email, uri = None))
      val owcBaseLink = new URL(s"${portalConfig.portalExternalBaseLink}/context/resource/${URLEncoder.encode(userFile.uuid.toString, "UTF-8")}")

      val consentableFilelink = s"${portalConfig.portalExternalBaseLink}/context/file/${URLEncoder.encode(userFile.uuid.toString, "UTF-8")}"

      val viaLink = OwcLink(href = owcBaseLink,
        mimeType = Some("application/json"),
        lang = None,
        title = None,
        length = None,
        rel = "via")


      val dataLink = OwcLink(href = new URL(consentableFilelink),
        mimeType = if (contentType.isDefined) contentType else Some("application/octet-stream"),
        lang = None,
        title = Some(userFile.originalfilename),
        length = fileSize,
        rel = "enclosure")

      OwcResource(
        id = owcBaseLink,
        geospatialExtent = None,
        title = userFile.originalfilename,
        subtitle = Some(s"${userFile.originalfilename} uploaded to $consentableFilelink via GW Hub by $email"),
        updateDate = updatedTime,
        author = if (owcAuthor.isDefined) List(owcAuthor.get) else List(),
        publisher = Some("GNS Science"),
        rights = Some(s"IP limitation: Please inquire with $email"),
        temporalExtent = None,
        contentDescription = List(), // links.alternates[] and rel=alternate
        preview = List(), // aka links.previews[] and rel=icon (atom)
        contentByRef = List(dataLink), // aka links.data[] and rel=enclosure (atom)
        resourceMetadata = List(viaLink), // aka links.via[] & rel=via
        offering = List(),
        minScaleDenominator = None,
        maxScaleDenominator = None,
        active = None,
        keyword = List(),
        folder = None)
    }
  }

  /**
    *
    * @param owcResource
    * @param user
    * @return
    */
  def addPlainFileResourceToUserDefaultCollection(owcResource: OwcResource, user: User): Boolean = {

    val collectionLookup = dbSession.viaConnection { implicit connection =>
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

        dbSession.viaTransaction { implicit connection =>
          OwcContextDAO.updateOwcContext(newDoc, user, Vector(OwcContextsRightsMatrix(
            owcDoc.id.toString,
            user.accountSubject,
            user.accountSubject,
            Seq(),
            0,
            2)
          )).isDefined
        }
    }

  }

  /**
    *
    * @param owcContext
    * @param user
    * @return
    */
  def insertCollection(owcContext: OwcContext, user: User): Option[OwcContext] = {
    dbSession.viaTransaction(implicit connection =>
      OwcContextDAO.createCustomOwcContext(owcContext, user))
  }

  /**
    *
    * @param owcContext
    * @param user
    * @return
    */
  def updateCollection(owcContext: OwcContext, user: User): Option[OwcContext] = {
    val owcContextsRightsMatrix = userGroupService.getOwcContextsRightsMatrixForUser(user)

    dbSession.viaTransaction(implicit connection =>
      OwcContextDAO.updateOwcContext(owcContext, user, owcContextsRightsMatrix.toVector))
  }

  /**
    *
    * @param owcContext
    * @param user
    * @return
    */
  def deleteCollection(owcContext: OwcContext, user: User): Boolean = {
    val owcContextsRightsMatrix = userGroupService.getOwcContextsRightsMatrixForUser(user)

    // FIXME, either use further down as rights check or remove
    val areYouPowerUserForThisContext =
      owcContextsRightsMatrix.filter(_.owcContextId.contentEquals(owcContext.id.toString))
        .exists(_.queryingUserAccessLevel >= 2)

    dbSession.viaConnection {
      implicit connection =>

        // TODO, think if every power user of a group can delete collections shared into the group
        val hasOwcDoc = OwcContextDAO.findOwcContextByIdAndNativeOwner(owcContext.id.toString, user)
        hasOwcDoc.fold {
          false
        } {
          theDoc =>
            OwcContextDAO.deleteOwcContext(owcContext, user)

        }
    }
  }

  def updateOwcContextVisibility(owcContextId: String,
                                 user: User,
                                 visibility: Int): Boolean = {
    val owcContextsRightsMatrix = userGroupService.getOwcContextsRightsMatrixForUser(user)

    dbSession.viaTransaction(implicit connection =>

      OwcContextDAO.updateOwcContextVisibility(owcContextId, user.accountSubject, visibility, owcContextsRightsMatrix))
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
