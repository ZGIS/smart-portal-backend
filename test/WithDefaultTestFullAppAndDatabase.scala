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
import org.scalatest.BeforeAndAfter
import org.scalatestplus.play.OneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}

/**
  * default traits mixed in that we use everywhere including application with database evolutions for test h2
  */
trait WithDefaultTestFullAppAndDatabase extends WithDefaultTest with OneAppPerSuite with BeforeAndAfter with WithTestDatabase {

  /**
    * custom application config for testing OneAppPerTest
    */
//  import scala.language.implicitConversions
//  implicit override def newAppForTest(testData: TestData): Application = new
//      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()

  /**
    * custom application config for testing OneAppPerSuite
    */
  override lazy val app: Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build()
}
