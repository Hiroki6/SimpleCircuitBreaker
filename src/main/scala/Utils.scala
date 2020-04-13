import cats.effect.IO
import java.time.Instant

object Utils {
  def getCurrentTime: IO[Long] = IO(Instant.now().getEpochSecond)
}
