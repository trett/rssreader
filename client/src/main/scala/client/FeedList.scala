package client

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import ru.trett.rss.models.FeedItemData

/** Middle pane: uniform rows, ~95px each, so a screen shows eight items instead of three. Row
  * anatomy is fixed — accent dot, feed name, relative time, title, two-line dek, optional 58px
  * thumbnail — which is what makes the list scannable. The whole row is the click target; there is
  * no per-row "Open feed" link competing with the headline.
  */
object FeedList:

    private val model = AppState.model
    import model.*

    def render(onSelect: FeedItemData => Unit, markAllRead: () => Unit): Element =
        div(
            cls := "rr-list",
            div(
                cls := "rr-list-header",
                div(
                    cls := "rr-list-header-text",
                    div(
                        cls := "rr-list-title",
                        child.text <-- scopeSignal
                            .combineWith(channelSignal)
                            .map {
                                case (Scope.Unread, _)   => "Unread"
                                case (Scope.AllItems, _) => "All items"
                                case (Scope.Starred, _)  => "Starred"
                                case (Scope.Channel(id), channels) =>
                                    channels.find(_.id == id).map(_.title).getOrElse("Unread")
                            }
                    ),
                    div(
                        cls := "rr-list-meta",
                        child.text <-- visibleFeedSignal
                            .combineWith(channelSignal)
                            .map { case (feeds, channels) =>
                                val unread = feeds.count(!_.isRead)
                                s"$unread unread · ${channels.size} feeds"
                            }
                    )
                ),
                button(cls := "rr-ghost-button", "Mark read", onClick --> markAllRead())
            ),
            div(
                cls := "rr-list-scroll",
                children <-- visibleFeedSignal.split(_.link)(renderRow(onSelect)),
                div(
                    cls := "rr-list-empty",
                    hidden <-- visibleFeedSignal.map(_.nonEmpty),
                    "Nothing to read"
                )
            )
        )

    /** Only `isRead` and `highlighted` ever change on a loaded item, so everything else is rendered
      * once from the initial value. Binding the title, dek or thumbnail to `itemSignal` instead
      * would re-parse the description HTML and rebuild the `<img>` — restarting its download —
      * every time a row was marked read.
      */
    private def renderRow(
        onSelect: FeedItemData => Unit
    )(link: String, item: FeedItemData, itemSignal: Signal[FeedItemData]): HtmlElement =
        val accent = FeedColors.of(item.channelTitle)
        articleTag(
            cls := "rr-row",
            cls("is-read") <-- itemSignal.map(_.isRead),
            cls("is-selected") <-- selectedLinkSignal.map(_.contains(link)),
            cls("is-highlighted") <-- itemSignal.map(_.highlighted),
            dataAttr("feed-link") := link,
            onClick --> { onSelect(item) },
            div(
                cls := "rr-row-main",
                div(
                    cls := "rr-row-body",
                    div(
                        cls := "rr-row-meta",
                        span(
                            cls := "rr-dot",
                            styleAttr <-- itemSignal.map(_.isRead).distinct.map { isRead =>
                                if isRead then "background:transparent;border:1px solid #cdc9c2"
                                else s"background:$accent"
                            }
                        ),
                        span(cls := "rr-row-feed", item.channelTitle),
                        span(cls := "rr-sep", "·"),
                        span(cls := "rr-row-time", RelativeTime.short(item.pubDate))
                    ),
                    h3(cls := "rr-row-title", item.title),
                    p(cls := "rr-row-dek", Descriptions.plain(item.description))
                ),
                item.imageUrl.fold(emptyNode)(Images.decorative(_, "rr-row-thumb"))
            )
        )
end FeedList
