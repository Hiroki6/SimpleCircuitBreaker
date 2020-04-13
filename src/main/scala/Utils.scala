import cats.syntax.applicative._
import java.time.Instant

import cats.Monad

object Utils {
  def getCurrentTime[F[_]: Monad]: F[Long] = Instant.now().getEpochSecond.pure[F]
}
