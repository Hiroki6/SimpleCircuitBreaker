package core

sealed trait BreakerStatus {
  def isStatusOpen: Boolean = this match {
    case BreakerClosed(_) => false
    case BreakerOpen(_)   => true
  }

  def isStatusClosed: Boolean = !isStatusOpen
}

case class BreakerClosed(errorCount: Int) extends BreakerStatus
case class BreakerOpen(timeOpened: Long) extends BreakerStatus
