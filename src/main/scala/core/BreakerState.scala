package core
import scala.concurrent.duration.FiniteDuration

sealed trait BreakerState {
  def isStatusOpen: Boolean = this match {
    case BreakerClosed(_) => false
    case BreakerOpen(_)   => true
  }

  def isStatusClosed: Boolean = !isStatusOpen
}

case class BreakerClosed(errorCount: Int) extends BreakerState
case class BreakerOpen(timeOpened: FiniteDuration) extends BreakerState
