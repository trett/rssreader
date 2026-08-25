package client

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import ru.trett.rss.models.FeedItemData

/** Right pane. The server stores only the feed's `description`, never the full article text, so
  * this pane is honest about being a preview: cleaned description, image, and a prominent "Open
  * original". Serif at 18px/1.62 because this is the only column meant to be read.
  */
object ArticlePane:

    private val model = AppState.model
    import model.*

    def render(onBack: () => Unit): Element =
        div(
            cls := "rr-article",
            // Keyed on the link, not the item: marking the open article read (or any unrelated
            // feed/channel refresh) re-emits selectedItemSignal, and rebuilding here would reset
            // the reading scroll position and re-download the image.
            child <-- selectedItemSignal.distinctBy(_.map(_.link)).map {
                case None       => empty
                case Some(item) => content(item, onBack)
            }
        )

    private val empty: HtmlElement =
        div(cls := "rr-article-empty", "Select an item to read")

    private def content(item: FeedItemData, onBack: () => Unit): HtmlElement =
        div(
            cls := "rr-article-inner",
            div(
                cls := "rr-article-bar",
                button(cls := "rr-back", "‹ Back", onClick --> onBack()),
                div(
                    cls := "rr-article-bar-feed",
                    span(
                        cls := "rr-dot",
                        styleAttr := s"background:${FeedColors.of(item.channelTitle)}"
                    ),
                    span(item.channelTitle)
                ),
                a(
                    cls := "rr-primary-button",
                    href := item.link,
                    target := "_blank",
                    rel := "noopener noreferrer",
                    "Open original ↗"
                )
            ),
            div(
                cls := "rr-article-scroll",
                div(
                    cls := "rr-article-measure",
                    div(
                        cls := "rr-article-kicker",
                        s"${item.channelTitle} · ${RelativeTime.full(item.pubDate)}"
                    ),
                    h1(cls := "rr-article-title", item.title),
                    item.imageUrl.fold(emptyNode)(Images.decorative(_, "rr-article-image")),
                    Descriptions.fragment(item.description),
                    div(
                        cls := "rr-article-foot",
                        span(cls := "rr-article-source", hostOf(item.link)),
                        div(cls := "rr-kbd-hint", span(cls := "rr-kbd", "j"), span("next unread"))
                    )
                )
            )
        )

    private def hostOf(link: String): String =
        scala.util
            .Try(new org.scalajs.dom.URL(link).host)
            .getOrElse(link)
end ArticlePane
