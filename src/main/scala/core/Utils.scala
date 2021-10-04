package core

import cats.Functor
import cats.effect.Clock

import scala.concurrent.duration
import cats.syntax.functor.*

import scala.concurrent.duration.FiniteDuration

object Utils {
  def getCurrentTime[F[_]](using clock: Clock[F]): F[FiniteDuration] =
    clock.realTime
}
