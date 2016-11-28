/*
// Copyright \(C\) 2011-2012 the original author or authors.
 */
package controllers

import javax.inject.{Inject, Provider}

import play.api.{Application, Configuration, Play}
import play.api.cache.CacheApi
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Controller, Result}
import utils.{ClassnameLogger, PasswordHashing}

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, Node}

/**
  * inserts the MDMetadata into the TransactionInsert XML
  * @param xml
  */
private class AddMDMetadata(xml: Node) extends RewriteRule {
  override def transform(n: Node): Seq[Node] = n match {
    case e: Elem if e.label == "Insert" => {
      new Elem(e.prefix, "Insert", e.attributes, e.scope, e.minimizeEmpty, (xml).toSeq:_*)
    }
    case x => x
  }
}

/**
  *
  */
class CswController @Inject()(val configuration: Configuration,
                              val cache: CacheApi,
                              val passwordHashing: PasswordHashing,
                              wsClient: WSClient,
                              implicit val context: ExecutionContext,
                              appProvider: Provider[Application]
                             )
  extends Controller with ClassnameLogger with Security {


  lazy val app = appProvider.get()
  lazy val cswtInsertResource = app.resource("csw/transaction.insert.xml").get
  lazy val cswtInsertXml = scala.xml.XML.load(cswtInsertResource)
  lazy val mdMetadata = scala.xml.XML.load(app.resource("csw/MD_Metadata.test.xml").get)


  val CSW_URL: String = configuration.getString("smart.csw.url").getOrElse("http://localhost:8000")
  val CSW_OPERATIONS_METADATA_URL: String = s"${CSW_URL}/?service=CSW&version=2.0.2&request=GetCapabilities&sections=OperationsMetadata"

  /** calls CSW:Transaction -> Insert
    * FIXME should implement HasToken(parse.json) instead of Action.async, to only allow authenticated users
    */
  def insert:Action[AnyContent] = Action.async { request =>
    logger.debug(request.body.asJson.toString)

    //TODO parse JSON to MDMetadataSet and convert that to XML

    val futureResponse: Future[WSResponse] = for {
      getCapaResponse <- wsClient.url(CSW_OPERATIONS_METADATA_URL).get()
      insertResponse <- {
        //check if getCapaResponse indicates, that the CSW can perform Transactions
        val operation = (getCapaResponse.xml \\ "OperationsMetadata" \\ "Operation").filter( node => {
          logger.debug(s"Attribute 'name'=${node.attribute("name").get.text.toString}")
          node.attribute("name").get.text.equals("Transaction")
        })
        logger.debug(s"operation = ${operation.toString()}")
        if (operation.isEmpty) {
          throw new UnsupportedOperationException("CSW does not support Transaction.")
        }

        //insert MDMEtadata in insert template
        val rule = new RuleTransformer(new AddMDMetadata(mdMetadata))
        val finalXML = rule.transform(cswtInsertXml)
        logger.error(finalXML.toString())
        wsClient.url(CSW_URL).post(finalXML.toString())
      }
    } yield insertResponse

    futureResponse.recover {
      case e: Exception =>
        logger.error("Insert CSW threw exception", e)
        InternalServerError(e.getMessage)
    }

    futureResponse.map(response => {
      logger.error(response.xml.toString())
      response.xml match {
        case e: Elem if e.label == "TransactionResponse" => {
          val fileIdentifier = (e \\ "InsertResult" \\ "BriefRecord" \\ "identifier").text
          Ok(Json.obj("type" -> "success", "fileIdentifier" -> fileIdentifier, "message" -> s"Inserted as ${fileIdentifier}." ))
        }
        case e: Elem if e.label == "ExceptionReport" => {
          val message = (e \\ "Exception" \\ "ExceptionText").text
          //TODO SR make this InternalServerError
          Ok(Json.obj("type" -> "danger", "message" -> message))
        }
        case x => InternalServerError(s"Unexpected Response: ${response}")
      }
    })
  }
}
