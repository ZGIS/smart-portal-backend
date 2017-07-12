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

import java.io.InvalidClassException

import info.smart.models.owc100.{OwcContext, OwcLink, OwcResource}

object OwcGeoJsonFixes {

  /**
    * There is a bug in the Owc GeoJson Scala Play JSON lib, that tags an undefined rel default as "alternate".
    * Actually we don't need the rel attribute immediately here JSON, and we now store by uuid and can easily distinguish which
    * OwcLink goes into what List (contentByRef, ResourceMetadata and similar), but for e.g. uploaded file properties
    * we search for the "enclosure" (aka data) rel and for a subsequent parallel support of ATOM encoding we also need to keep the rels proper
    *
    * from [[OwcLink]] verifyingKnownRelationsReads -> knownRelations = List("profile", "via", "alternate", "icon", "enclosure")
    *
    * https://github.com/ZGIS/smart-owc-geojson/issues/7
    *
    * @param owcUnfixed [[OwcContext]] or [[OwcResource]]
    * @tparam A [[OwcContext]] or [[OwcResource]]
    * @return
    */
  def fixRelPropertyForOwcLinks[A](owcUnfixed: A): A = {

    owcUnfixed match {
      case owcUnfixed: OwcContext => {
        val specReference = owcUnfixed.specReference.map(o => changeRelForOwcLink(o, "profile")) // links.profiles[] and rel=profile
        val contextMetadata = owcUnfixed.contextMetadata.map(o => changeRelForOwcLink(o, "via")) // aka links.via[] & rel=via
        owcUnfixed.copy(specReference = specReference, contextMetadata = contextMetadata).asInstanceOf[A]
      }
      case owcUnfixed: OwcResource => {
        val contentDescription = owcUnfixed.contentDescription.map(o => changeRelForOwcLink(o, "alternate")) // links.alternates[] and rel=alternate
        val preview = owcUnfixed.preview.map(o => changeRelForOwcLink(o, "icon")) // aka links.previews[] and rel=icon (atom)
        val contentByRef = owcUnfixed.contentByRef.map(o => changeRelForOwcLink(o, "enclosure")) // aka links.data[] and rel=enclosure (atom)
        val resourceMetadata = owcUnfixed.resourceMetadata.map(o => changeRelForOwcLink(o, "via")) // aka links.via[] & rel=via
        owcUnfixed.copy(contentDescription = contentDescription, preview = preview,
          contentByRef = contentByRef, resourceMetadata = resourceMetadata).asInstanceOf[A]
      }
      case _ => throw new InvalidClassException(s"The parameter type ${owcUnfixed.getClass.getCanonicalName} not supported here")
    }
  }

  /**
    * changes the rel property of an OwcLink into specified one
    *
    * @param owcLink
    * @param newRel
    * @return
    */
  private def changeRelForOwcLink(owcLink: OwcLink, newRel: String): OwcLink = {
    owcLink.copy(rel = newRel)
  }
}
