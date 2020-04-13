import cats.MonadError
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.apply._

class CircuitBreaker[F[_], A, B](breakerOptions: BreakerOptions, status: Ref[F, BreakerStatus])(implicit S: Sync[F], ME: MonadError[F, Throwable]) {
  def withCircuitBreaker(body: F[B]): F[B] =
    status.get.flatMap {
       case BreakerClosed(_) => callIfClosed(body)
       case BreakerOpen(_) => callIfOpen(body)
      }

  private def callIfClosed(body: F[B]): F[B] =
    body.handleErrorWith {
      e => incError() *> ME.raiseError(e)
    }

  private def callIfOpen(body: F[B]): F[B] =
    Utils.getCurrentTime[F].flatMap { currentTime =>
      status.modify {
        case closed@BreakerClosed(_) => (closed, false)
        case open@BreakerOpen(timeOpened) => {
          if(currentTime > timeOpened) (BreakerOpen(currentTime+breakerOptions.resetTimeoutSecs), true)
          else (open, false)
        }
      }.flatMap { canaryResult =>
        if(canaryResult) canaryCall(body)
        else failingCall(breakerOptions)
      }
    }

  private def canaryCall(body: F[B]): F[B] =
    callIfClosed(body) <* status.set(BreakerClosed(0))

  private def incError() =
    Utils.getCurrentTime.flatMap { currentTime =>
      status.update {
        case BreakerClosed(errorCount) => {
          if(errorCount >= breakerOptions.maxBreakerFailures) BreakerOpen(currentTime+breakerOptions.resetTimeoutSecs)
          else BreakerClosed(errorCount + 1)
        }
        case other => other
      }
    }

  private def failingCall(breakerOptions: BreakerOptions) = throw CircuitBreakerException(breakerOptions.breakerDescription)
}

object CircuitBreaker {
  def create[F[_], A, B](implicit S: Sync[F], ME: MonadError[F, Throwable]): F[CircuitBreaker[F, A, B]] =
    Ref.of[F, BreakerStatus](BreakerClosed(0)).map { ref =>
      new CircuitBreaker[F, A, B](BreakerOptions.breakerOptions, ref)
    }
}
