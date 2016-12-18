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

package models

import java.time.ZonedDateTime
import java.util.UUID

import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.io.ShapeIO
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.libs.json.{JsValue, _}
import org.locationtech.spatial4j.shape.{Point, Rectangle}
import play.api.data.validation.ValidationError
import utils.ClassnameLogger

package object owc {

  implicit val tableOwcDocumentsHasOwcEntries = "owc_feature_types_as_document_has_owc_entries"
  implicit val tableOwcEntriesHasOwcOfferings = "owc_feature_types_as_entry_has_owc_offerings"
  implicit val tableOwcFeatureTypesHasOwcProperties = "owc_feature_types_has_owc_properties"
  implicit val tableOwcPropertiesHasOwcAuthors = "owc_properties_has_owc_authors"
  implicit val tableOwcPropertiesHasOwcAuthorsAsContributors = "owc_properties_has_owc_authors_as_contributors"
  implicit val tableOwcPropertiesHasOwcCategories = "owc_properties_has_owc_categories"
  implicit val tableOwcPropertiesHasOwcLinks = "owc_properties_has_owc_links"
  implicit val tableOwcOfferingsHasOwcOperations = "owc_offerings_has_owc_operations"

  implicit val tableOwcOperations = "owc_operations"
  implicit val tableOwcOfferings = "owc_offerings"
  implicit val tableOwcProperties = "owc_properties"
  implicit val tableOwcLinks = "owc_links"
  implicit val tableOwcCategories = "owc_categories"
  implicit val tableOwcAuthors = "owc_authors"
  implicit val tableOwcFeatureTypes = "owc_feature_types"

  /**
    * OwcAuthor Json stuff
    */
  object OwcAuthorJs {

    /**
      *
      * @param name
      * @param email
      * @param uri
      * @return
      */
    def apply(name: String, email: Option[String], uri: Option[String]): OwcAuthor = {
      // Todo, we might find a way to find an existing UUID from DB if entry exists
      val uuid = UUID.randomUUID()
      OwcAuthor(uuid, name, email, uri)
    }

    /**
      *
      * @param arg
      * @return
      */
    def unapply(arg: OwcAuthor): Option[(String, Option[String], Option[String])] = {
      Some(arg.name, arg.email, arg.uri)
    }
  }

  implicit val owcAuthorReads: Reads[OwcAuthor] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "email").readNullable[String] and
      (JsPath \ "uri").readNullable[String]
    ) (OwcAuthorJs.apply _)

  val owcAuthorWrites: Writes[OwcAuthor] = (
    (JsPath \ "name").write[String] and
      (JsPath \ "email").writeNullable[String] and
      (JsPath \ "uri").writeNullable[String]
    ) (unlift(OwcAuthorJs.unapply))

  implicit val owcAuthorFormat: Format[OwcAuthor] =
    Format(owcAuthorReads, owcAuthorWrites)

  /**
    * OwcCategory Json stuff
    */
  object OwcCategoryJs {

    /**
      *
      * @param scheme
      * @param term
      * @param label
      * @return
      */
    def apply(scheme: String, term: String, label: Option[String]): OwcCategory = {
      // Todo, we might find a way to find an existing UUID from DB if entry exists
      val uuid = UUID.randomUUID()
      OwcCategory(uuid, scheme, term, label)
    }

    /**
      *
      * @param arg
      * @return
      */
    def unapply(arg: OwcCategory): Option[(String, String, Option[String])] = {
      Some(arg.scheme, arg.term, arg.label)
    }
  }

  implicit val owcCategoryReads: Reads[OwcCategory] = (
    (JsPath \ "scheme").read[String] and
      (JsPath \ "term").read[String] and
      (JsPath \ "label").readNullable[String]
    ) (OwcCategoryJs.apply _)

  implicit val owcCategoryWrites: Writes[OwcCategory] = (
    (JsPath \ "scheme").write[String] and
      (JsPath \ "term").write[String] and
      (JsPath \ "label").writeNullable[String]
    ) (unlift(OwcCategoryJs.unapply))

  implicit val owcCategoryFormat: Format[OwcCategory] =
    Format(owcCategoryReads, owcCategoryWrites)

  /**
    * OwcLink Json stuff
    */
  object OwcLinkJs {

    /**
      *
      * @param rel
      * @param mimeType
      * @param href
      * @param title
      * @return
      */
    def apply(rel: String, mimeType: Option[String], href: String, title: Option[String]): OwcLink = {
      // Todo, we might find a way to find an existing UUID from DB if entry exists
      val uuid = UUID.randomUUID()
      OwcLink(uuid, rel, mimeType, href, title)
    }

    /**
      *
      * @param arg
      * @return
      */
    def unapply(arg: OwcLink): Option[(String, Option[String], String, Option[String])] = {
      Some(arg.rel, arg.mimeType, arg.href, arg.title)
    }
  }

  implicit val owcLinkReads: Reads[OwcLink] = (
    (JsPath \ "rel").read[String] and
      (JsPath \ "type").readNullable[String] and
      (JsPath \ "href").read[String] and
      (JsPath \ "title").readNullable[String]
    ) (OwcLinkJs.apply _)

  implicit val owcLinkWrites: Writes[OwcLink] = (
    (JsPath \ "rel").write[String] and
      (JsPath \ "type").writeNullable[String] and
      (JsPath \ "href").write[String] and
      (JsPath \ "title").writeNullable[String]
    ) (unlift(OwcLinkJs.unapply))

  implicit val owcLinkFormat: Format[OwcLink] =
    Format(owcLinkReads, owcLinkWrites)

  /**
    * OwcProperties Json stuff
    */
  object OwcPropertiesJs {

    /**
      *
      * @param language
      * @param title
      * @param subtitle
      * @param updated
      * @param generator
      * @param rights
      * @param authors
      * @param contributors
      * @param creator
      * @param publisher
      * @param categories
      * @param links
      * @return
      */
    def apply(language: String,
              title: String,
              subtitle: Option[String],
              updated: Option[ZonedDateTime],
              generator: Option[String],
              rights: Option[String],
              authors: List[OwcAuthor],
              contributors: List[OwcAuthor],
              creator: Option[String],
              publisher: Option[String],
              categories: List[OwcCategory],
              links: List[OwcLink]): OwcProperties = {
      // Todo, we might find a way to find an existing UUID from DB if entry exists
      val uuid = UUID.randomUUID()
      OwcProperties(uuid,
        language,
        title,
        subtitle,
        updated,
        generator,
        rights,
        authors,
        contributors,
        creator,
        publisher,
        categories,
        links)
    }

    /**
      *
      * @param arg
      * @return
      */
    def unapply(arg: OwcProperties): Option[(String,
      String,
      Option[String],
      Option[ZonedDateTime],
      Option[String],
      Option[String],
      List[OwcAuthor],
      List[OwcAuthor],
      Option[String],
      Option[String],
      List[OwcCategory],
      List[OwcLink])] = {
      Some(arg.language,
        arg.title,
        arg.subtitle,
        arg.updated,
        arg.generator,
        arg.rights,
        arg.authors,
        arg.contributors,
        arg.creator,
        arg.publisher,
        arg.categories,
        arg.links)
    }
  }

  implicit val owcPropertiesReads: Reads[OwcProperties] = (
    (JsPath \ "lang").read[String] and
      (JsPath \ "title").read[String] and
      (JsPath \ "subtitle").readNullable[String] and
      (JsPath \ "updated").readNullable[ZonedDateTime] and
      (JsPath \ "generator").readNullable[String] and
      (JsPath \ "rights").readNullable[String] and
      (JsPath \ "authors").read[List[OwcAuthor]] and
      (JsPath \ "contributors").read[List[OwcAuthor]] and
      (JsPath \ "creator").readNullable[String] and
      (JsPath \ "publisher").readNullable[String] and
      (JsPath \ "categories").read[List[OwcCategory]] and
      (JsPath \ "links").read[List[OwcLink]]
    ) (OwcPropertiesJs.apply _)

  implicit val owcPropertiesWrites: Writes[OwcProperties] = (
    (JsPath \ "lang").write[String] and
      (JsPath \ "title").write[String] and
      (JsPath \ "subtitle").writeNullable[String] and
      (JsPath \ "updated").writeNullable[ZonedDateTime] and
      (JsPath \ "generator").writeNullable[String] and
      (JsPath \ "rights").writeNullable[String] and
      (JsPath \ "authors").write[List[OwcAuthor]] and
      (JsPath \ "contributors").write[List[OwcAuthor]] and
      (JsPath \ "creator").writeNullable[String] and
      (JsPath \ "publisher").writeNullable[String] and
      (JsPath \ "categories").write[List[OwcCategory]] and
      (JsPath \ "links").write[List[OwcLink]]
    ) (unlift(OwcPropertiesJs.unapply))

  implicit val owcPropertiesFormat: Format[OwcProperties] =
    Format(owcPropertiesReads, owcPropertiesWrites)

  /**
    * OwcPostRequestConfig Json stuff
    */
  implicit val owcPostRequestConfigReads: Reads[OwcPostRequestConfig] = (
    (JsPath \ "type").readNullable[String] and
      (JsPath \ "request").readNullable[String]
    ) (OwcPostRequestConfig.apply _)

  implicit val owcPostRequestConfigWrites: Writes[OwcPostRequestConfig] = (
    (JsPath \ "type").writeNullable[String] and
      (JsPath \ "request").writeNullable[String]
    ) (unlift(OwcPostRequestConfig.unapply))

  implicit val owcPostRequestConfigFormat: Format[OwcPostRequestConfig] =
    Format(owcPostRequestConfigReads, owcPostRequestConfigWrites)

  /**
    * OwcRequestResult Json stuff
    */
  implicit val owcRequestResultReads: Reads[OwcRequestResult] = (
    (JsPath \ "contentType").readNullable[String] and
      (JsPath \ "result").readNullable[String]
    ) (OwcRequestResult.apply _)

  implicit val owcRequestResultWrites: Writes[OwcRequestResult] = (
    (JsPath \ "contentType").writeNullable[String] and
      (JsPath \ "result").writeNullable[String]
    ) (unlift(OwcRequestResult.unapply))

  implicit val owcRequestResultFormat: Format[OwcRequestResult] =
    Format(owcRequestResultReads, owcRequestResultWrites)

  /**
    * OwcOperation Json stuff
    */
  object OwcOperationJs {

    /**
      *
      * @param code
      * @param method
      * @param contentType
      * @param href
      * @param request
      * @param result
      * @return
      */
    def apply(code: String,
              method: String,
              contentType: String,
              href: String,
              request: Option[OwcPostRequestConfig],
              result: Option[OwcRequestResult]): OwcOperation = {
      // Todo, we might find a way to find an existing UUID from DB if entry exists
      val uuid = UUID.randomUUID()
      OwcOperation(uuid, code, method, contentType, href, request, result)
    }

    /**
      *
      * @param arg
      * @return
      */
    def unapply(arg: OwcOperation): Option[(String,
      String,
      String,
      String,
      Option[OwcPostRequestConfig],
      Option[OwcRequestResult])] = {
      Some(arg.code,
        arg.method,
        arg.contentType,
        arg.href,
        arg.request,
        arg.result)
    }
  }

  implicit val owcOperationReads: Reads[OwcOperation] = (
    (JsPath \ "code").read[String] and
      (JsPath \ "method").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "href").read[String] and
      (JsPath \ "request").readNullable[OwcPostRequestConfig] and
      (JsPath \ "result").readNullable[OwcRequestResult]
    ) (OwcOperationJs.apply _)

  implicit val owcOperationWrites: Writes[OwcOperation] = (
    (JsPath \ "code").write[String] and
      (JsPath \ "method").write[String] and
      (JsPath \ "type").write[String] and
      (JsPath \ "href").write[String] and
      (JsPath \ "request").writeNullable[OwcPostRequestConfig] and
      (JsPath \ "result").writeNullable[OwcRequestResult]
    ) (unlift(OwcOperationJs.unapply))

  implicit val owcOperationFormat: Format[OwcOperation] =
    Format(owcOperationReads, owcOperationWrites)

  /**
    * OwcOffering Json stuff
    */
  object OwcOfferingJs {

    /**
      *
      * @param code
      * @param operations
      * @param content
      * @return
      */
    def apply(code: String, operations: List[OwcOperation], content: List[String]): OwcOffering = {
      // Todo, we might find a way to find an existing UUID from DB if entry exists
      val uuid = UUID.randomUUID()
      code match {
        case "http://www.opengis.net/spec/owc-geojson/1.0/req/wms" => WmsOffering(uuid, code, operations, content)
        case "http://www.opengis.net/spec/owc-geojson/1.0/req/wmts" => WmtsOffering(uuid, code, operations, content)
        case "http://www.opengis.net/spec/owc-geojson/1.0/req/wfs" => WfsOffering(uuid, code, operations, content)
        case "http://www.opengis.net/spec/owc-geojson/1.0/req/wcs" => WcsOffering(uuid, code, operations, content)
        case "http://www.opengis.net/spec/owc-geojson/1.0/req/csw" => CswOffering(uuid, code, operations, content)
        case "http://www.opengis.net/spec/owc-geojson/1.0/req/wps" => WpsOffering(uuid, code, operations, content)
        case "http://www.opengis.net/spec/owc-geojson/1.0/req/gml" => GmlOffering(uuid, code, operations, content)
        case "http://www.opengis.net/spec/owc-geojson/1.0/req/kml" => KmlOffering(uuid, code, operations, content)
        case "http://www.opengis.net/spec/owc-geojson/1.0/req/geotiff" => GeoTiffOffering(uuid, code, operations, content)
        case "http://www.opengis.net/spec/owc-geojson/1.0/req/sos" => SosOffering(uuid, code, operations, content)
        case "http://www.opengis.net/spec/owc-geojson/1.0/req/netcdf" => NetCdfOffering(uuid, code, operations, content)
        case "http://www.opengis.net/spec/owc-geojson/1.0/req/http-link" => HttpLinkOffering(uuid, code, operations, content)
        case _ => throw new IllegalArgumentException("offering code cannot be used to build offering type")
      }

    }

    /**
      *
      * @param arg
      * @return
      */
    def unapply(arg: OwcOffering): Option[(String, List[OwcOperation], List[String])] = {
      Some(arg.code, arg.operations, arg.content)
    }
  }

  implicit val owcOfferingReads: Reads[OwcOffering] = (
    (JsPath \ "code").read[String] and
      (JsPath \ "operations").read[List[OwcOperation]] and
      (JsPath \ "content").read[List[String]]
    ) (OwcOfferingJs.apply _)

  implicit val owcOfferingWrites: Writes[OwcOffering] = (
    (JsPath \ "code").write[String] and
      (JsPath \ "operations").write[List[OwcOperation]] and
      (JsPath \ "content").write[List[String]]
    ) (unlift(OwcOfferingJs.unapply))

  implicit val owcOfferingFormat: Format[OwcOffering] =
    Format(owcOfferingReads, owcOfferingWrites)

  /**
    * OwcEntry Json stuff
    */

  object OwcEntryJs {
    def apply(id: String,
              bbox: Option[Rectangle],
              properties: OwcProperties,
              offerings: List[OwcOffering]): OwcEntry = {
      OwcEntry(id,
        bbox,
        properties,
        offerings)
    }

    def unapply(arg: OwcEntry): Option[(
      String,
        Option[Rectangle],
        OwcProperties,
        List[OwcOffering])] = {
      Some(arg.id, Some(SpatialContext.GEO.getShapeFactory().rect(-180.0, 180.0, -90.0, 90.0)), arg.properties, arg.offerings)
    }
  }

  implicit val owcEntryReads: Reads[OwcEntry] = (
    (JsPath \ "id").read[String] and
      (JsPath \ "geometry").readNullable[Rectangle] and
      (JsPath \ "properties").read[OwcProperties] and
      (JsPath \ "offerings").read[List[OwcOffering]]
    ) (OwcEntryJs.apply _)

  implicit val owcEntryWrites: Writes[OwcEntry] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "geometry").writeNullable[Rectangle] and
      (JsPath \ "properties").write[OwcProperties] and
      (JsPath \ "offerings").write[List[OwcOffering]]
    ) (unlift(OwcEntryJs.unapply))

  implicit val owcEntryFormat: Format[OwcEntry] =
    Format(owcEntryReads, owcEntryWrites)



  /**
    * OwcDocument Json stuff
    */
  implicit val owcDocumentReads: Reads[OwcDocument] = (
    (JsPath \ "id").read[String] and
      (JsPath \ "geometry").readNullable[Rectangle] and
      (JsPath \ "properties").read[OwcProperties] and
      (JsPath \ "features").read[List[OwcEntry]]
    ) (OwcDocument.apply _)

  implicit val owcDocumentWrites: Writes[OwcDocument] = (
    (JsPath \ "code").write[String] and
      (JsPath \ "geometry").writeNullable[Rectangle] and
      (JsPath \ "properties").write[OwcProperties] and
      (JsPath \ "features").write[List[OwcEntry]]
    ) (unlift(OwcDocument.unapply))

  implicit val owcDocumentFormat: Format[OwcDocument] =
    Format(owcDocumentReads, owcDocumentWrites)

  /**
    * spatial4j Rectangle / Geometries Json stuff
    */

  /**
    * RectangleReader
    */
  class RectangleReader extends Reads[Rectangle] with ClassnameLogger {

    private lazy val ctx = SpatialContext.GEO
    private lazy val geoJsonReader = ctx.getFormats().getReader(ShapeIO.GeoJSON)

    /**
      *
      * @param geoJsonString
      * @return
      */
    def jsToRect(geoJsonString: String): Option[Rectangle] = {
      Some(geoJsonReader.read(geoJsonString).asInstanceOf[Rectangle])
    }

    /**
      *
      * @param geoJsonJsValue
      * @return
      */
    def jsToRect(geoJsonJsValue: JsValue): Option[Rectangle] = {
      val geoJsonString = Json.stringify(geoJsonJsValue)
      Some(geoJsonReader.read(geoJsonString).asInstanceOf[Rectangle])
    }

    /**
      *
      * @param json
      * @return
      */
    def reads(json: JsValue): JsResult[Rectangle] = {
      val rectOpt = jsToRect(json)
      rectOpt match {
        case Some(s) => {
          JsSuccess(s)
        }
        case _ => JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.rectangle"))))
      }
    }
  }

  /**
    * RectangleWriter
    */
