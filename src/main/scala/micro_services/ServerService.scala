package micro_services

import cats.syntax.functor.*
import cats.effect.{ ExitCode, IO, IOApp }
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.io.*
import org.http4s.implicits.*

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
