import pureconfig._
import pureconfig.generic.auto._

case class BreakerOptions(maxBreakerFailures: Int = 3, resetTimeoutSecs: Int = 60, breakerDescription: String = "Circuit breaker open.")

object BreakerOptions {
  val breakerOptions = ConfigSource.default.loadOrThrow[BreakerOptions]
}
