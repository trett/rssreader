package client2

import be.doeraene.webcomponents.ui5.MessageStrip
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement

import scala.language.implicitConversions

enum NotifyLevel:
  case Info, Error

case class Notify(message: String, level: NotifyLevel)

object NotifyComponent {

  val notifyVar: Var[List[Notify]] = Var(List())

  def infoMessage(message: String): Notify =
    Notify(message, NotifyLevel.Info)

  def errorMessage(message: String): Notify =
    Notify(message, NotifyLevel.Error)

  def render = div(
    children <-- notifyVar.signal.map(_.map(NotifyComponent(_)))
  )

  def apply(notify: Notify): HtmlElement = {
    val Notify(message, level) = notify
    val nc =
      level match {
        case NotifyLevel.Info =>
          MessageStrip(_.design := MessageStripDesign.Information, message)
        case NotifyLevel.Error =>
          MessageStrip(_.design := MessageStripDesign.Critical, message)
      }
    nc.amend(
      MessageStrip.events.onClose
        .mapTo(message) --> notifyVar.updater((xs, x) =>
        xs.filterNot(_.message == x)
      )
    )
    nc
  }
}
