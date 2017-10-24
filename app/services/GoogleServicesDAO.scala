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

import java.io.{File, FileReader}
import javax.inject.{Inject, Singleton}

import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeTokenRequest, GoogleClientSecrets, GoogleTokenResponse}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.cloud.storage._
import com.google.common.io.{Files => GoogleFiles}
import models.ErrorResult
import models.users.GAuthCredentials
import play.api.Configuration
import utils.ClassnameLogger

import scala.util.{Failure, Success, Try}

trait AbstractCloudServiceDAO {

  val configuration: Configuration

  // def withProviderClientSecret[T](block: ProviderClientSecret => T): T

  // def uploadFileProvider(fileHandle: File): Either[ErrorResult, ProviderResult]

  // def getProviderAuthorization(authCredentials: AuthCredentials): Either[ErrorResult, ProviderAuthResponse]
}
/**
  * provide Google
  * @param configuration
  */
@Singleton
class GoogleServicesDAO @Inject()(val configuration: Configuration) extends AbstractCloudServiceDAO with ClassnameLogger {

  val googleClientSecretFile: String = configuration.getString("google.client.secret")
    .getOrElse("client_secret.json")
  val googleStorageBucket: String = configuration.getString("google.storage.bucket")
    .getOrElse("smart-backup")
  val googleProjectId: String = configuration.getString("google.project.id")
    .getOrElse("dynamic-cove-129211")

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
    * upload a file to a google cloud bucket
    *
    * @param fileHandle
    * @return
    */
  def uploadFileGoogleBucket(fileHandle: File): Either[ErrorResult, Blob] = {
    // Google Java upload stuff
    import scala.collection.JavaConverters._

    // val storage: Storage = authenticatedStorageOptions.getService()
    val storage: Storage = StorageOptions.getDefaultInstance().getService()
    // val noCredentials = NoCredentials.getInstance()

    // Modify access list to allow all users with link to read file
    val roAcl = Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER)
    val acls: java.util.List[Acl] = List(roAcl).asJava

    // the inputstream is closed by default, so we don't need to close it here
    val blobTry: Try[Blob] = Try {
      storage.create(BlobInfo.newBuilder(googleStorageBucket, fileHandle.getName).setAcl(acls).build(),
        GoogleFiles.asByteSource(fileHandle).openStream())
    }

    blobTry match {
      case Success(blob) => Right(blob)
      case Failure(ex) => Left(ErrorResult("Blob creation in cloud storage failed.", Some(ex.getLocalizedMessage)))
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
