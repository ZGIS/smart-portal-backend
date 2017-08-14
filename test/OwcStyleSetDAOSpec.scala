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

import models.owc._

/**
  * Test Spec for [[OwcStyleSetDAO]] with [[info.smart.models.owc100.OwcStyleSet]]
  */
class OwcStyleSetDAOSpec extends WithDefaultTestFullAppAndDatabase {

  "OwcStyleSetDAO" can {

    val demodata = new DemoData

    val style1 = demodata.style1
    val style2 = demodata.style2
    val style3 = demodata.style3
    val style3_1 = demodata.style3_1

    "create OwcStyleSet with DB" in {
      withTestDatabase { database =>
        database.withTransaction { implicit connection =>
          OwcStyleSetDAO.createOwcStyleSet(style1) must contain(style1)
          OwcStyleSetDAO.findOwcStyleSetByUuid(style1.uuid).get mustEqual style1
        }
        database.withTransaction { implicit connection =>
          OwcStyleSetDAO.createOwcStyleSet(style2) must contain(style2)
          OwcStyleSetDAO.findOwcStyleSetByUuid(style2.uuid).get mustEqual style2
        }
        database.withConnection { implicit connection =>
          OwcStyleSetDAO.getAllOwcStyleSets.size mustBe 2
        }
      }
    }

    "update OwcStyleSet with DB" in {
      withTestDatabase { database =>

        database.withTransaction { implicit connection =>
          OwcStyleSetDAO.createOwcStyleSet(style3) must contain(style3)
          OwcStyleSetDAO.findOwcStyleSetByUuid(style3.uuid) must contain(style3)
        }
        database.withTransaction { implicit connection =>
          OwcStyleSetDAO.createOwcStyleSet(style2) must contain(style2)
          OwcStyleSetDAO.findOwcStyleSetByUuid(style2.uuid).get mustEqual style2
        }
        database.withConnection { implicit connection =>
          OwcStyleSetDAO.findOwcStyleSetByUuid(style3.uuid).size mustBe 1
        }
        database.withTransaction { implicit connection =>
          OwcStyleSetDAO.updateOwcStyleSet(style3_1) must contain(style3_1)
          OwcStyleSetDAO.findOwcStyleSetByUuid(style3_1.uuid) must contain(style3_1)
        }
      }
    }

    "delete OwcStyleSet with DB" in {
      withTestDatabase { database =>
        database.withTransaction { implicit connection =>
          OwcStyleSetDAO.createOwcStyleSet(style3) must contain(style3)
          OwcStyleSetDAO.findOwcStyleSetByUuid(style3.uuid).get mustEqual style3
        }
        database.withTransaction { implicit connection =>
          OwcStyleSetDAO.deleteOwcStyleSet(style3_1) mustBe true
          OwcStyleSetDAO.getAllOwcStyleSets.size mustEqual 0
        }
      }
    }
  }
}
