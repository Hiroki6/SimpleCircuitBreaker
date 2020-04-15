package core

import cats.MonadError
import cats.effect.{ Clock, Concurrent }
import cats.effect.concurrent.MVar
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._

trait CircuitBreaker[F[_], A] {
  def withCircuitBreaker(body: F[A]): F[A]
}

object CircuitBreaker {
  def create[F[_]: Clock, A](breakerOptions: BreakerOptions)(implicit S: Concurrent[F], ME: MonadError[F, Throwable]): F[CircuitBreaker[F, A]] =
    create(BreakerClosed(0), breakerOptions)

  private[core] def create[F[_]: Clock, A](breakerStatus: BreakerStatus, breakerOptions: BreakerOptions)(implicit S: Concurrent[F], ME: MonadError[F, Throwable]): F[CircuitBreaker[F, A]] =
    MVar.of[F, BreakerStatus](breakerStatus).map { status =>
      new CircuitBreaker[F, A] {
        override def withCircuitBreaker(body: F[A]): F[A] =
          status.read.flatMap {
            case BreakerClosed(_) => callIfClosed(body)
            case BreakerOpen(_)   => callIfOpen(body)
          }

        def callIfClosed(body: F[A]): F[A] =
          body.attempt.flatMap {
            case Right(v) => ME.pure(v)
            case Left(e)  => incError() *> ME.raiseError(e)
          }

        def callIfOpen(body: F[A]): F[A] =
          for {
            currentTime <- Utils.getCurrentTime[F]
            s <- status.take
            canaryResult <- s match {
              case closed @ BreakerClosed(_) => status.put(closed).map(_ => false)
              case open @ BreakerOpen(timeOpened) =>
                if (currentTime > timeOpened) status.put(BreakerOpen(currentTime + breakerOptions.resetTimeoutSecs)).map(_ => true)
                else status.put(open).map(_ => false)
            }
            result <- if (canaryResult) canaryCall(body) else failingCall(breakerOptions)
          } yield {
            result
          }

        def canaryCall(body: F[A]): F[A] =
          callIfClosed(body) <* status.put(BreakerClosed(0))

        def incError(): F[Unit] =
          for {
            currentTime <- Utils.getCurrentTime
            s <- status.take
            _ <- s match {
              case BreakerClosed(errorCount) =>
                if (errorCount >= breakerOptions.maxBreakerFailures) status.put(BreakerOpen(currentTime + breakerOptions.resetTimeoutSecs))
                else status.put(BreakerClosed(errorCount + 1))
              case open => status.put(open)
            }
          } yield ()

        def failingCall(breakerOptions: BreakerOptions): F[A] = ME.raiseError(CircuitBreakerException(breakerOptions.breakerDescription))
      }
    }
}

