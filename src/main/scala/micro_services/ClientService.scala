package micro_services

import cats.syntax.semigroup._
import cats.syntax.functor._
import cats.effect.{ ExitCode, IO, IOApp }
import org.http4s.{ HttpRoutes, Response }
import org.http4s.dsl.io._
import core.{ BreakerOptions, CircuitBreaker }
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import retry.retryingOnAllErrors
import retry.RetryPolicies._
import retry.CatsEffect._

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

object ClientService extends IOApp {
  val PORT = 8080

  def internalRequest(client: Client[IO], circuitBreaker: CircuitBreaker[IO, Response[IO]]) = retryingOnAllErrors(
    policy = limitRetries[IO](3) |+| exponentialBackoff[IO](10.seconds),
    onError = retry.noop[IO, Throwable]
  ) {
    circuitBreaker.withCircuitBreaker {
      val url = s"http://localhost:${ServerService.PORT}"
      client.expect[String](url).flatMap(res => Ok(res))
    }
  }

  val client = HttpRoutes.of[IO] {
    case GET -> Root => {
      BlazeClientBuilder[IO](global).resource.use { client =>
        CircuitBreaker.create[IO, Response[IO]](BreakerOptions.breakerOptions).flatMap { circuitBreaker =>
          internalRequest(client, circuitBreaker)
        }
      }
    }
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(PORT, "localhost")
      .withHttpApp(client)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}
