package client

import client.NetworkUtils.responseDecoder
import com.raquo.airstream.eventbus.EventBus
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import ru.trett.rss.models.ChannelData

import scala.util.{Failure, Success, Try}

/** Left pane: the feed list. Replaces the UI5 ShellBar — the actions that lived in its profile
  * popover (Settings, Update feeds, Sign out) are in the footer here, where they are visible
  * instead of hidden behind an avatar.
  */
object Sidebar:

    /** Channel list and counts — after adding, deleting or refreshing feeds. */
    val refreshChannelsBus: EventBus[Unit] = new EventBus

    /** Counts only. Marking an item read cannot change the channel list, so it uses this rather
      * than paying for a `/api/channels` round trip per click.
      */
    val refreshCountsBus: EventBus[Unit] = new EventBus

    private val model = AppState.model
    import model.*

    private val channelsObserver = Observer[Try[ChannelList]] {
        case Success(channels) => channelVar.set(channels)
        case Failure(err)      => NetworkUtils.handleError(err)
    }

    private val unreadByChannelObserver = Observer[Try[Map[Long, Int]]] {
        case Success(counts) => unreadByChannelVar.set(counts)
        case Failure(err)    => NetworkUtils.handleError(err)
    }

    def render: Element =
        div(
            cls := "rr-sidebar",
            onMountBind(_ =>
                EventStream
                    .merge(EventStream.unit(), refreshChannelsBus.events)
                    .flatMapSwitch(_ => NetworkUtils.getChannels()) --> channelsObserver
            ),
            onMountBind(_ =>
                EventStream
                    .merge(EventStream.unit(), refreshChannelsBus.events, refreshCountsBus.events)
                    .flatMapSwitch(_ => getUnreadByChannelRequest()) --> unreadByChannelObserver
            ),
            div(cls := "rr-brand", span(cls := "rr-brand-dot"), span("RSS Reader")),
            div(
                cls := "rr-sidebar-scroll",
                div(
                    cls := "rr-nav",
                    navRow("Unread", Scope.Unread, unreadCountSignal.map(countLabel)),
                    navRow("All items", Scope.AllItems, totalCountSignal.map(countLabel)),
                    navRow("Starred", Scope.Starred, starredUnreadSignal)
                        .amend(cls("is-muted") <-- channelSignal.map(!_.exists(_.highlighted)))
                ),
                // distinct: a refresh usually returns an equal list, and without this every
                // refresh would tear down and rebuild every row and its subscriptions.
                children <-- channelSignal.distinct.map { channels =>
                    sections(channels).flatMap { (label, grouped) =>
                        List(
                            div(cls := "rr-nav-label", label),
                            div(cls := "rr-nav", grouped.map(renderChannel))
                        )
                    }
                }
            ),
            div(
                cls := "rr-sidebar-footer",
                footerAction(
                    "Update feeds",
                    onClick.flatMap(_ => NetworkUtils.refreshFeeds()) --> { _ =>
                        feedVar.set(List.empty)
                        EventBus.emit(
                            Home.refreshFeedsBus -> 1,
                            Home.refreshUnreadCountBus -> (),
                            Home.refreshTotalCountBus -> (),
                            refreshChannelsBus -> ()
                        )
                    }
                ),
                footerAction(
                    "Settings",
                    onClick --> { Router.currentPageVar.set(Some(SettingsRoute)) }
                ),
                footerAction(
                    "Sign out",
                    onClick.flatMap(_ => NetworkUtils.logout()) --> { _ =>
                        Router.currentPageVar.set(Some(LoginRoute))
                    }
                )
            )
        )

    private def footerAction(label: String, mods: Modifier[HtmlElement]*): HtmlElement =
        div(cls := "rr-footer-action", tabIndex := 0, label, mods)

    /** Zero reads as absence, not as "0". */
    private def countLabel(n: Int): String = if n > 0 then n.toString else ""

    /** Unread items sitting in channels the user starred. Summed from the per-channel counts the
      * sidebar already fetches, so starring does not cost an extra request.
      */
    private val starredUnreadSignal: Signal[String] =
        channelSignal.combineWith(unreadByChannelSignal).map { (channels, counts) =>
            countLabel(channels.filter(_.highlighted).map(ch => counts.getOrElse(ch.id, 0)).sum)
        }

    private def navRow(
        label: String,
        scope: Scope,
        count: Signal[String],
        accent: Option[String] = None,
        starred: Boolean = false
    ): HtmlElement =
        div(
            cls := "rr-nav-row",
            cls("is-active") <-- scopeSignal.map(_ == scope),
            accent.map(color => span(cls := "rr-dot", styleAttr := s"background:$color")),
            span(cls := "rr-nav-title", label),
            // Starring itself lives in Settings; the sidebar only reflects it.
            if starred then span(cls := "rr-nav-star", title := "Starred", "★") else emptyNode,
            span(cls := "rr-nav-count", child.text <-- count),
            onClick --> {
                scopeVar.set(scope)
                selectedVar.set(None)
                if Responsive.isMobile then paneVar.set(Pane.List)
            }
        )

    /** Named folders first, alphabetically, then whatever is unfiled.
      *
      * With no folders in play this collapses to a single section under the original "Feeds"
      * heading — the flat list exactly as before. Once folders exist the leftovers are called
      * "Ungrouped", so "Feeds" never sits alongside real folder names as if it were one.
      */
    private def sections(channels: ChannelList): List[(String, ChannelList)] =
        val byFolder = channels.groupBy(_.folder)
        val named = byFolder
            .collect { case (Some(name), grouped) => name -> grouped }
            .toList
            .sortBy((name, _) => name.toLowerCase)
        val unfiled = byFolder.getOrElse(None, List.empty)
        val unfiledLabel = if named.isEmpty then "Feeds" else "Ungrouped"
        named ++ Option.when(unfiled.nonEmpty)(unfiledLabel -> unfiled)

    private def renderChannel(channel: ChannelData): HtmlElement =
        val unread = unreadByChannelSignal.map(_.getOrElse(channel.id, 0)).distinct
        navRow(
            channel.title,
            Scope.Channel(channel.id),
            unread.map(countLabel),
            Some(FeedColors.of(channel.title)),
            channel.highlighted
        ).amend(cls("is-muted") <-- unread.map(_ == 0))

    private def getUnreadByChannelRequest(): EventStream[Try[Map[Long, Int]]] =
        FetchStream
            .withDecoder(responseDecoder[Map[Long, Int]])
            .get("/api/feeds/unread/by-channel")
            .mapSuccess(_.getOrElse(Map.empty))

end Sidebar
