package core

import cats.effect.Clock
import scala.concurrent.duration.FiniteDuration

object Utils {
  def getCurrentTime[F[_]](using clock: Clock[F]): F[FiniteDuration] = clock.realTime
}
