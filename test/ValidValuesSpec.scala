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

import models.metadata.ValidValues

/**
  * Specification test for {@link ValidValues}
  */
class ValidValuesSpec extends WithDefaultTest {

  "Instantiation " should {
    "succeed on values list + standard value" in {
      new ValidValues(0, List("test", "test2"), None)
    }

    "succeed on values list + descriptions + standard value" in {
      new ValidValues(0, List("test", "test2"), Some(List("Description", "Description 2")))
    }

    "fail on empty values list" in {
      val thrown = intercept[IllegalArgumentException] {
        new ValidValues(0, List(), None)
      }
      thrown.getMessage mustEqual "requirement failed: values list must not be empty"
    }

    "fail on values and descriptions list of different size" in {
      val thrown = intercept[IllegalArgumentException] {
        new ValidValues(0, List("test"), Some(List("Description", "Description 2")))
      }
      thrown.getMessage mustEqual "requirement failed: decriptions list must either be None or same length as values list"
    }

    "fail on standardValue too big" in {
      val thrown = intercept[IllegalArgumentException] {
        new ValidValues(4, List("test", "test2"), Some(List("Description", "Description 2")))
      }
      thrown.getMessage mustEqual "requirement failed: standardValue must be within values list length"
    }

    "fail on standardValue too small" in {
      val thrown = intercept[IllegalArgumentException] {
        new ValidValues(-4, List("test", "test2"), Some(List("Description", "Description 2")))
      }
      thrown.getMessage mustEqual "requirement failed: standardValue must be within values list length"
    }

    "fail on non-unique values" in{
      val thrown = intercept[IllegalArgumentException] {
        new ValidValues(1, List("test", "test2", "test"), None)
      }
      thrown.getMessage mustEqual "requirement failed: all values must be unique"
    }

    "fail on non-unique descriptions" in{
      val thrown = intercept[IllegalArgumentException] {
        new ValidValues(1, List("test", "test2", "test3"), Some(List("Description", "Description", "Description 3")))
      }
      thrown.getMessage mustEqual "requirement failed: all descriptions must be unique"
    }
  }

}
