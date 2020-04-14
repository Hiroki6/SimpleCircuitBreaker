package core

import java.time.Instant

import cats.effect.{ ContextShift, IO, Timer }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should._

import scala.concurrent.ExecutionContext

class CircuitBreakerTest extends AnyFreeSpec with Matchers {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  def correctService(req: Int): IO[String] = IO("success")

  val breakerOptions = BreakerOptions(3, 60, "Test Circuit Breaker open.")

  "Circuit Breaker" - {
    "When the breaker is closed, requests are transported directly" in {
      val prog = CircuitBreaker.create[IO, String](breakerOptions).flatMap { circuitBreaker =>
        circuitBreaker.withCircuitBreaker(correctService(1))
      }

      prog.unsafeRunSync() shouldBe "success"
    }

    "When the breaker is open, requests are refused" in {
      val prog = CircuitBreaker.create[IO, String](BreakerOpen(Instant.now().getEpochSecond), breakerOptions).flatMap { circuitBreaker =>
        circuitBreaker.withCircuitBreaker(correctService(1))
      }

      prog.attempt.unsafeRunSync() shouldBe Left(CircuitBreakerException(breakerOptions.breakerDescription))
    }
  }
}

