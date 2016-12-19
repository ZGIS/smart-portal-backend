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


package utils

import java.security.{MessageDigest, SecureRandom}
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.{Inject, Singleton}

import play.api.{Configuration, Logger}

/**
  * provides reasonably current  and secure measures of password hashing as of 2015/2016
  * This Scala implementation of password hashing was inspired by:
  * https://github.com/dholbrook/scala-password-hash
  *
  * the password hash in the database comsists of several fields actually
  *
  * @param configuration
  */
@Singleton
class PasswordHashing @Inject()(configuration: Configuration) {

  val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA1"

  val SALT_BYTE_SIZE = 24
  val HASH_BYTE_SIZE = 24
  val PBKDF2_ITERATIONS = 1000

  val ITERATION_INDEX = 0
  val SALT_INDEX = 1
  val PBKDF2_INDEX = 2

  val DEFAULT_SECRET = "insecure"

  private lazy val appSecret = configuration.getString("play.crypto.secret").getOrElse("insecure")

  Logger.debug("Reading configuration application.secret")

  /**
    * create Session Cookie
    *
    * @param username
    * @param useragent
    * @return
    */
  def createSessionCookie(username: String, useragent: String): String = {
    val in = appSecret + username + useragent.toUpperCase
    val digest = MessageDigest.getInstance("SHA-512").digest(in.getBytes("UTF-8"))
    // Logger.trace(s"createSessionCookie: ${toHex(digest)} app secret: $appSecret , username: $username, useragent: $useragent >>> $in")
    toHex(digest)
  }

  /**
    * test Session Cookie with token, username, uaIdentifier, based on validatePassword
    *
    * @param cookieHash
    * @param username
    * @param useragent
    * @return
    */
  def testSessionCookie(cookieHash: String, username: String, useragent: String): Boolean = {
    val in = appSecret + username + useragent.toUpperCase
    val digestHash = MessageDigest.getInstance("SHA-512").digest(in.getBytes("UTF-8"))
    val testHash = fromHex(cookieHash)
//    Logger.trace(s"testSessionCookie: testhash ${toHex(testHash)} vs newdigest ${toHex(digestHash)}app secret: " +
//      s"$appSecret cookieHash: $cookieHash, username: $username, $useragent: useragent  >>> $in")
    slowEquals(digestHash, testHash)
  }

  /**
    *
    * @param bytes
    * @return
    */
  private def toHex(bytes: Array[Byte]): String = {
    bytes.map("%02X" format _).mkString
  }

  /**
    *
    * @param password
    * @return
    */
  def createHash(password: String): String = {
    createHash(password.toCharArray())
  }

  /**
    *
    * @param password
    * @return
    */
  def createHash(password: Array[Char]): String = {
    val salt = nextRandomSalt
    val hash = pbkdf2(password, salt, PBKDF2_ITERATIONS, HASH_BYTE_SIZE)
    PBKDF2_ITERATIONS + ":" + toHex(salt) + ":" + toHex(hash)
  }

  /**
    *
    * @return
    */
  private def nextRandomSalt(): Array[Byte] = {
    val random = new SecureRandom()
    val salt = Array.ofDim[Byte](SALT_BYTE_SIZE)
    random.nextBytes(salt)
    salt
  }

  /**
    *
    * @param password
    * @param correctHash
    * @return
    */
  def validatePassword(password: String, correctHash: String): Boolean = {
    validatePassword(password.toCharArray(), correctHash)
  }

  /**
    * the work horse...
    *
    * @param password
    * @param correctHash
    * @return
    */
  def validatePassword(password: Array[Char], correctHash: String): Boolean = {
    val params = correctHash.split(":")
    val iterations = Integer.parseInt(params(ITERATION_INDEX))
    val salt = fromHex(params(SALT_INDEX))
    val hash = fromHex(params(PBKDF2_INDEX))
    val testHash = pbkdf2(password, salt, iterations, hash.length)
    slowEquals(hash, testHash)
  }

  /**
    * the other work horse
    *
    * @param a
    * @param b
    * @return
    */
  private def slowEquals(a: Array[Byte], b: Array[Byte]): Boolean = {
    val range = 0 until scala.math.min(a.length, b.length)
    val diff = range.foldLeft(a.length ^ b.length) {
      case (acc, i) => acc | a(i) ^ b(i)
    }
    diff == 0
  }

  /**
    * crypto magic
    *
    * @param password
    * @param salt
    * @param iterations
    * @param bytes
    * @return
    */
  private def pbkdf2(password: Array[Char], salt: Array[Byte], iterations: Int, bytes: Int): Array[Byte] = {
    val spec = new PBEKeySpec(password, salt, iterations, bytes * 8)
    val skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
    skf.generateSecret(spec).getEncoded()
  }

  /**
    *
    * @param hex
    * @return
    */
  private def fromHex(hex: String): Array[Byte] = {
    hex.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
  }

}
