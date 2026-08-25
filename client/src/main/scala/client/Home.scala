package client

import client.NetworkUtils.*
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import io.circe.syntax.*
import ru.trett.rss.models.FeedItemData

import scala.util.{Failure, Success, Try}

/** Three panes on desktop, one at a time on a phone. All the network plumbing and buses from the
  * previous version are kept as-is — what changed is the rendering and that selecting a row both
  * marks it read and loads it into the reading pane.
  */
object Home:

    val refreshFeedsBus: EventBus[Int] = new EventBus
    val markAllAsReadBus: EventBus[Unit] = new EventBus
    val refreshUnreadCountBus: EventBus[Unit] = new EventBus

    /** Read+unread total. Only new feeds can move it, so it is refreshed after "Update feeds", not
      * after every mark-read.
      */
    val refreshTotalCountBus: EventBus[Unit] = new EventBus
    private val pageLimit = 20

    private val model = AppState.model
    import model.*
    import Decoders.given

    private val itemClickObserver = Observer[Try[List[String]]] {
        case Success(ids) =>
            feedVar.update { feeds =>
                feeds.map(feed =>
                    if ids.contains(feed.link) then feed.copy(isRead = true) else feed
                )
            }
            // Only the unread counts can have moved. The channel list and the read+unread total
            // are both invariant under marking an item read, so neither is refetched here.
            EventBus.emit(refreshUnreadCountBus -> (), Sidebar.refreshCountsBus -> ())
        case Failure(err) => handleError(err)
    }

    private val feedResponseObserver = Observer[Try[FeedItemList]] {
        case Success(xs) =>
            feedVar.update(existing => (existing ++: xs).distinctBy(_.link))
            hasMoreVar.set(xs.size == pageLimit)
        case Failure(err) => reportLoadFailure(err)
    }

    /** A failed page load is the one error the reader can act on directly, so it carries the retry
      * rather than only being announced. Anything not worth retrying — an expired session, a
      * malformed response — goes back to [[handleError]], which navigates or reports as before.
      */
    private def reportLoadFailure(err: Throwable): Unit =
        val described = Failures.describe(err)
        if !described.retriable then handleError(err)
        else
            org.scalajs.dom.console.error(err)
            NotifyComponent.errorMessage(described.message, described.detail, "Try again") {
                refreshFeedsBus.emit(1)
            }

    private val unreadCountObserver = Observer[Try[Int]] {
        case Success(count) => unreadCountVar.set(count)
        case Failure(err)   => handleError(err)
    }

    private val totalCountObserver = Observer[Try[Int]] {
        case Success(count) => totalCountVar.set(count)
        case Failure(err)   => handleError(err)
    }

    def render: Element =
        div(
            cls := "rr-app",
            cls("pane-feeds") <-- paneSignal.map(_ == Pane.Feeds),
            cls("pane-list") <-- paneSignal.map(_ == Pane.List),
            cls("pane-article") <-- paneSignal.map(_ == Pane.Article),
            plumbing,
            keyboard,
            Sidebar.render,
            div(
                cls := "rr-list-column",
                FeedList.render(select, markAllRead),
                div(
                    cls := "rr-more",
                    button(
                        cls := "rr-ghost-button",
                        "More news",
                        onClick.mapTo(
                            (feedVar.now().size + pageLimit - 1) / pageLimit + 1
                        ) --> refreshFeedsBus,
                        hidden <-- feedSignal.combineWith(hasMoreSignal).map {
                            case (feeds, hasMore) => feeds.isEmpty || !hasMore
                        }
                    )
                )
            ),
            ArticlePane.render(() => paneVar.set(Pane.List)),
            mobileTabs
        )

    /** Marking read is the same request the old UI5 `onItemClick` made. */
    private def select(item: FeedItemData): Unit =
        selectedVar.set(Some(item.link))
        if Responsive.isMobile then paneVar.set(Pane.Article)
        markRead(List(item.link))

    private def markRead(links: List[String]): Unit =
        markReadBus.emit(links)

    private val markReadBus: EventBus[List[String]] = new EventBus

    private def markAllRead(): Unit = EventBus.emit(markAllAsReadBus -> ())

    /** One long-lived subscription per concern. The earlier shape called `addObserver` on the mount
      * owner each time a bus fired, which never released the previous one — those piled up for the
      * life of the page.
      */
    private val plumbing: Modifier[HtmlElement] =
        List(
            onMountBind(_ =>
                EventStream
                    .merge(EventStream.fromValue(1), refreshFeedsBus.events)
                    .flatMapSwitch(getChannelsAndFeedsRequest) --> feedResponseObserver
            ),
            // Only "All items" changes what the server sends; the other scopes filter what is
            // already loaded. So refetch from page 1 exactly when that boundary is crossed.
            onMountBind(_ =>
                scopeSignal.map(_.includesRead).changes.distinct --> { _ =>
                    feedVar.set(List.empty)
                    hasMoreVar.set(true)
                    refreshFeedsBus.emit(1)
                }
            ),
            onMountBind(_ =>
                markReadBus.events.flatMapSwitch(updateFeedRequest) --> itemClickObserver
            ),
            onMountBind(_ =>
                EventStream
                    .merge(EventStream.unit(), refreshUnreadCountBus.events)
                    .flatMapSwitch(_ => getUnreadCountRequest()) --> unreadCountObserver
            ),
            // The read+unread total only moves when new feeds arrive, not when they are read.
            onMountBind(_ =>
                EventStream
                    .merge(EventStream.unit(), refreshTotalCountBus.events)
                    .flatMapSwitch(_ => getTotalCountRequest()) --> totalCountObserver
            ),
            // Observed once per mount, not once per event: `Signal.observe` registers a fresh
            // subscription on the owner every call, and the owner lives as long as the page.
            onMountBind { ctx =>
                val visible = visibleFeedSignal.observe(ctx.owner)
                markAllAsReadBus --> { _ =>
                    val links = visible.now().filter(!_.isRead).map(_.link)
                    if links.nonEmpty then markReadBus.emit(links)
                }
            }
        )

    /** j/k move, o opens, m marks read, r refreshes. Ignored while typing in a field. */
    private val keyboard: Modifier[HtmlElement] =
        onMountBind { ctx =>
            // Observed once, outside the handler — see the note in `plumbing`. Doing this per
            // keydown left a permanent subscription behind on every keystroke.
            val visible = visibleFeedSignal.observe(ctx.owner)
            val selected = selectedItemSignal.observe(ctx.owner)
            documentEvents(_.onKeyDown) --> { e =>
                val target = Option(e.target.asInstanceOf[org.scalajs.dom.Element])
                val typing = target.exists(t =>
                    t.tagName == "INPUT" || t.tagName == "TEXTAREA" || t.tagName == "UI5-INPUT"
                )
                if !typing && !e.metaKey && !e.ctrlKey && !e.altKey then
                    val items = visible.now()
                    val current = selected.now()
                    val idx = current.fold(-1)(c => items.indexWhere(_.link == c.link))
                    e.key match
                        case "j" | "ArrowDown" =>
                            items.lift(math.min(idx + 1, items.size - 1)).foreach { it =>
                                e.preventDefault(); select(it)
                            }
                        case "k" | "ArrowUp" =>
                            items.lift(math.max(idx - 1, 0)).foreach { it =>
                                e.preventDefault(); select(it)
                            }
                        case "o" | "Enter" =>
                            current.foreach(it => org.scalajs.dom.window.open(it.link, "_blank"))
                        case "m" =>
                            current.filter(!_.isRead).foreach(it => markRead(List(it.link)))
                        case "r" =>
                            EventBus.emit(refreshFeedsBus -> 1)
                        case "Escape" =>
                            if Responsive.isMobile then paneVar.set(Pane.List)
                        case _ => ()
            }
        }

    /** Phone only — hidden by CSS above 767px. */
    private val mobileTabs: Element =
        div(
            cls := "rr-tabs",
            tab("Unread", Pane.List),
            tab("Feeds", Pane.Feeds),
            tab("Article", Pane.Article)
        )

    private def tab(label: String, pane: Pane): HtmlElement =
        div(
            cls := "rr-tab",
            cls("is-active") <-- paneSignal.map(_ == pane),
            span(cls := "rr-tab-dot"),
            span(label),
            onClick --> paneVar.set(pane)
        )

    private def filterNews: Boolean = settingsSignal.now().exists(_.filterNews)

    private def getChannelsAndFeedsRequest(page: Int): EventStream[Try[FeedItemList]] =
        val filterParam = if filterNews then "&filter=important" else ""
        val stateParam = if scopeSignal.now().includesRead then "&state=all" else ""
        FetchStream
            .withDecoder(responseDecoder[FeedItemList])
            .get(s"/api/channels/feeds?page=${page}&limit=${pageLimit}${filterParam}${stateParam}")
            .mapSuccess(_.get)

    private def updateFeedRequest(links: List[String]): EventStream[Try[List[String]]] =
        val seen = feedSignal.now().filter(feed => links.contains(feed.link)).filter(!_.isRead)
        if seen.isEmpty then EventStream.empty
        else
            FetchStream
                .withDecoder(responseDecoder[String])
                .post(
                    "/api/feeds/read",
                    _.body(links.asJson.toString),
                    _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
                )
                .mapSuccess(_ => seen.map(_.link))

    private def getUnreadCountRequest(): EventStream[Try[Int]] =
        val filterParam = if filterNews then "?filter=important" else ""
        FetchStream
            .withDecoder(responseDecoder[Int])
            .get(s"/api/feeds/unread/total$filterParam")
            .mapSuccess(_.get)

    private def getTotalCountRequest(): EventStream[Try[Int]] =
        val filterParam = if filterNews then "?filter=important" else ""
        FetchStream
            .withDecoder(responseDecoder[Int])
            .get(s"/api/feeds/total$filterParam")
            .mapSuccess(_.get)
end Home
