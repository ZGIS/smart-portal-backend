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

import com.typesafe.config.ConfigFactory
import play.api.Configuration
import play.api.db.evolutions.{ClassLoaderEvolutionsReader, Evolutions}
import play.api.db.{Database, Databases}
import utils.ClassnameLogger

/**
  *
  */
trait WithTestDatabase extends ClassnameLogger {

  /**
    *
    * @param block
    * @tparam T
    * @return
    */
  def withTestDatabase[T](block: Database => T): T = {
    val config = new Configuration(ConfigFactory.load("application.test.conf"))

    val driver = config.getString("db.default.driver").get
    val url = config.getString("db.default.url").get
    val username = config.getString("db.default.username").get
    val password = config.getString("db.default.password").get
    val logSql = config.getBoolean("db.default.logSql").get

    logger.info(s"logSql: $logSql")

    Databases.withDatabase(
      driver = driver,
      url = url,
      config = Map(
        "username" -> username,
        "password" -> password,
        "logStatements" -> logSql
      )
    ) { database =>
      Evolutions.withEvolutions(database, ClassLoaderEvolutionsReader.forPrefix("testh2db/")) {
        block(database)
      }
    }
  }
}