/*  class RectangleWriter extends Writes[Rectangle] with ClassnameLogger {
    private lazy val ctx = SpatialContext.GEO
    private lazy val geoJsonWriter = ctx.getFormats().getWriter(ShapeIO.GeoJSON)


   def rectToJsValue(rect: Rectangle): JsValue = {
      logger.error(s"been here, recttoJsValue rect minx ${rect.getMinX}")
      val jsonString = geoJsonWriter.toString(rect)
      logger.error(s"been here, jsonString ${jsonString}")
      Json.parse(jsonString)
    }

    def writes(rect: Rectangle): JsValue = {
      logger.error(s"been here, writes rect minx ${rect.getMinX}")
      rectToJsValue(rect)
    }


      def writes(rect: Rectangle) = Json.obj(
        "lat" -> "blub",
        "long" -> "blu"
      )

  }*/

  implicit val rectangleWrites = new Writes[Rectangle] {
    def writes(rect: Rectangle) = Json.obj(
      "lat" -> "blub",
      "long" -> "blu"
    )
  }

  implicit val rectangleReads: Reads[Rectangle] = new RectangleReader()

  // implicit val rectangleWrites: Writes[Rectangle] = new RectangleWriter()

/*  implicit val rectangleFormat: Format[Rectangle] =
    Format(rectangleReads, rectangleWrites)*/

}
