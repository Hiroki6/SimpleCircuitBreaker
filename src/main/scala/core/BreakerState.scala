package core
import scala.concurrent.duration.FiniteDuration

enum BreakerState:
  case BreakerClosed(errorCount: Int)
  case BreakerOpen(timeOpened: FiniteDuration)

  def isStatusOpen: Boolean = this match
    case BreakerClosed(_) => false
    case BreakerOpen(_)   => true

  def isStatusClosed: Boolean = !isStatusOpen