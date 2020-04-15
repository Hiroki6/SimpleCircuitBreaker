package core

import java.time.Instant

import cats.syntax.semigroup._
import cats.syntax.apply._
import cats.effect.{ ContextShift, IO, Timer }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should._
import retry._
import retry.RetryDetails._
import retry.CatsEffect._
import retry.RetryPolicies._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class CircuitBreakerTest extends AnyFreeSpec with Matchers {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  def correctService(req: Int): IO[String] = IO("success")
  def failureService(req: Int): IO[String] = IO.raiseError(new Exception("error"))

  val breakerOptions = BreakerOptions(3, 60, "Test Circuit Breaker open.")

  def logError(action: String)(err: Throwable, details: RetryDetails): IO[Unit] = details match {
    case WillDelayAndRetry(nextDelay: FiniteDuration, retriesSoFar: Int, cumulativeDelay: FiniteDuration) =>
      IO {
        println(s"$action was failed. So far we have retried $retriesSoFar times. error: ${err.getMessage}")
      }

    case GivingUp(totalRetries: Int, totalDelay: FiniteDuration) =>
      IO {
        println(s"$action gave up after $totalRetries retries. error: ${err.getMessage}")
      }
  }

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

    "After a failure happens more than specific times, requests are refused" in {
      val prog = CircuitBreaker.create[IO, String](breakerOptions.copy(maxBreakerFailures = 1)).flatMap { circuitBreaker =>
        retryingOnAllErrors(
          policy = RetryPolicies.limitRetries[IO](3),
          onError = logError("failureService")
        ) {
            circuitBreaker.withCircuitBreaker(failureService(1))
          }
      }

      prog.attempt.unsafeRunSync() shouldBe Left(CircuitBreakerException(breakerOptions.breakerDescription))
    }

    "After wait for the specific time during the breaker is opened, request are transported directly again." in {
      val RESET_TIME = 10
      val retry3times5s = limitRetries[IO](3) |+| constantDelay[IO]((RESET_TIME - 5).seconds)
      val retry3times10s = limitRetries[IO](1) |+| constantDelay[IO]((RESET_TIME + 10).seconds)
      val prog = CircuitBreaker.create[IO, String](breakerOptions.copy(maxBreakerFailures = 1, resetTimeoutSecs = RESET_TIME)).flatMap { circuitBreaker =>
        val failure3times: IO[String] = retryingOnAllErrors(
          policy = retry3times5s,
          onError = logError("failureService")
        ) {
            circuitBreaker.withCircuitBreaker(failureService(1))
          }

        val success: IO[String] = retryingOnAllErrors(
          policy = retry3times10s,
          onError = logError("correctService")
        ) {
            circuitBreaker.withCircuitBreaker(correctService(1))
          }

        failure3times.attempt.flatMap { _ => IO.unit } *> success
      }

      prog.attempt.unsafeRunSync() shouldBe "success"
    }
  }
}
