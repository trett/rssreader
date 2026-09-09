package client

import be.doeraene.webcomponents.ui5.MessageStrip
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLDivElement

import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.duration.*
import scala.language.implicitConversions
import scala.scalajs.js.timers

enum NotifyLevel:
    case Info, Error

case class Notify(id: Long, message: String, level: NotifyLevel)

object NotifyComponent {

    /** Toasts live in a fixed overlay, so they never push the article list around. */
    private val notifyVar: Var[List[Notify]] = Var(List())
    private val maxVisible = 4
    private val infoTimeout = 4.seconds
    private val errorTimeout = 8.seconds
    private val nextId: AtomicLong = AtomicLong(0)

    def infoMessage(message: String): Unit = show(message, NotifyLevel.Info, infoTimeout)

    def errorMessage(error: Throwable): Unit =
        show(
            Option(error.getMessage).getOrElse("Unexpected error"),
            NotifyLevel.Error,
            errorTimeout
        )

    private def show(message: String, level: NotifyLevel, timeout: FiniteDuration): Unit =
        val notify = Notify(nextId.incrementAndGet(), message, level)
        // A repeated message restarts its timer instead of piling up another copy.
        notifyVar.update(xs => (xs.filterNot(_.message == message) :+ notify).takeRight(maxVisible))
        timers.setTimeout(timeout)(dismiss(notify.id))

    private def dismiss(id: Long): Unit = notifyVar.update(_.filterNot(_.id == id))

    def render: ReactiveHtmlElement[HTMLDivElement] = div(
        cls := "toast-stack",
        children <-- notifyVar.signal.split(_.id)((_, notify, _) => NotifyComponent(notify))
    )

    def apply(notify: Notify): HtmlElement =
        val design = notify.level match
            case NotifyLevel.Info  => MessageStripDesign.Information
            case NotifyLevel.Error => MessageStripDesign.Critical
        MessageStrip(
            cls := "toast",
            _.design := design,
            notify.message,
            MessageStrip.events.onClose.mapTo(notify.id) --> (id => dismiss(id))
        )
}
