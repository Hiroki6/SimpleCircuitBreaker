package core

import cats.MonadError
import cats.effect.{ Clock, Concurrent }
import cats.effect.concurrent.{ MVar, Ref }
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._

case class CircuitBreakerException(message: String) extends Exception {
  override def getMessage: String = message
}

trait CircuitBreaker[F[_]] {
  def run[A](body: => F[A]): F[A]
  def getStatus: F[BreakerStatus]
}

object CircuitBreaker {
  def create[F[_]](breakerOptions: BreakerOptions)(implicit C: Clock[F], S: Concurrent[F], ME: MonadError[F, Throwable]): F[CircuitBreaker[F]] =
    create(BreakerClosed(0), breakerOptions)

  def create[F[_]](breakerStatus: BreakerStatus, breakerOptions: BreakerOptions)(implicit C: Clock[F], S: Concurrent[F], ME: MonadError[F, Throwable]): F[CircuitBreaker[F]] =
    createWithRef(breakerStatus, breakerOptions)

  /**
   * CircuitBreaker with Ref
   */
  private[core] def createWithRef[F[_]](breakerStatus: BreakerStatus, breakerOptions: BreakerOptions)(implicit C: Clock[F], S: Concurrent[F], ME: MonadError[F, Throwable]): F[CircuitBreaker[F]] =
    Ref.of[F, BreakerStatus](breakerStatus).map { status =>
      new CircuitBreaker[F] {
        override def run[A](body: => F[A]): F[A] =
          getStatus.flatMap {
            case BreakerClosed(_) => callIfClosed(body)
            case BreakerOpen(_)   => callIfOpen(body)
          }

        override def getStatus: F[BreakerStatus] = status.get

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

  /**
   * CircuitBreaker with MVar
   */
  private[core] def createWithMVar[F[_]](breakerStatus: BreakerStatus, breakerOptions: BreakerOptions)(implicit C: Clock[F], S: Concurrent[F], ME: MonadError[F, Throwable]): F[CircuitBreaker[F]] =
    MVar.of[F, BreakerStatus](breakerStatus).map { status =>
      new CircuitBreaker[F] {
        override def run[A](body: => F[A]): F[A] =
          status.read.flatMap {
            case BreakerClosed(_) => callIfClosed(body)
            case BreakerOpen(_)   => callIfOpen(body)
          }

        override def getStatus: F[BreakerStatus] = status.read

        def callIfClosed[A](body: F[A]): F[A] =
          body.handleErrorWith(e => incError() >> e.raiseError)

        def callIfOpen[A](body: => F[A]): F[A] =
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

        def canaryCall[A](body: F[A]): F[A] =
          callIfClosed(body) <* status.take.flatMap(_ => status.put(BreakerClosed(0)))

        def incError(): F[Unit] =
          for {
            currentTime <- Utils.getCurrentTime[F]
            s <- status.take
            _ <- s match {
              case BreakerClosed(errorCount) =>
                if (errorCount >= breakerOptions.maxBreakerFailures) status.put(BreakerOpen(currentTime + breakerOptions.resetTimeoutSecs))
                else status.put(BreakerClosed(errorCount + 1))
              case open => status.put(open)
            }
          } yield ()

        def failingCall[A](breakerOptions: BreakerOptions): F[A] = CircuitBreakerException(breakerOptions.breakerDescription).raiseError
      }
    }
}

