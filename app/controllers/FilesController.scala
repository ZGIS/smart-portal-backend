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

import java.io.FileInputStream
import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID
import javax.inject.Inject

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.{Acl, Blob, BlobInfo, Storage, StorageOptions}
import com.google.common.io.Files

import models.owc.{HttpLinkOffering, OwcEntry, OwcLink, OwcOperation, OwcProperties}
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.{GoogleServicesDAO, OwcCollectionsService}
import utils.{ClassnameLogger, PasswordHashing}

import scala.concurrent.ExecutionContext

/**
  *
  * @param config
  * @param cacheApi
  * @param passwordHashing
  * @param wsClient
  * @param context
  * @param googleService
  * @param collectionsService
  */
class FilesController @Inject()(config: Configuration,
                                cacheApi: CacheApi,
                                val passwordHashing: PasswordHashing,
                                wsClient: WSClient,
                                implicit val context: ExecutionContext,
                                googleService: GoogleServicesDAO,
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
    * @param email
    * @param filelink
    * @return
    */
  def fileMetadata(filename: String, contentType: Option[String], email: String, filelink: String): OwcEntry = {

    val propsUuid = UUID.randomUUID()
    val updatedTime = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())

    val httpGetOps = OwcOperation(UUID.randomUUID(),
      "GetFile",
      "GET",
      contentType.getOrElse("application/octet-stream"),
      filelink,
      None, None)

    val httpLinkOffering = HttpLinkOffering(
      UUID.randomUUID(),
      "http://www.opengis.net/spec/owc-geojson/1.0/req/http-link",
      List(httpGetOps),
      List()
    )

    val link1 = OwcLink(UUID.randomUUID(), "self", Some("application/json"),
      s"http://portal.smart-project.info/context/entry/${propsUuid.toString}", None)

    val useLimitation = s"IP limitation: Please inquire with $email"

    val entryProps = OwcProperties(
      propsUuid,
      "en",
      filename,
      Some(s"$filename uploaded to $filelink via GW Hub by $email"),
      Some(updatedTime),
      None,
      Some(useLimitation),
      List(),
      List(),
      None,
      Some("GNS Science"),
      List(),
      List(link1)
    )
    val owcEntry = OwcEntry(s"http://portal.smart-project.info/context/${propsUuid.toString}",
      None, entryProps, List(httpLinkOffering))

    owcEntry
  }

  /**
    *
    * @return
    */
  def uploadMultipartForm = HasToken(parse.multipartFormData) {
    token =>
      authUser =>
        implicit request =>

          request.body.file("file").map { theFile =>
            import java.io.File
            val filename = theFile.filename
            val contentType = theFile.contentType
            val tmpFile = new File(s"$uploadDataPath/$filename")
            theFile.ref.moveTo(tmpFile)

            // Google Java upload stuff
            import scala.collection.JavaConverters._

//            val serviceAccountCredentials = ServiceAccountCredentials.fromStream(new FileInputStream(googleClientSecret))
//            val authenticatedStorageOptions = StorageOptions.newBuilder().setProjectId(googleProjectId)
//              .setCredentials(serviceAccountCredentials).build()

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
            val owcEntry = fileMetadata(filename, contentType, authUser, blob.getMediaLink())
            val insertOk = collectionsService.addPlainFileEntryToUserDefaultCollection(owcEntry, authUser)

            if (insertOk) {
              logger.debug(s"file upload $filename to ${blob.getMediaLink}")
              Ok(Json.obj("status" -> "OK", "message" -> s"file uploaded $filename.", "file" -> blob.getMediaLink(), "entry" -> owcEntry.toJson))
            } else {
              logger.error("file metadata insert failed.")
              BadRequest(Json.obj("status" -> "ERR", "message" -> "file metadata insert failed."))
            }
          }.getOrElse {
            logger.error("file upload failed")
            BadRequest(Json.obj("status" -> "ERR", "message" -> "file upload failed."))
          }
  }

  /*  def uploadDirect = Action(parseOm2Measurements.temporaryFile) { request =>
      request.body.moveTo(new File("/tmp/uploaded.bin"))
      Ok("File uploaded")
    }*/


}
