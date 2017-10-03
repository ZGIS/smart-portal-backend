package mockserv

import com.github.unisay.mockserver.scala.DSL._
import com.github.unisay.mockserver.scala.DSL.Statuses._
import org.mockserver.client.server.MockServerClient

import scala.io.Source
import scala.language.postfixOps

import utils.ClassnameLogger

class CswMockServer extends ClassnameLogger {

  val fileAsString = Source.fromURL(this.getClass().getResource("csw/pycsw.local.capabilities.xml")).getLines.mkString("\n")

  implicit val mockServerClient = new MockServerClient("localhost", 1080)

  def noCswGetCapa300: Unit = {
    when get "/pycsw/csw" has {
      param("request", "GetCapabilities") and
        param("service", "CSW") and
        param("version", "3.0.0")
    } respond {
      BadRequest
    } always
  }

  def cswGetCapaLocal = {
    when get "/pycsw/csw" has {
      param("request", "GetCapabilities") and
        param("service", "CSW") and
        param("version", "2.0.2")
    } respond Ok + body(fileAsString) always
  }

  def postCswTransactionOk: Unit = {
    when post "/pycsw/csw" has {
        header("Content-type", "application/xml") and
          regexBody(".*Transaction.*")

    } respond {
      Ok + """<csw:TransactionResponse xmlns:csw="http://www.opengis.net/cat/csw" xmlns:dc="http://www.purl.org/dc/elements/1.1/"
        xmlns:dct="http://www.purl.org/dc/terms/" xsi:schemaLocation="http://www.opengis.net/cat/csw/2.0.2 http://schemas.opengis.net/csw/2.0.2/CSW-discovery.xsd"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
             |   <csw:TransactionSummary>
             |      <csw:totalInserted>1</csw:totalInserted>
             |   </csw:TransactionSummary>
             |</csw:TransactionResponse>""".stripMargin
    } always
  }

  def getIndexUpdateOk: Unit = {
    when get "/cswi-api/v1/buildIndex/smart" respond {
      Ok
    } always
  }

  // http://localhost:1080/pycsw/csw?request=GetCapabilities&version=2.0.2&service=CSW

}
