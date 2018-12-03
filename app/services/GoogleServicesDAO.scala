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

package services

import java.io.{File, FileInputStream, FileReader}
import java.nio.ByteBuffer
import java.time.{LocalDateTime, ZoneOffset}

import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeTokenRequest, GoogleClientSecrets, GoogleTokenResponse}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage._
import com.google.common.io.{Files => GoogleFiles}
import javax.inject.{Inject, Singleton}
import models.ErrorResult
import models.users.GAuthCredentials
import play.api.libs.functional.syntax._
import play.api.libs.json.Writes._
import play.api.libs.json._
import utils.ClassnameLogger

import scala.util.{Failure, Success, Try}

final case class LocalBlobInfo(name: String,
                         mediaLink: String,
                         bucket: String,
                         contentType: String,
                         contentEncoding: String,
                         size: Long,
                         md5: String,
                         createTime: LocalDateTime
                        ) extends ClassnameLogger {

  def toJson: JsValue = Json.toJson(this)
}

object LocalBlobInfo {

  implicit val googleBlobWrites: Writes[LocalBlobInfo] = (
    (JsPath \ "name").write[String] and
      (JsPath \ "mediaLink").write[String] and
      (JsPath \ "bucket").write[String] and
      (JsPath \ "contentType").write[String] and
      (JsPath \ "contentEncoding").write[String] and
      (JsPath \ "size").write[Long] and
      (JsPath \ "md5").write[String] and
      (JsPath \ "createTime").write[LocalDateTime]) (unlift(LocalBlobInfo.unapply))

  def newFrom(blob: Blob): LocalBlobInfo = {
    LocalBlobInfo(blob.getName,
      blob.getMediaLink,
      blob.getBucket,
      blob.getContentType,
      blob.getContentEncoding,
      blob.getSize,
      blob.getMd5,
      LocalDateTime.ofEpochSecond(blob.getCreateTime, 0, ZoneOffset.UTC)
    )
  }
}


trait AbstractCloudServiceDAO {

  val portalConfig: PortalConfig

  // def withProviderClientSecret[T](block: ProviderClientSecret => T): T

  // def uploadFileProvider(fileHandle: File): Either[ErrorResult, ProviderResult]

  // def getProviderAuthorization(authCredentials: AuthCredentials): Either[ErrorResult, ProviderAuthResponse]
}

/**
  * provide Google
  *
  * @param configuration
  */
@Singleton
class GoogleServicesDAO @Inject()(val portalConfig: PortalConfig) extends AbstractCloudServiceDAO with ClassnameLogger {

  val googleClientSecretFile: String = portalConfig.googleClientSecretFile
  val googleServiceAccountSecretFile: String = portalConfig.googleServiceAccountSecretFile
  val googleStorageBucket: String = portalConfig.googleStorageBucket
  val googleProjectId: String = portalConfig.googleProjectId

  val gAuthRedirectUrl = "postmessage"

