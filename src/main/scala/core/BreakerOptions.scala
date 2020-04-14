package core

import pureconfig._
import pureconfig.generic.auto._

case class BreakerOptions(maxBreakerFailures: Int, resetTimeoutSecs: Int, breakerDescription: String)

object BreakerOptions {
  val breakerOptions = ConfigSource.default.loadOrThrow[BreakerOptions]
}
