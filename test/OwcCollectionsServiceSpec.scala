import models.db.DatabaseSessionHolder
import org.locationtech.spatial4j.shape.Rectangle
import org.specs2.mock.Mockito
import services.{OwcCollectionsService, PortalConfig, UserGroupService}

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

class OwcCollectionsServiceSpec extends WithDefaultTest with Mockito {

  /*

  getUserDefaultOwcContext(authUser: String): Option[OwcContext]

  getOwcLinksForOwcAuthorOwnFiles(authUser: String): Seq[OwcLink]

  getOwcContextsForUserAndId(authUserOption: Option[String], owcContextIdOption: Option[String]): Seq[OwcContext]

   */

  "OwcCollectionsService" can {

    val demodata = new DemoData

    lazy val ctx = demodata.ctx
    lazy val baseEmpty: Option[Rectangle] = None
    lazy val baseSome: Rectangle = ctx.getShapeFactory().rect(-150.0, 150.0, -70.0, 70.0)
    lazy val smaller1: Rectangle = ctx.getShapeFactory().rect(-170.0, 170.0, -90.0, 90.0)
    lazy val smaller2: Rectangle = ctx.getShapeFactory().rect(-180.0, 180.0, -80.0, 80.0)

    "update areaOfInterest in OwcContext" in {

      val mockDbSession = mock[DatabaseSessionHolder]
      val portalConfig = mock[PortalConfig]
      val userGroupService = mock[UserGroupService]

      val collectionsService = new OwcCollectionsService(
        mockDbSession,
        portalConfig,
        userGroupService
      )

      val res1 = demodata.owcResource1.copy(geospatialExtent = Some(smaller1))
      val res2 = demodata.owcResource2.copy(geospatialExtent = Some(smaller2))
      val res3 = demodata.owcResource1.copy(geospatialExtent = None)
      val res4 = demodata.owcResource2.copy(geospatialExtent = None)
      val owc1 = demodata.owcContext1.copy(areaOfInterest = baseEmpty, resource = List(res1, res2))
      val owc2 = demodata.owcContext1.copy(areaOfInterest = Some(baseSome), resource = List(res1, res2))
      val owc3 = demodata.owcContext1.copy(areaOfInterest = baseEmpty, resource = List(res3, res4))
      val owc4 = demodata.owcContext1.copy(areaOfInterest = baseEmpty, resource = List())

      val rect1 = collectionsService.calculateBBoxForCollection(owc1)
      val rect2 = collectionsService.calculateBBoxForCollection(owc2)
      val rect3 = collectionsService.calculateBBoxForCollection(owc3)
      val rect4 = collectionsService.calculateBBoxForCollection(owc4)

      rect1 mustEqual Some(demodata.world)
      rect2 mustEqual Some(demodata.world)
      rect3 mustBe None
      rect4 mustBe None
    }
  }
}
