import models.metadata.ValidValues
import org.scalatestplus.play.PlaySpec

/**
  * Specification test for {@link ValidValues}
  */
class ValidValuesSpec extends PlaySpec {

  "Instantiation " should {
    "succeed on values list + standard value" in {
      new ValidValues(0, List("test", "test2"), None)
    }

    "succeed on values list + descriptions + standard value" in {
      new ValidValues(0, List("test", "test2"), Some(List("Description", "Description 2")))
    }

    "fail on empty values list" in {
      val thrown = intercept[IllegalArgumentException] {
        new ValidValues(0, List(), None)
      }
      thrown.getMessage mustEqual "requirement failed: values list must not be empty"
    }

    "fail on values and descriptions list of different size" in {
      val thrown = intercept[IllegalArgumentException] {
        new ValidValues(0, List("test"), Some(List("Description", "Description 2")))
      }
      thrown.getMessage mustEqual "requirement failed: decriptions list must either be None or same length as values list"
    }

    "fail on standardValue too big" in {
      val thrown = intercept[IllegalArgumentException] {
        new ValidValues(4, List("test", "test2"), Some(List("Description", "Description 2")))
      }
      thrown.getMessage mustEqual "requirement failed: standardValue must be within values list length"
    }

    "fail on standardValue too small" in {
      val thrown = intercept[IllegalArgumentException] {
        new ValidValues(-4, List("test", "test2"), Some(List("Description", "Description 2")))
      }
      thrown.getMessage mustEqual "requirement failed: standardValue must be within values list length"
    }

    "fail on non-unique values" in{
      val thrown = intercept[IllegalArgumentException] {
        new ValidValues(1, List("test", "test2", "test"), None)
      }
      thrown.getMessage mustEqual "requirement failed: all values must be unique"
    }

    "fail on non-unique descriptions" in{
      val thrown = intercept[IllegalArgumentException] {
        new ValidValues(1, List("test", "test2", "test3"), Some(List("Description", "Description", "Description 3")))
      }
      thrown.getMessage mustEqual "requirement failed: all descriptions must be unique"
    }
  }

}
