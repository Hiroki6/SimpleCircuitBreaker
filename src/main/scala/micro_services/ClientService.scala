package micro_services

import cats.syntax.semigroup.*
import cats.syntax.functor.*
import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.io.*
import core.{BreakerOptions, CircuitBreaker}
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.client.Client
import org.http4s.implicits.*
import retry.{RetryPolicies, retryingOnAllErrors}
import retry.RetryPolicies.*

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

object ClientService extends IOApp {
  val PORT = 8080

  def internalRequest(client: Client[IO], circuitBreaker: CircuitBreaker[IO]) =
    retryingOnAllErrors(
      policy = RetryPolicies.limitRetries[IO](3) |+| exponentialBackoff[IO](10.seconds),
      onError = retry.noop[IO, Throwable]
    ) {
      circuitBreaker.run[Response[IO]] {
        val url = s"http://localhost:${ServerService.PORT}"
        client.expect[String](url).flatMap(res => Ok(res))
      }
    }

  def client(circuitBreaker: CircuitBreaker[IO]) = HttpRoutes
    .of[IO] {
      case GET -> Root => {
        BlazeClientBuilder[IO](global).resource.use { client =>
          internalRequest(client, circuitBreaker)
        }
      }
    }
    .orNotFound

  def run(args: List[String]): IO[ExitCode] = for {
    circuitBreaker <- CircuitBreaker.create[IO](BreakerOptions.breakerOptions)
    exitCode <- BlazeServerBuilder[IO]
      .bindHttp(PORT, "localhost")
      .withHttpApp(client(circuitBreaker))
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  } yield exitCode
}
