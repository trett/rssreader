package client

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLDivElement

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
// Not `duration.*`: it exports a `span` that shadows Laminar's `span` tag below.
import scala.concurrent.duration.DurationInt
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}

enum NotifyLevel:
    case Info, Error

/** @param id
  *   monotonic, so keying children stays stable while the message text repeats
  * @param count
  *   how many times this same message arrived; shown as "×2" instead of a second row
  * @param action
  *   optional label + effect, e.g. Try again
  * @param detail
  *   the original technical text, kept as a tooltip so it is available without being shouted
  */
case class Notify(
    id: Long,
    message: String,
    level: NotifyLevel,
    count: Int = 1,
    action: Option[(String, () => Unit)] = None,
    detail: Option[String] = None
)

/** Toasts, not message strips.
  *
  * The old version had three problems at once: the strips were in the document flow, so every
  * notification pushed the whole app down; nothing expired, so they piled up until dismissed by
  * hand; and identical messages appended new rows because the close handler matched on text while
  * the emitter always appended — so "Settings saved" appeared twice, forever.
  *
  * Now: a fixed overlay above the list, dark so it can never be mistaken for content, info expiring
  * on its own, errors waiting to be read, duplicates coalescing into a count, and at most three at
  * a time.
  */
object NotifyComponent:

    private val InfoTtl = 4200.millis
    private val MaxVisible = 3

    private val notifyVar: Var[List[Notify]] = Var(List.empty)

    // AtomicLong/AtomicReference rather than var: DisableSyntax.noVars is on. Single-threaded in
    // the browser, so these are cells, not concurrency control.
    private val seq = new AtomicLong(0L)
    private val timers = new AtomicReference[Map[Long, SetTimeoutHandle]](Map.empty)

    def infoMessage(message: String): Unit =
        push(message, NotifyLevel.Info, None, None)

    /** Info with something to do about it. */
    def infoMessage(message: String, actionLabel: String)(action: => Unit): Unit =
        push(message, NotifyLevel.Info, Some((actionLabel, () => action)), None)

    /** Prefer [[NetworkUtils.handleError]], which translates the throwable first. */
    def errorMessage(error: Throwable): Unit =
        val described = Failures.describe(error)
        push(described.message, NotifyLevel.Error, None, described.detail)

    def errorMessage(message: String, detail: Option[String] = None): Unit =
        push(message, NotifyLevel.Error, None, detail)

    /** An error worth retrying carries the retry. */
    def errorMessage(message: String, detail: Option[String], actionLabel: String)(
        action: => Unit
    ): Unit =
        push(message, NotifyLevel.Error, Some((actionLabel, () => action)), detail)

    private def push(
        message: String,
        level: NotifyLevel,
        action: Option[(String, () => Unit)],
        detail: Option[String]
    ): Unit =
        notifyVar.now().find(n => n.message == message && n.level == level) match
            case Some(existing) =>
                notifyVar.update(
                    _.map(n => if n.id == existing.id then n.copy(count = n.count + 1) else n)
                )
                arm(existing.id, level)
            case None =>
                val id = seq.incrementAndGet()
                val added = Notify(id, message, level, 1, action, detail)
                // takeRight drops the oldest; its timer would otherwise outlive the toast and fire
                // a dismiss for an id that is no longer there.
                notifyVar.update { xs =>
                    val next = xs :+ added
                    next.dropRight(MaxVisible).foreach(dropped => cancel(dropped.id))
                    next.takeRight(MaxVisible)
                }
                arm(id, level)

    /** Info dismisses itself. An error stays until it has been read. */
    private def arm(id: Long, level: NotifyLevel): Unit =
        cancel(id)
        level match
            case NotifyLevel.Error => ()
            case NotifyLevel.Info =>
                timers.updateAndGet(_ + (id -> setTimeout(InfoTtl)(dismiss(id))))

    private def cancel(id: Long): Unit =
        timers.getAndUpdate(_ - id).get(id).foreach(clearTimeout)

    private def dismiss(id: Long): Unit =
        cancel(id)
        notifyVar.update(_.filterNot(_.id == id))

    def render: ReactiveHtmlElement[HTMLDivElement] =
        div(cls := "rr-toasts", children <-- notifyVar.signal.split(_.id)(renderToast))

    private def renderToast(id: Long, item: Notify, itemSignal: Signal[Notify]): HtmlElement =
        div(
            cls := "rr-toast",
            cls("is-error") := item.level == NotifyLevel.Error,
            role := "status",
            item.detail.map(d => title := d),
            span(cls := "rr-toast-dot"),
            span(cls := "rr-toast-text", child.text <-- itemSignal.map(_.message)),
            span(
                cls := "rr-toast-count",
                hidden <-- itemSignal.map(_.count <= 1),
                child.text <-- itemSignal.map(n => s"×${n.count}")
            ),
            item.action.map { case (label, run) =>
                button(
                    cls := "rr-toast-action",
                    tpe := "button",
                    label,
                    onClick --> {
                        run()
                        dismiss(id)
                    }
                )
            },
            button(
                cls := "rr-toast-close",
                tpe := "button",
                aria.label := "Dismiss",
                "×",
                onClick --> dismiss(id)
            )
        )
end NotifyComponent
