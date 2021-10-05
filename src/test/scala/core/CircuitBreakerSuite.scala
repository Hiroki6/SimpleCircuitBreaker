package core

import cats.syntax.semigroup.*
import cats.effect.IO
import munit.CatsEffectSuite
import retry.*
import retry.RetryDetails.*
import retry.RetryPolicies.*

import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class CircuitBreakerSuite extends CatsEffectSuite {

  def correctService(req: Int): IO[String] = IO("success")
  def failureService: IO[String] = IO.raiseError(new Exception("error"))

  override def munitTimeout: Duration = 60.seconds

  private val breakerOptions = BreakerOptions(3, 60.seconds, "Test Circuit Breaker open.")

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

  test("When the breaker is closed, requests are transported directly") {
    val program = CircuitBreaker.create[IO](breakerOptions).flatMap { circuitBreaker =>
      circuitBreaker.run(correctService(1))
    }

    assertIO(program, "success")
  }

  test("When the breaker is open, requests are refused") {
    val program = for {
      currentTime <- Utils.getCurrentTime[IO]
      circuitBreaker <- CircuitBreaker.create[IO](BreakerOpen(currentTime + 10.seconds), breakerOptions)
      response <- circuitBreaker.run(correctService(1))
    } yield {
      response
    }

    program.attempt.map(it => assertEquals(it, Left(CircuitBreakerException(breakerOptions.breakerDescription))))
  }

  test("After a failure happens more than specific times, requests are refused") {
    val program = CircuitBreaker.create[IO](breakerOptions.copy(maxBreakerFailures = 1)).flatMap { circuitBreaker =>
      retryingOnAllErrors(
        policy = RetryPolicies.limitRetries[IO](3),
        onError = logError("failureService")
      ) {
          circuitBreaker.run(failureService)
        }
    }

    program.attempt.map(it => assertEquals(it, Left(CircuitBreakerException(breakerOptions.breakerDescription))))
  }

  test("After failures happened asynchronously more than specific times, requests are refused") {
    val program = CircuitBreaker.create[IO](breakerOptions.copy(maxBreakerFailures = 1)).flatMap { circuitBreaker =>
      val r: IO[String] = circuitBreaker.run(failureService)
      val p = for {
        f1 <- r.start
        f2 <- r.start
        f3 <- r.start
        _ <- f1.joinWithNever
        _ <- f2.joinWithNever
        _ <- f3.joinWithNever
      } yield ()

      p.handleErrorWith(_ => circuitBreaker.getStatus)
    }

    assertIOBoolean(program.map(_.isInstanceOf[BreakerOpen]))
  }

  test("After wait for the specific time during the breaker is opened, request are transported directly again.") {
    val RESET_TIME = 10.seconds
    val retry3times5s = limitRetries[IO](3) |+| constantDelay[IO](RESET_TIME - 5.seconds)
    val retry3times10s = limitRetries[IO](1) |+| constantDelay[IO](RESET_TIME + 10.seconds)
    val program = CircuitBreaker.create[IO](breakerOptions.copy(maxBreakerFailures = 1, resetTimeoutSecs = RESET_TIME)).flatMap { circuitBreaker =>
      val failure3times: IO[String] = retryingOnAllErrors(
        policy = retry3times5s,
        onError = logError("failureService")
      ) {
          circuitBreaker.run(failureService)
        }

      val success: IO[String] = retryingOnAllErrors(
        policy = retry3times10s,
        onError = logError("correctService")
      ) {
          circuitBreaker.run(correctService(1))
        }

      failure3times.handleErrorWith(_ => success)
    }

    assertIO(program, "success")
  }
}
