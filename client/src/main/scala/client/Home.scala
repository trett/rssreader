package client

import be.doeraene.webcomponents.ui5.configkeys.*
import be.doeraene.webcomponents.ui5.{Button, *}
import client.NetworkUtils.*
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.circe.Decoder
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import ru.trett.rss.models.{ChannelData, FeedItemData}

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}
import scala.language.implicitConversions
import scala.scalajs.js
import scala.util.{Failure, Success, Try}

object Home:

    val refreshFeedsBus: EventBus[Int] = new EventBus
    val markAllAsReadBus: EventBus[Unit] = new EventBus
    private val pageLimit = 20
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private val model = new Model
    import model.*

    given Decoder[FeedItemData] = deriveDecoder
    given Decoder[ChannelData] = deriveDecoder
    given Conversion[LocalDateTime, String] with {
        def apply(date: LocalDateTime): String = dateTimeFormatter.format(date)
    }

    private val itemClickObserver = Observer[Try[List[String]]] {
        case Success(ids) =>
            feedVar.update { feeds =>
                feeds.map { feed =>
                    if (ids.contains(feed.link)) feed.copy(isRead = true) else feed
                }
            }
        case Failure(err) => handleError(err)
    }

    private val feedsObserver =
        feedVar.updater[FeedItemList]((xs1, xs2) => (xs1 ++: xs2).distinctBy(_.link))

    def render: Element = div(
        cls := "cards main-content",
        div(
            onMountBind(ctx =>
                refreshFeedsBus --> { page =>
                    val response = getChannelsAndFeedsRequest(page)
                    val data = response.collectSuccess
                    val errors = response.collectFailure
                    data.addObserver(feedsObserver)(ctx.owner)
                    errors.addObserver(errorObserver)(ctx.owner)
                }
            ),
            div(
                onMountBind(ctx =>
                    markAllAsReadBus --> { _ =>
                        val link = feedVar.now().map(_.link)
                        if (link.nonEmpty) {
                            val response = updateFeedRequest(link)
                            response.addObserver(itemClickObserver)(ctx.owner)
                        }
                    }
                )
            )
        ),
        feeds(),
        div(
            display.flex,
            justifyContent.center,
            marginTop.px := 20,
            marginBottom.px := 20,
            Button(
                _.design := ButtonDesign.Transparent,
                _.icon := IconName.download,
                "More News",
                onClick.mapTo(feedVar.now().size / pageLimit + 1) --> Home.refreshFeedsBus,
                hidden <-- feedVar.signal.map(xs => xs.isEmpty)
            )
        )
    )

    private def feeds(): Element =
        val response = getChannelsAndFeedsRequest(1)
        val data = response.collectSuccess
        val errors = response.collectFailure
        UList(
            _.noDataText := "Nothing to read",
            children <-- feedSignal.split(_.link)(renderItem),
            data --> feedsObserver,
            errors --> errorObserver
        )

    private def renderItem(
        id: String,
        item: FeedItemData,
        itemSignal: Signal[FeedItemData]
    ): HtmlElement = div(
        padding.px := 2,
        Card(
            _.slots.header := CardHeader(
                _.slots.avatar := Icon(_.name := IconName.feed),
                _.titleText <-- itemSignal.map(_.title),
                _.subtitleText <-- itemSignal.map(_.channelTitle),
                _.slots.action <-- itemSignal.map(x =>
                    Icon(_.name := (if (x.isRead) IconName.complete else IconName.pending))
                )
            ),
            UList(
                _.separators := ListSeparator.None,
                _.events.onItemClick
                    .map(_.detail.item.dataset.get("feedLink"))
                    .map(link => List(link.get))
                    .flatMapStream(updateFeedRequest) --> itemClickObserver,
                child <-- itemSignal.map(x =>
                    CustomListItem(
                        div(
                            cls("feed-content"),
                            width.percent := 100,
                            flexWrap.wrap,
                            div(unsafeParseToHtmlFragment(x.description)),
                            div(flexBasis.percent := 100),
                            div(
                                paddingTop.px := 10,
                                paddingBottom.px := 10,
                                display.flex,
                                alignItems.center,
                                justifyContent.spaceBetween,
                                Link(
                                    "Open feed ",
                                    _.href <-- itemSignal.map(_.link),
                                    _.target := LinkTarget._blank,
                                    _.design := LinkDesign.Emphasized,
                                    _.endIcon := IconName.inspect
                                ),
                                Text(
                                    x.pubDate
                                        .atZoneSameInstant(ZoneOffset.UTC)
                                        .toLocalDateTime
                                        .convert
                                )
                            )
                        ),
                        dataAttr("feed-link") := x.link,
                        dataAttr("seen") := x.isRead.toString
                    )
                )
            )
        )
    )

    private def getChannelsAndFeedsRequest(page: Int): EventStream[Try[FeedItemList]] =
        FetchStream
            .withDecoder(responseDecoder[FeedItemList])
            .get(s"$HOST/api/channels/feeds?page=${page}&limit=${pageLimit}")
            .mapSuccess(_.get)

    private def updateFeedRequest(links: List[String]): EventStream[Try[List[String]]] =
        val seen =
            feedSignal.now().filter(feed => links.contains(feed.link)).filter(!_.isRead)
        if (seen.isEmpty) EventStream.empty
        else
            FetchStream
                .withDecoder(responseDecoder[String])
                .post(
                    s"$HOST/api/feeds/read",
                    _.body(links.asJson.toString),
                    _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
                )
                .mapSuccess(_ => seen.map(_.link))
