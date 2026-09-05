package client

import be.doeraene.webcomponents.ui5.Icon
import be.doeraene.webcomponents.ui5.Input
import be.doeraene.webcomponents.ui5.configkeys.IconName
import be.doeraene.webcomponents.ui5.configkeys.InputType
import client.NetworkUtils.*
import com.raquo.laminar.api.L.*
import io.circe.Decoder
import io.circe.generic.semiauto.*
import ru.trett.rss.models.ChannelData

import org.scalajs.dom
import scala.util.Try

/** Left navigation pane: feed search, the user's channels and the label filters. */
object Sidebar:

    private val model = AppState.model
    import model.*

    given Decoder[ChannelData] = deriveDecoder

    def render: Element =
        div(
            cls := "sidebar-backdrop",
            cls("is-open") <-- sidebarOpenSignal,
            onClick.mapTo(false) --> sidebarOpenVar,
            div(
                cls := "sidebar",
                onClick.stopPropagation --> Observer.empty,
                onMountBind(ctx =>
                    val response = channelsRequest
                    response.collectFailure.addObserver(errorObserver)(ctx.owner)
                    response.collectSuccess --> channelVar
                ),
                div(
                    cls := "sidebar-search",
                    Input(
                        _.tpe := InputType.Text,
                        _.placeholder := "Search feeds",
                        _.slots.icon := Icon(_.name := IconName.search),
                        _.value <-- sidebarQuerySignal,
                        _.events.onInput.mapToValue --> sidebarQueryVar
                    )
                ),
                div(cls := "sidebar-section-title", "My feeds"),
                div(
                    cls := "sidebar-list",
                    navItem(
                        FeedFilter.AllFeeds,
                        Icon(cls := "sidebar-icon", _.name := IconName.feed),
                        "All Feeds"
                    ),
                    children <-- visibleChannels.map(_.map(channelItem))
                ),
                div(cls := "sidebar-section-title", "Labels"),
                div(
                    cls := "sidebar-list",
                    navItem(
                        FeedFilter.Important,
                        Icon(cls := "sidebar-icon", _.name := IconName.tags),
                        "Important"
                    )
                )
            )
        )

    private def visibleChannels: Signal[ChannelList] =
        channelSignal.combineWith(sidebarQuerySignal).map { (channels, query) =>
            val q = query.trim.toLowerCase
            val matching =
                if q.isEmpty then channels
                else channels.filter(_.title.toLowerCase.contains(q))
            matching.sortBy(_.title.toLowerCase)
        }

    private def channelItem(channel: ChannelData): Element =
        navItem(FeedFilter.Channel(channel.id, channel.title), favicon(channel.link), channel.title)

    /** Site icon taken straight from the feed's own host, falling back to a generic RSS glyph. */
    private def favicon(link: String): Element =
        def fallback = Icon(cls := "sidebar-icon", _.name := IconName.feed)
        faviconUrl(link) match
            case None => fallback
            case Some(url) =>
                val failed = Var(false)
                div(
                    cls := "sidebar-icon-slot",
                    child <-- failed.signal.map { broken =>
                        if broken then fallback
                        else
                            img(
                                cls := "sidebar-icon sidebar-favicon",
                                src := url,
                                alt := "",
                                onError.mapTo(true) --> failed
                            )
                    }
                )

    /** Most feed hosts do not serve a usable /favicon.ico, so resolve icons through DuckDuckGo. */
    private def faviconUrl(link: String): Option[String] =
        Try(new dom.URL(link)).toOption
            .map(_.host)
            .filter(_.nonEmpty)
            .map(host => s"https://icons.duckduckgo.com/ip3/$host.ico")

    private def navItem(filter: FeedFilter, icon: Element, label: String): Element =
        a(
            cls := "sidebar-item",
            cls("is-active") <-- feedFilterSignal.map(sameFilter(_, filter)),
            icon,
            span(cls := "sidebar-item-label", label),
            onClick.preventDefault.mapTo(filter) --> selectFilter
        )

    private def sameFilter(current: FeedFilter, item: FeedFilter): Boolean =
        (current, item) match
            case (FeedFilter.Channel(a, _), FeedFilter.Channel(b, _)) => a == b
            case (a, b)                                               => a == b

    private val selectFilter: Observer[FeedFilter] = Observer[FeedFilter] { filter =>
        if !sameFilter(feedFilterSignal.now(), filter) then feedFilterVar.set(filter)
        sidebarOpenVar.set(false)
    }

    private def channelsRequest: EventStream[Try[ChannelList]] =
        FetchStream
            .withDecoder(responseDecoder[ChannelList])
            .get("/api/channels")
            .mapSuccess(_.get)
