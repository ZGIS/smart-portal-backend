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

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID

import models.db.SessionHolder
import models.owc.OwcContextDAO
import models.users._
import utils.PasswordHashing

/**
  * Test Spec for [[UserGroup]] and [[UserGroupUsersLevel]] and [[UserGroupContextsVisibility]]
  */
class UserGroupSpec extends WithDefaultTestFullAppAndDatabase {

  "UserGroup" can {

    val demodata = new DemoData
    val owcContext1 = demodata.owcContext1
    val owcContext2 = demodata.owcContext2

    "handle UserGroup with DB" in {
      withTestDatabase { database =>

        val sessionHolder = new SessionHolder(database)
        val passwordHashing = new PasswordHashing(app.configuration)

        val testTime = ZonedDateTime.now.withZoneSameInstant(ZoneId.systemDefault())
        val testUser1 = demodata.testUser1(passwordHashing.createHash("testpass123"))
        val testUser3 = demodata.testUser3(passwordHashing.createHash("testpass123"))

        sessionHolder.viaTransaction { implicit connection =>
          UserDAO.createUser(testUser1) must contain(testUser1)
          UserDAO.createUser(testUser3) must contain(testUser3)
          OwcContextDAO.createOwcContext(owcContext1, testUser1, 1, "CUSTOM") must contain(owcContext1)
          OwcContextDAO.createOwcContext(owcContext2, testUser3, 1, "CUSTOM") must contain(owcContext2)
        }

        // testUser1 owns a custom collection, he will enable visible to group level by raising to 1 (0 private, 1 group, 2 public)
        sessionHolder.viaConnection { implicit connection =>
          OwcContextDAO.findOwcContextByIdAndUser(owcContext1.id, testUser1) must contain(owcContext1)
        }

        // new group is created, basically empty, the user that creates the group will be made first member and power-user
        val sacTeam = UserGroup(uuid = UUID.randomUUID(),
          name = "SAC Project",
          shortinfo = "Researchers in the SAC Project",
          laststatustoken = "created",
          laststatuschange = testTime,
          hasUsersLevel = List[UserGroupUsersLevel](),
          hasOwcContextsVisibility = List[UserGroupContextsVisibility]())

        sessionHolder.viaTransaction { implicit connection =>
          UserGroup.createUserGroup(sacTeam) must contain(sacTeam)
        }

        sessionHolder.viaConnection { implicit connection =>
          UserGroup.findUserGroupsById(sacTeam.uuid) must contain(sacTeam)
        }

        // testUser1 becomes power user
        val gl1 = UserGroupUsersLevel(usergroups_uuid = sacTeam.uuid, users_accountsubject = testUser1.accountSubject, userlevel = 1)
        val sacTeamGl1 = sacTeam.copy(hasUsersLevel = List(gl1))
        sessionHolder.viaTransaction { implicit connection =>
          UserGroup.updateUserGroup(sacTeamGl1) must contain(sacTeamGl1)
        }

        sessionHolder.viaConnection { implicit connection =>
          UserGroup.findUserGroupsById(sacTeam.uuid) must contain(sacTeamGl1)
        }

        // testUser3 becomes member in order to be able to see the owcContext1 of testUser1
        val gl2 = UserGroupUsersLevel(usergroups_uuid = sacTeam.uuid, users_accountsubject = testUser3.accountSubject, userlevel = 0)
        val sacTeamGl2 = sacTeam.copy(hasUsersLevel = List(gl1, gl2))

        sessionHolder.viaTransaction { implicit connection =>
          UserGroup.updateUserGroup(sacTeamGl2) must contain(sacTeamGl2)
        }

        sessionHolder.viaConnection { implicit connection =>
          UserGroup.findUserGroupsById(sacTeam.uuid) must contain(sacTeamGl2)
        }

        // testUser3 becomes power-user in order to be able to edit the owcContext1 of testUser1
        val gl2_1 = UserGroupUsersLevel(usergroups_uuid = sacTeam.uuid, users_accountsubject = testUser3.accountSubject, userlevel = 1)
        val sacTeamGl2_1 = sacTeam.copy(hasUsersLevel = List(gl1, gl2_1))

        sessionHolder.viaTransaction { implicit connection =>
          UserGroup.updateUserGroup(sacTeamGl2_1) must contain(sacTeamGl2_1)
        }

        sessionHolder.viaConnection { implicit connection =>
          UserGroup.findUserGroupsById(sacTeam.uuid) must contain(sacTeamGl2_1)
        }

        // and check if remove works
        sessionHolder.viaTransaction { implicit connection =>
          UserGroup.updateUserGroup(sacTeamGl1) must contain(sacTeamGl1)
        }

        sessionHolder.viaConnection { implicit connection =>
          UserGroup.findUserGroupsById(sacTeam.uuid) must contain(sacTeamGl1)
        }

        // Context Visibility

        // context1 is made available to the team
        val cv1 = UserGroupContextsVisibility(usergroups_uuid = sacTeam.uuid, owc_context_id = owcContext1.id.toString, visibility = 1)
        val sacTeamcv1 = sacTeam.copy(hasOwcContextsVisibility = List(cv1))
        sessionHolder.viaTransaction { implicit connection =>
          UserGroup.updateUserGroup(sacTeamcv1) must contain(sacTeamcv1)
        }

        sessionHolder.viaConnection { implicit connection =>
          UserGroup.findUserGroupsById(sacTeam.uuid) must contain(sacTeamcv1)
        }


        // context2 is made available to the team
        val cv2 = UserGroupContextsVisibility(usergroups_uuid = sacTeam.uuid, owc_context_id = owcContext2.id.toString, visibility = 1)
        val sacTeamcv2 = sacTeam.copy(hasOwcContextsVisibility = List(cv1, cv2))

        sessionHolder.viaTransaction { implicit connection =>
          UserGroup.updateUserGroup(sacTeamcv2) must contain(sacTeamcv2)
        }

        sessionHolder.viaConnection { implicit connection =>
          UserGroup.findUserGroupsById(sacTeam.uuid) must contain(sacTeamcv2)
        }

        // context2 increased visibility to the team in order to be able to edit by team
        val cv2_1 = UserGroupContextsVisibility(usergroups_uuid = sacTeam.uuid, owc_context_id = owcContext2.id.toString, visibility = 2)
        val sacTeamcv2_1 = sacTeam.copy(hasOwcContextsVisibility = List(cv1, cv2_1))

        sessionHolder.viaTransaction { implicit connection =>
          UserGroup.updateUserGroup(sacTeamcv2_1) must contain(sacTeamcv2_1)
        }

        sessionHolder.viaConnection { implicit connection =>
          UserGroup.findUserGroupsById(sacTeam.uuid) must contain(sacTeamcv2_1)
        }

        // and check if remove works
        sessionHolder.viaTransaction { implicit connection =>
          UserGroup.updateUserGroup(sacTeamcv1) must contain(sacTeamcv1)
        }

        sessionHolder.viaConnection { implicit connection =>
          UserGroup.findUserGroupsById(sacTeam.uuid) must contain(sacTeamcv1)
        }
      }
    }
  }
}
