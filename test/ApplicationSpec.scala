import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play._
import play.api.db.evolutions._
import play.api.db.{Database, Databases}
import play.api.inject.guice._
import play.api.test.Helpers._
import play.api.test._
import play.api.{Application, Configuration}

/**
  *
  */
trait WithTestDatabase {

  /**
    *
    * @param block
    * @tparam T
    * @return
    */
  def withTestDatabase[T](block: Database => T) = {

    val config = new Configuration(ConfigFactory.load("application-testdb.conf"))

    val driver = config.getString("db.default.driver").get
    val url = config.getString("db.default.url").get
    val username = config.getString("db.default.username").get
    val password = config.getString("db.default.password").get
    val logSql = config.getBoolean("db.default.logSql").get

    Databases.withDatabase(
      driver = driver,
      url = url,
      config = Map(
        "user" -> username,
        "password" -> password,
        "logStatements" -> logSql
      )
    ) { database =>

      Evolutions.withEvolutions(database, ClassLoaderEvolutionsReader.forPrefix("test/")) {

        block(database)

      }
    }
  }
}

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
class ApplicationSpec extends PlaySpec with OneAppPerTest with BeforeAndAfter with WithTestDatabase {

  // Override newAppForTest if you need a FakeApplication with other than non-default parameters
  implicit override def newAppForTest(testData: TestData): Application = new
      GuiceApplicationBuilder().loadConfig(new Configuration(ConfigFactory.load("application-testdb.conf"))).build()

  before {

  }

  after {

  }

  "Routes" should {

    "send 404 on a bad request" in  {
      route(app, FakeRequest(GET, "/boum")).map(status(_)) mustBe Some(NOT_FOUND)
    }

  }

  "HomeController" should {

    "render the index page" in {
      val home = route(app, FakeRequest(GET, "/")).get

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Your new application is ready.")
    }

  }

  "CountController" should {

    "return an increasing count" in {
      contentAsString(route(app, FakeRequest(GET, "/count")).get) mustBe "0"
      contentAsString(route(app, FakeRequest(GET, "/count")).get) mustBe "1"
      contentAsString(route(app, FakeRequest(GET, "/count")).get) mustBe "2"
    }

  }

}
