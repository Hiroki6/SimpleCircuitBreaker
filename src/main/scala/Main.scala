import cats.effect.{ ExitCode, IO, IOApp }
import cats.implicits._
import core.{ BreakerOptions, CircuitBreaker }

object Main extends IOApp {
  def service(req: Int): IO[String] = {
    IO.raiseError {
      new Exception("test")
    }
  }

  val program: IO[List[String]] =
    CircuitBreaker.create[IO, String](BreakerOptions.breakerOptions).flatMap { circuitBreaker =>
      val r1 = circuitBreaker.withCircuitBreaker(service(1))
      val r2 = circuitBreaker.withCircuitBreaker(service(2))
      val r3 = circuitBreaker.withCircuitBreaker(service(3))
      for {
        fiber1 <- r1.start
        fiber2 <- r2.start
        fiber3 <- r3.start
        result1 <- fiber1.join
        result2 <- fiber2.join
        result3 <- fiber3.join
      } yield (List(result1, result2, result3))
    }

  override def run(args: List[String]): IO[ExitCode] = {
    program.as(ExitCode.Success)
  }
}
