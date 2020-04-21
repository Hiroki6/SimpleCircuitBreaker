package micro_services

import cats.syntax.functor._
import cats.effect.{ ExitCode, IO, IOApp }
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder

object ServerService extends IOApp {
  val PORT = 8081

  val server = HttpRoutes.of[IO] {
    case GET -> Root => Ok(s"Hello Server")
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(PORT, "localhost")
      .withHttpApp(server)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}
