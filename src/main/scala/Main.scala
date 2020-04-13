import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

object Main extends IOApp {
  def service(req: Int): IO[String] = {
    IO { throw new Exception("test") }
  }

  val program: IO[Unit] = for {
    circuitBreaker <- CircuitBreaker.create[IO, Int, String]
    r1 = circuitBreaker.withCircuitBreaker(service(1))
    r2 = circuitBreaker.withCircuitBreaker(service(2))
    r3 = circuitBreaker.withCircuitBreaker(service(3))
    _ <- List(
      r1.start,
      r2.start,
      r3.start
    ).parSequence.void
  } yield ()

  override def run(args: List[String]): IO[ExitCode] = {
    program.as(ExitCode.Success)
  }
}
