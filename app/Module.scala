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

import com.google.inject.AbstractModule
import models.gmd.{MdMetadataCitation, MdMetadataCitationTrait, MdMetadataExtent, MdMetadataExtentTrait}
import models.owc.{OwcDocumentDAO, OwcOfferingDAO, OwcPropertiesDAO}
import models.users.UserDAO
import services.{EmailService, MetadataService}
import utils.PasswordHashing

/**
  * This class is a Guice module that tells Guice how to bind several
  * different types. This Guice module is created when the Play
  * application starts.
  *
  * Play will automatically use any class called `Module` that is in
  * the root package. You can create modules in other locations by
  * adding `play.modules.enabled` settings to the `application.conf`
  * configuration file.
  */
class Module extends AbstractModule {

  override def configure(): Unit = {
    //utils and services
    bind(classOf[PasswordHashing]).asEagerSingleton()
    bind(classOf[EmailService]).asEagerSingleton()
    bind(classOf[MetadataService]).asEagerSingleton()

    //DAOs
    bind(classOf[UserDAO]).asEagerSingleton()
    bind(classOf[OwcPropertiesDAO]).asEagerSingleton()
    bind(classOf[OwcOfferingDAO]).asEagerSingleton()
    bind(classOf[OwcDocumentDAO]).asEagerSingleton()

    //compagnion objects to inject objects into (see http://michaelpnash.github.io/guice-up-your-scala/)
    bind(classOf[MdMetadataCitationTrait]).toInstance(MdMetadataCitation)
    bind(classOf[MdMetadataExtentTrait]).toInstance(MdMetadataExtent)
  }
}
