package core

import cats.effect.Clock
import scala.concurrent.duration

object Utils {
  def getCurrentTime[F[_]](implicit clock: Clock[F]): F[Long] = clock.realTime(duration.SECONDS)
}
