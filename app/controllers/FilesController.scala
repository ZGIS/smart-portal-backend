/*
 * Copyright (c) 2011-2017 Interfaculty Department of Geoinformatics, University of
 * Salzburg (Z_GIS) & Institute of Geological and Nuclear Sciences Limited (GNS Science)
 * in the SMART Aquifer Characterisation (SAC) programme funded by the New Zealand
 * Ministry of Business, Innovation and Employment (MBIE)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import java.net.{URL, URLEncoder}
import java.time._
import java.util.UUID
import javax.inject.Inject

import com.google.cloud.storage.{Acl, Blob, BlobInfo, Storage, StorageOptions}
import com.google.common.io.Files
import info.smart.models.owc100._
import models.ErrorResult
import models.db.SessionHolder
import models.users.UserDAO
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.{GoogleServicesDAO, OwcCollectionsService}
import uk.gov.hmrc.emailaddress.EmailAddress
import utils.StringUtils.OptionConverters
import utils.{ClassnameLogger, PasswordHashing}

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
  * 
  * @param config
  * @param cacheApi
  * @param passwordHashing
  * @param wsClient
  * @param context
  * @param googleService
  * @param sessionHolder
  * @param collectionsService
  */
class FilesController @Inject()(config: Configuration,
                                cacheApi: CacheApi,
                                val passwordHashing: PasswordHashing,
                                wsClient: WSClient,
                                implicit val context: ExecutionContext,
                                googleService: GoogleServicesDAO,
                                sessionHolder: SessionHolder,
                                collectionsService: OwcCollectionsService
                               )
  extends Controller with ClassnameLogger with Security {

  lazy private val appTimeZone: String = configuration.getString("datetime.timezone").getOrElse("Pacific/Auckland")
  lazy private val googleClientSecret: String = configuration.getString("google.client.secret")
    .getOrElse("client_secret.json")
  lazy private val googleStorageBucket: String = configuration.getString("google.storage.bucket")
    .getOrElse("smart-backup")
  lazy private val googleProjectId: String = configuration.getString("google.project.id")
    .getOrElse("dynamic-cove-129211")
  lazy private val uploadDataPath: String = configuration.getString("smart.upload.datapath")
    .getOrElse("/tmp")
  val cache: play.api.cache.CacheApi = cacheApi
  val configuration: play.api.Configuration = config

  /**
    *
    * @param filename
    * @param contentType
    * @param accountSubject
    * @param filelink
    * @param fileSize
    * @return
    */
  def fileMetadata(filename: String, contentType: Option[String], accountSubject: String, filelink: String, fileSize: Option[Int]): OwcResource = {
    sessionHolder.viaConnection { implicit connection =>
      val propsUuid = UUID.randomUUID()
      val updatedTime = OffsetDateTime.now(ZoneId.of(appTimeZone))
      val user = UserDAO.findByAccountSubject(accountSubject)
      val email = user.map(u => EmailAddress(u.email))
      val owcAuthor = user.map(u => OwcAuthor(name = s"${u.firstname} ${u.lastname}".toOption(), email = email, uri = None))
      val baseLink = new URL(s"http://portal.smart-project.info/context/resource/${URLEncoder.encode(propsUuid.toString, "UTF-8")}")

      val viaLink = OwcLink(href = baseLink,
        mimeType = Some("application/json"),
        lang = None,
        title = None,
        length = None,
        rel = "via")

      val dataLink = OwcLink(href = new URL(filelink),
        mimeType = if (contentType.isDefined) contentType else Some("application/octet-stream"),
        lang = None,
        title = Some(filename),
        length = fileSize,
        rel = "enclosure")

      OwcResource(
        id = baseLink,
        geospatialExtent = None,
        title = filename,
        subtitle = Some(s"$filename uploaded to $filelink via GW Hub by $email"),
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
    * @return
    */
  def uploadMultipartForm: Action[MultipartFormData[TemporaryFile]] = HasToken(parse.multipartFormData) {
    token =>
      authUser =>
        implicit request =>

          request.body.file("file").map { theFile =>
            import java.io.File
            val filename = theFile.filename
            val contentType = theFile.contentType
            val tmpFile = new File(s"$uploadDataPath/$filename")
            val handle = theFile.ref.moveTo(tmpFile)
            val kilobytes = handle.length / 1024

            // Google Java upload stuff
            import scala.collection.JavaConverters._

            // val storage: Storage = authenticatedStorageOptions.getService()
            val storage: Storage = StorageOptions.getDefaultInstance().getService()
            // val noCredentials = NoCredentials.getInstance()

            // Modify access list to allow all users with link to read file
            val roAcl = Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER)
            val acls: java.util.List[Acl] = List(roAcl).asJava

            // the inputstream is closed by default, so we don't need to close it here
            val blob: Blob = storage.create(BlobInfo.newBuilder(googleStorageBucket, filename).setAcl(acls).build(),
              Files.asByteSource(tmpFile).openStream())

            // return the public download link
            val owcResource = fileMetadata(filename, contentType, authUser, blob.getMediaLink(), Try(kilobytes.toInt).toOption)
            val insertOk = collectionsService.addPlainFileResourceToUserDefaultCollection(owcResource, authUser)

            if (insertOk) {
              logger.debug(s"file upload $filename to ${blob.getMediaLink}")
              //FIXME SR do we also want to have a general "return" object? Status is always in the respone so it does not need to be in here
              Ok(Json.obj("status" -> "OK", "message" -> s"file uploaded $filename.", "file" -> blob.getMediaLink(), "entry" -> owcResource.toJson))
            } else {
              logger.error("file metadata insert failed.")
              val error = ErrorResult("File metadata insert failed.", None)
              BadRequest(Json.toJson(error)).as(JSON)
            }
          }.getOrElse {
            logger.error("file upload failed")
            val error = ErrorResult("File upload failed.", None)
            BadRequest(Json.toJson(error)).as(JSON)
          }
  }

  /*  def uploadDirect = Action(parseOm2Measurements.temporaryFile) { request =>
      request.body.moveTo(new File("/tmp/uploaded.bin"))
      Ok("File uploaded")
    }*/


}