  /**
    * enclosing helper for client secret usage if needed
    *
    * @param block
    * @tparam T
    * @return
    */
  def withProviderClientSecret[T](block: GoogleClientSecrets => T): T = {

    if (!new java.io.File(googleClientSecretFile).exists) {
      logger.error("Service JSON file not available")
      throw new IllegalArgumentException(s"Value $googleClientSecretFile is not a valid GoogleClientSecrets file")
    } else {
      // basic precondition for Google OAuth2 stuff
      val googleClientSecret = GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(), new FileReader(googleClientSecretFile))

      block(googleClientSecret)
    }
  }

  /**
    * enclosing helper for serviceaccount secret usage if needed
    *
    * @param block
    * @tparam T
    * @return
    */
  def withServiceAccountStorageOptions[T](block: StorageOptions => T): T = {

    if (!new java.io.File(googleServiceAccountSecretFile).exists) {
      logger.warn("Service Account JSON file not available")
      val storageOptions: StorageOptions = StorageOptions.getDefaultInstance()
      block(storageOptions)
    } else {
      // basic precondition for Google OAuth2 stuff
      // val googleClientSecret = GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(), new FileReader(googleClientSecretFile))

      // service account json for GoogleServicesDAO
      val storageOptions: StorageOptions = StorageOptions.newBuilder()
        .setCredentials(ServiceAccountCredentials.fromStream(new FileInputStream(googleServiceAccountSecretFile)))
        .build()

      block(storageOptions)
    }
  }

  /**
    * upload a file to a google cloud bucket
    *
    * @param fileHandle
    * @return
    */
  def uploadFileGoogleBucket(fileHandle: File): Either[ErrorResult, LocalBlobInfo] = {
    withServiceAccountStorageOptions { storageOptions =>
    // Google Java upload stuff
    import scala.collection.JavaConverters._

    // val storage: Storage = authenticatedStorageOptions.getService()
    val storage: Storage = storageOptions.getService()
    // val noCredentials = NoCredentials.getInstance()

    // Modify access list to allow all users with link to read file
    val roAcl = Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER)
    val acls: java.util.List[Acl] = List(roAcl).asJava

    // the inputstream is closed by default, so we don't need to close it here
    val blobTry: Try[LocalBlobInfo] = Try {
      val blob = storage.create(BlobInfo.newBuilder(googleStorageBucket, fileHandle.getName).setAcl(acls).build(),
        GoogleFiles.asByteSource(fileHandle).openStream())
      LocalBlobInfo.newFrom(blob)
    }

    blobTry match {
      case Success(blobInfo) => Right(blobInfo)
      case Failure(ex) =>
        logger.error(s"Blob creation in cloud storage failed. ${ex.getLocalizedMessage}")
        Left(ErrorResult("Blob creation in cloud storage failed.", Some(ex.getLocalizedMessage)))
    }

  }
  }

  /**
    *
    * @param vocabString the vocabString
    * @param pathPrefix sub directory where the file should go /prefix/prefix no trailing slash
    * @return
    */
  def updateVocabFileGoogleBucket(vocabString: String, fileName: String, pathPrefix: String): Either[ErrorResult, LocalBlobInfo] = {
    withServiceAccountStorageOptions { storageOptions =>

    // Google Java upload stuff
    import java.nio.charset.StandardCharsets.UTF_8

    // val storage: Storage = authenticatedStorageOptions.getService()
    val storage: Storage = storageOptions.getService()
    // val noCredentials = NoCredentials.getInstance()

    val blobTry: Try[LocalBlobInfo] = Try {
      val blobId = BlobId.of(googleStorageBucket, pathPrefix + "/" + fileName)
      val blob = storage.get(blobId)
      if (blob != null) {
        val channel = blob.writer()
        channel.write(ByteBuffer.wrap(vocabString.getBytes(UTF_8)))
        channel.close()
        LocalBlobInfo.newFrom(blob)
      } else {
        throw new IllegalArgumentException("original blob for file could not be retrieved.")
      }
    }

    blobTry match {
      case Success(blobInfo) => Right(blobInfo)
      case Failure(ex) =>
        logger.error(s"Blob update in cloud storage failed. ${ex.getLocalizedMessage}")
        Left(ErrorResult("Blob update in cloud storage failed.", Some(ex.getLocalizedMessage)))
    }

  }
  }

  /**
    * humble try of listing the buckets content for admin
    *
    * @return
    */
  def listbucket: Either[ErrorResult, Seq[LocalBlobInfo]] = {
    withServiceAccountStorageOptions { storageOptions =>

    val storage: Storage = storageOptions.getService()
    val bucket = storage.get(googleStorageBucket)

    import scala.collection.JavaConverters._

    val blobTry = Try {
      bucket.list().iterateAll().asScala.filter(blob => blob != null ).map{
        blob => LocalBlobInfo.newFrom(blob)
      }.toSeq
    }
    blobTry match {
      case Success(blobSeq) => Right(blobSeq)
      case Failure(ex) =>
        logger.error(s"Blob listing of cloud storage failed. ${ex.getLocalizedMessage}")
        Left(ErrorResult("Blob listing of cloud storage failed.", Some(ex.getLocalizedMessage)))
    }
  }
  }

  /**
    * find a specific blob
    *
    * @param fileName
    * @return
    */
  def getFileBlob(fileName: String): Either[ErrorResult, LocalBlobInfo] = {
    withServiceAccountStorageOptions { storageOptions =>

    val storage: Storage = storageOptions.getService()
    val bucket = storage.get(googleStorageBucket)
    val blobTry = Try {
      bucket.get(fileName)
    }
    blobTry match {
      case Success(blob) if blob != null => Right(LocalBlobInfo.newFrom(blob))
      case Success(blob) if blob == null =>
        logger.error(s"Blob for $fileName retrieve from cloud storage returns file not found.")
        Left(ErrorResult(s"Blob for $fileName retrieve from cloud storage returns file not found.", Some("Blob is NULL")))
      case Failure(ex) =>
        logger.error(s"Blob retrieve from cloud storage failed. ${ex.getLocalizedMessage}")
        Left(ErrorResult("Blob retrieve from cloud storage failed.", Some(ex.getLocalizedMessage)))
    }
  }
  }

  /**
    * delete a file in the bucket
    *
    * @param fileName
    * @return
    */
  def deleteFileBlob(fileName: String): scala.Option[ErrorResult] = {
    withServiceAccountStorageOptions { storageOptions =>

    val storage: Storage = storageOptions.getService()
    val bucket = storage.get(googleStorageBucket)
    val blobTry = Try {
      bucket.get(fileName).delete()
    }
    blobTry match {
      case Success(blobDel) =>
        if (blobDel) {
          None
        } else {
          logger.error("Blob delete from cloud storage failed. No details here.")
          Some(ErrorResult("Blob delete from cloud storage failed.", Some(s"$fileName not found.")))
        }
      case Failure(ex) =>
        logger.error(s"Blob delete from cloud storage failed. ${ex.getLocalizedMessage}")
        Some(ErrorResult("Blob delete from cloud storage failed.", Some(ex.getLocalizedMessage)))
    }
  }
  }

  /**
    * do google oauth operation
    *
    * @param gAuthCredentials
    * @return
    */
  def getGoogleAuthorization(gAuthCredentials: GAuthCredentials): Either[ErrorResult, GoogleTokenResponse] = {
    withProviderClientSecret { googleClientSecret =>

      // 988846878323-bkja0j1tgep5ojthfr2e92ao8n7iksab.apps.googleusercontent.com
      val googleClientID = googleClientSecret.getDetails().getClientId()
      logger.debug(s"clientId $googleClientID")

      // Specify the same redirect URI that you use with your web
      // app. If you don't have a web version of your app, you can
      // specify an empty string.
      val tokenTry: Try[GoogleTokenResponse] = Try {
        new GoogleAuthorizationCodeTokenRequest(
          new NetHttpTransport(),
          JacksonFactory.getDefaultInstance(),
          "https://www.googleapis.com/oauth2/v4/token",
          googleClientID,
          googleClientSecret.getDetails().getClientSecret(),
          gAuthCredentials.authcode,
          gAuthRedirectUrl).execute()
      }

      tokenTry match {
        case Success(tokenResponse) => Right(tokenResponse)
        case Failure(ex) => Left(ErrorResult("Google OAuth Access failed.", Some(ex.getLocalizedMessage)))
      }
    }
  }
}
