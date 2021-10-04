package core
import com.typesafe.config.ConfigFactory

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

case class BreakerOptions(maxBreakerFailures: Int, resetTimeoutSecs: FiniteDuration, breakerDescription: String)

object BreakerOptions {
  private val config = ConfigFactory.load()
  val breakerOptions: BreakerOptions = BreakerOptions(
    config.getInt("max-breaker-failures"),
    config.getLong("reset-timeout-secs").seconds,
    config.getString("breaker-description")
  )
}
