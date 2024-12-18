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
import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.circe.Decoder
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import org.scalajs.dom

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.language.implicitConversions
import scala.scalajs.js
import scala.util.Try
import client2.NetworkUtils.responseDecoder
import client2.NetworkUtils.errorObserver

object Home:

  val refreshFeedsBus: EventBus[Unit] = new EventBus

  private val dateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  private val model = new Model
  import model.*

  given feedDecoder: Decoder[FeedItemData] = deriveDecoder

  given decodeLocalDateTime: Decoder[LocalDateTime] =
    Decoder.decodeString.emapTry { str =>
      Try(LocalDateTime.parse(str, dateTimeFormatter))
    }

  given localDateTimeToString: Conversion[LocalDateTime, String] with {
    def apply(date: LocalDateTime): String = dateTimeFormatter.format(date)
  }

  private val itemClickObserver: Observer[Long] =
    feedVar.updater[Long]((xs, x) =>
      xs.zipWithIndex.map { case (elem, idx) =>
        if (xs(idx).id == x) then xs(idx).copy(read = true) else elem
      }
    )

  private val feedObserver = Observer[Option[List[FeedItemData]]] {
    case Some(feeds) => feedVar.set(feeds)
    case None        => Router.currentPageVar.set(ErrorRoute)
  }

  def render: Element = div(
    onMountBind(ctx =>
      refreshFeedsBus --> { _ =>
        val response = getFeedsRequest()
        val data = response.collectSuccess
        val errors = response.collectFailure
        data.addObserver(feedObserver)(ctx.owner)
        errors.addObserver(errorObserver)(ctx.owner)
      }
    ),
    feeds()
  )

  private def feeds(): Element =
    val response = getFeedsRequest()
    val data = response.collectSuccess
    val errors = response.collectFailure
    UList(
      _.noDataText := "Nothing to read",
      children <-- feedSignal.split(_.id)(renderItem),
      data --> feedObserver,
      errors --> errorObserver
    )

  private def renderItem(
      id: Long,
      item: FeedItemData,
      itemSignal: Signal[FeedItemData]
  ): HtmlElement =
    div(
      padding.px := 2,
      Card(
        _.slots.header := CardHeader(
          _.slots.avatar := Icon(_.name := IconName.feed),
          _.titleText <-- itemSignal.map(_.title),
          _.subtitleText <-- itemSignal.map(_.channelTitle),
          _.slots.action <-- itemSignal.map(x =>
            Icon(
              _.name := (if (x.read) IconName.complete else IconName.pending)
            )
          )
        ),
        UList(
          _.separators := ListSeparator.None,
          _.events.onItemClick
            .map(_.detail.item.dataset.get("feedId"))
            .map(_.get)
            .flatMapStream(updateFeedRequest(_)) --> itemClickObserver,
          child <-- itemSignal.map(x =>
            CustomListItem(
              div(
                cls("feed-content"),
                width.percent := 100,
                flexWrap.wrap,
                div(
                  unsafeParseToHtmlFragment(x.description)
                ),
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
              dataAttr("feed-id") := x.id.toString(),
              dataAttr("seen") := x.read.toString()
            )
          )
        )
      )
    )

  private def unsafeParseToHtmlFragment(html: String): HtmlElement =
    div(
      DomApi
        .unsafeParseHtmlStringIntoNodeArray(html)
        .flatMap {
          case el: dom.html.Element => Some(el)
          case raw                  => Some(div(raw.textContent).ref)
        }
        .filter(!_.textContent.isEmpty())
        .map(foreignHtmlElement(_))
    )

  private def getFeedsRequest(): EventStream[Try[Option[List[FeedItemData]]]] =
    FetchStream
      .withDecoder(responseDecoder[List[FeedItemData]])
      .get(s"${HOST}/api/feed/all")

  private def updateFeedRequest(id: String): EventStream[Long] =
    val seen =
      feedSignal.now().find(_.id == id.toLong).map(_.read).getOrElse(true)
    if (seen) EventStream.empty
    else
      val responses = FetchStream
        .withDecoder(responseDecoder[String])
        .post(
          s"${HOST}/api/feed/read",
          _.body(List(id).asJson.toString),
          _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
        )
      val result = responses.collectSuccess
      val errors = responses.collectFailure // TODO
      result.mapTo(id.toLong)
