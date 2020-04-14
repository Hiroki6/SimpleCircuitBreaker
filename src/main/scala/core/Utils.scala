package core

import java.time.Instant

import cats.Applicative
import cats.syntax.applicative._

object Utils {
  def getCurrentTime[F[_]: Applicative]: F[Long] = Instant.now().getEpochSecond.pure[F]
}
