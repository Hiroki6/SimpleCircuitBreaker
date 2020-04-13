import cats.data.Kleisli
import cats.effect.IO
import cats.effect.concurrent.Ref

class CircuitBreaker[A, B](service: A => IO[B]) {
  def runService: Kleisli[IO, BreakerOptions, (CircuitBreakerState, A => IO[B])] = Kleisli { breakerOptions =>
    for {
      ref <- Ref.of[IO, BreakerStatus](BreakerClosed(0))
    } yield {
      (CircuitBreakerState(List(ref)), breakerService(breakerOptions)(ref))
    }
  }

  private def breakerService(breakerOptions: BreakerOptions)(ref: Ref[IO, BreakerOptions])(request: A): IO[B] =
    ref.get.flatMap {
      case BreakerClosed(_) => callIfClosed(breakerOptions)(request, ref)
      case BreakerOpen(_) => callIfOpen(breakerOptions)(request, ref)
    }

  private def callIfClosed(breakerOptions: BreakerOptions)(request: A, ref: Ref[IO, BreakerStatus]): IO[B] =
    service(request).handleErrorWith {
      _ => incError(breakerOptions)(ref)
    }

  private def callIfOpen(breakerOptions: BreakerOptions)(request: A, ref: Ref[IO, BreakerStatus]): IO[B] =
    Utils.getCurrentTime.flatMap { currentTime =>
      ref.modify {
        case status@BreakerClosed(_) => (status, false)
        case status@BreakerOpen(timeOpened) => {
          if(currentTime > timeOpened) ((BreakerOpen(currentTime+(breakerOptions.resetTimeoutSecs))), true)
          else (status, false)
        }
      }.flatMap { canaryResult =>
        if(canaryResult) canaryCall(breakerOptions)(request, ref)
        else failingCall(breakerOptions)
      }
    }

  private def canaryCall(breakerOptions: BreakerOptions)(request: A, ref: Ref[IO, BreakerStatus]) : IO[B] =
    for {
      result <- callIfClosed(breakerOptions)(request, ref)
    } yield {
      ref.set(BreakerClosed(0))
      result
    }

  private def incError(breakerOptions: BreakerOptions)(ref: Ref[IO, BreakerStatus]) =
    Utils.getCurrentTime.flatMap { currentTime =>
      ref.modify {
        case BreakerClosed(errorCount) => {
          if(errorCount >= breakerOptions.maxBreakerFailures) (BreakerOpen(currentTime+breakerOptions.resetTimeoutSecs), ())
          else (BreakerClosed(errorCount + 1), ())
        }
        case other => (other, ())
      }
    }

  private def failingCall(breakerOptions: BreakerOptions) = throw CircuitBreakerException(breakerOptions.breakerDescription)
}
