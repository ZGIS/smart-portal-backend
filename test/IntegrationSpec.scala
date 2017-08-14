import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
class IntegrationSpec extends WithDefaultTest with OneServerPerTest with BeforeAndAfter with WithTestDatabase with OneBrowserPerTest with HtmlUnitFactory {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  implicit override def newAppForTest(testData: TestData): Application =
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application.test.conf"))).build(
        // here can go customisation
      )

  before {
    // here can go customisation
  }

  after {
    // here can go customisation
  }

  "Application" should {

    "work from within a browser" in {

      go to ("http://localhost:" + port)

      // contentAsJson(home) mustEqual Json.parse("""{"status": "Ok", "message": "application is ready"}""")
      pageSource must include ("application is ready")
    }
  }
}
