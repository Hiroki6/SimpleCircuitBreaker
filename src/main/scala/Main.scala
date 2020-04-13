import cats.effect.IO
import cats.implicits._
import scala.concurrent.ExecutionContext.global

object Main extends App {
  val service: Int => IO[String] = req => IO("success")
  implicit val cs = IO.contextShift(global)

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
}
