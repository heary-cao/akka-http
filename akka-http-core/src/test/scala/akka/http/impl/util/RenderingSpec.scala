/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.http.impl.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RenderingSpec extends AnyWordSpec with Matchers {

  "The StringRendering" should {

    "correctly render Ints and Longs to decimal" in {
      (new StringRendering ~~ 0).get shouldEqual "0"
      (new StringRendering ~~ 123456789).get shouldEqual "123456789"
      (new StringRendering ~~ -123456789L).get shouldEqual "-123456789"
    }

    "correctly render Ints and Longs to hex" in {
      (new StringRendering ~~% 0).get shouldEqual "0"
      (new StringRendering ~~% 65535).get shouldEqual "ffff"
      (new StringRendering ~~% 65537).get shouldEqual "10001"
      (new StringRendering ~~% -10L).get shouldEqual "fffffffffffffff6"
    }

    "correctly render plain Strings" in {
      (new StringRendering ~~ "").get shouldEqual ""
      (new StringRendering ~~ "hello").get shouldEqual "hello"
    }

    "correctly render escaped Strings" in {
      (new StringRendering ~~# "").get shouldEqual "\"\""
      (new StringRendering ~~# "hello").get shouldEqual "hello"
      (new StringRendering ~~# """hel"lo""").get shouldEqual """"hel\"lo""""
    }
  }
}
