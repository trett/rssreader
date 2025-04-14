package client2

import be.doeraene.webcomponents.ui5.Card
import be.doeraene.webcomponents.ui5.CardHeader
import be.doeraene.webcomponents.ui5.CustomListItem
import be.doeraene.webcomponents.ui5.Icon
import be.doeraene.webcomponents.ui5.Link
import be.doeraene.webcomponents.ui5.Text
import be.doeraene.webcomponents.ui5.UList
import be.doeraene.webcomponents.ui5.configkeys.*
import client2.NetworkUtils.HOST
import client2.NetworkUtils.JSON_ACCEPT
import client2.NetworkUtils.JSON_CONTENT_TYPE
import client2.NetworkUtils.errorObserver
import client2.NetworkUtils.handleError
import client2.NetworkUtils.responseDecoder
import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.circe.Decoder
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import org.scalajs.dom
import ru.trett.rss.models.{FeedItemData, ChannelData}

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import scala.language.implicitConversions
import scala.scalajs.js
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object Home:

    val refreshFeedsBus: EventBus[Unit] = new EventBus
    val markAllAsReadBus: EventBus[Unit] = new EventBus

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private val model = new Model
    import model.*

    // given Decoder[FeedItemData] = deriveDecoder
    given Decoder[FeedItemData] = deriveDecoder
    given Decoder[ChannelData] = deriveDecoder
    given Conversion[OffsetDateTime, String] with {
        def apply(date: OffsetDateTime): String = dateTimeFormatter.format(date)
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

    private val feedsObserver = Observer[FeedItemList](feedVar.set(_))

    def render: Element = div(
        onMountBind(ctx =>
            refreshFeedsBus --> { _ =>
                val response = getChannelsRequest()
                val data = response.collectSuccess
                val errors = response.collectFailure
                data.addObserver(feedsObserver)(ctx.owner)
                errors.addObserver(errorObserver)(ctx.owner)
            }
            markAllAsReadBus --> { _ =>
                val link = feedVar.now().map(_.link.toString)
                if (link.nonEmpty) {
                    val response = updateFeedRequest(link)
                    response.addObserver(itemClickObserver)(ctx.owner)
                }
            }
        ),
        feeds()
    )

    // private def mapChannelTitle(s: ChannelList): FeedItemList =
    //     s.flatMap { case Channel(id, title, link, feedItems) =>
    //         feedItems.map(item =>
    //             FeedItemData(
    //                 item.link,
    //                 title,
    //                 item.title,
    //                 item.description,
    //                 item.pubDate.get,
    //                 item.isRead
    //             )
    //         )
    //     }

    private def feeds(): Element =
        val response = getChannelsRequest()
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
                    .flatMapStream(updateFeedRequest(_)) --> itemClickObserver,
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
                                Text(x.pubDate.convert)
                            )
                        ),
                        dataAttr("feed-link") := x.link,
                        dataAttr("seen") := x.isRead.toString()
                    )
                )
            )
        )
    )

    private def unsafeParseToHtmlFragment(html: String): HtmlElement = div(
        DomApi
            .unsafeParseHtmlStringIntoNodeArray(html)
            .flatMap {
                case el: dom.html.Element => Some(el)
                case raw                  => Some(div(raw.textContent).ref)
            }
            .filter(!_.textContent.isEmpty())
            .map(foreignHtmlElement(_))
    )

    private def getChannelsRequest(): EventStream[Try[FeedItemList]] =
        FetchStream
            .withDecoder(responseDecoder[FeedItemList])
            .get(s"${HOST}/api/channels")
            .mapSuccess(_.get)

    private def updateFeedRequest(links: List[String]): EventStream[Try[List[String]]] =
        val seen =
            feedSignal.now().filter(feed => links.contains(feed.link.toString)).filter(!_.isRead)
        if (seen.isEmpty) EventStream.empty
        else
            FetchStream
                .withDecoder(responseDecoder[String])
                .post(
                    s"${HOST}/api/feeds/read",
                    _.body(links.asJson.toString),
                    _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
                )
                .mapSuccess(_ => seen.map(_.link))
