package core

import cats.MonadError
import cats.effect.{ Clock, Concurrent }
import cats.effect.Ref
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._

case class CircuitBreakerException(message: String) extends Exception {
  override def getMessage: String = message
}

trait CircuitBreaker[F[_]] {
  def run[A](body: => F[A]): F[A]
  def getStatus: F[BreakerState]
}

object CircuitBreaker {
  def create[F[_]](breakerOptions: BreakerOptions)(using C: Clock[F], S: Concurrent[F], ME: MonadError[F, Throwable]): F[CircuitBreaker[F]] =
    create(BreakerClosed(0), breakerOptions)

  def create[F[_]](breakerState: BreakerState, breakerOptions: BreakerOptions)(using C: Clock[F], S: Concurrent[F], ME: MonadError[F, Throwable]): F[CircuitBreaker[F]] =
    createWithRef(breakerState, breakerOptions)

  /**
   * CircuitBreaker with Ref
   */
  private[core] def createWithRef[F[_]](breakerState: BreakerState, breakerOptions: BreakerOptions)(using C: Clock[F], S: Concurrent[F], ME: MonadError[F, Throwable]): F[CircuitBreaker[F]] =
    Ref[F].of(breakerState).map { status =>
      new CircuitBreaker[F] {
        override def run[A](body: => F[A]): F[A] =
          getStatus.flatMap {
            case BreakerClosed(_) => callIfClosed(body)
            case BreakerOpen(_)   => callIfOpen(body)
          }

        override def getStatus: F[BreakerState] = status.get

        def callIfClosed[A](body: F[A]): F[A] =
          body.handleErrorWith(e => incError() >> e.raiseError)

        def callIfOpen[A](body: => F[A]): F[A] = for {
          currentTime <- Utils.getCurrentTime[F]
          canaryResult <- status.modify {
            case closed @ BreakerClosed(_) => (closed, false)
            case open @ BreakerOpen(timeOpened) =>
              if (currentTime > timeOpened) (BreakerOpen(currentTime + breakerOptions.resetTimeoutSecs), true)
              else (open, false)
          }
          result <- if (canaryResult) canaryCall(body) else failingCall(breakerOptions)
        } yield {
          result
        }

        def canaryCall[A](body: F[A]): F[A] =
          callIfClosed(body) <* status.set(BreakerClosed(0))

        def incError(): F[Unit] = for {
          currentTime <- Utils.getCurrentTime[F]
          _ <- status.modify {
            case BreakerClosed(errorCount) =>
              if (errorCount >= breakerOptions.maxBreakerFailures) (BreakerOpen(currentTime + breakerOptions.resetTimeoutSecs), ())
              else (BreakerClosed(errorCount + 1), ())
            case open => (open, ())
          }
        } yield ()

        def failingCall[A](breakerOptions: BreakerOptions): F[A] = CircuitBreakerException(breakerOptions.breakerDescription).raiseError
      }
    }
}

