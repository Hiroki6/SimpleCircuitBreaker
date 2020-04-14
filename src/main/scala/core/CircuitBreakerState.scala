package core

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.instances.list._
import cats.syntax.traverse._

case class CircuitBreakerException(message: String) extends Exception {
  override def getMessage: String = message
}

case class CircuitBreakerState(refs: List[Ref[IO, BreakerStatus]]) {
  def combine(state: CircuitBreakerState): CircuitBreakerState = CircuitBreakerState(refs ++ state.refs)

  def isCircuitBreakerOpen: IO[Boolean] =
    refs.traverse(_.get).map(_.exists(_.isStatusOpen))

  def isCircuitBreakerClosed: IO[Boolean] =
    refs.traverse(_.get).map(_.forall(_.isStatusClosed))
}

