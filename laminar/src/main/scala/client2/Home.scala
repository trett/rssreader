package client2

import be.doeraene.webcomponents.ui5.Card
import be.doeraene.webcomponents.ui5.CardHeader
import be.doeraene.webcomponents.ui5.CustomListItem
import be.doeraene.webcomponents.ui5.Icon
import be.doeraene.webcomponents.ui5.Link
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
import io.circe.parser.decode
import io.circe.syntax.*
import org.scalajs.dom

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.language.implicitConversions
import scala.scalajs.js
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import client2.NotifyComponent.notifyVar
import client2.NotifyComponent.errorMessage

object Home:

  val refreshFeedsBus: EventBus[Unit] = new EventBus

  private val model = new Model
  import model.*

  given feedDecoder: Decoder[FeedItemData] = deriveDecoder
  given decodeLocalDateTime: Decoder[LocalDateTime] =
    Decoder.decodeString.emapTry { str =>
      Try(
        LocalDateTime.parse(
          str,
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        )
      )
    }
  given localDateTimeToString: Conversion[LocalDateTime, String] with {
    def apply(date: LocalDateTime): String =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(date)
  }

  private val itemClickObserver: Observer[Long] =
    feedVar.updater[Long]((xs, x) =>
      xs.zipWithIndex.map { case (elem, idx) =>
        if (xs(idx).id == x) then xs(idx).copy(read = true) else elem
      }
    )

  private val refreshFeedsObserver = Observer[String] { response =>
    decode[List[FeedItemData]](response).toTry match
      case Success(xs) => feedVar.set(xs)
      case Failure(exception) =>
        notifyVar.update(_ :+ errorMessage(exception.getMessage()))
  }

  def render: Element = div(
    onMountBind(ctx =>
      refreshFeedsBus --> { _ =>
        getFeeds().addObserver(refreshFeedsObserver)(ctx.owner)
      }
    ),
    feeds()
  )

  private def feeds(): Element =
    UList(
      _.noDataText := "Nothing to read",
      children <-- feedSignal.split(_.id)(renderItem),
      feedItemsModifier()
    )

  private def renderItem(
      id: Long,
      item: FeedItemData,
      itemSignal: Signal[FeedItemData]
  ): HtmlElement =
    div(
      Card(
        _.slots.header := CardHeader(
          _.slots.avatar := Icon(_.name := IconName.feed),
          _.titleText <-- itemSignal.map(_.title),
          _.subtitleText <-- itemSignal.map(_.channelTitle),
          _.additionalText <-- itemSignal.map(_.pubDate),
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
            .flatMap(markFeed(_)) --> itemClickObserver,
          child <-- itemSignal.map(x =>
            CustomListItem(
              div(
                display.flex,
                flexWrap.wrap,
                div(
                  unsafeParseToHtmlFragment(x.description)
                ),
                div(flexBasis.percent := 100),
                div(
                  paddingTop.px := 10,
                  paddingBottom.px := 10,
                  Link(
                    "Open feed ",
                    _.href <-- itemSignal.map(_.link),
                    _.target := LinkTarget._blank,
                    _.design := LinkDesign.Emphasized,
                    _.endIcon := IconName.inspect
                  )
                )
              ),
              dataAttr("feed-id") := x.id.toString()
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

  private def getFeeds(): EventStream[String] =
    FetchStream
      .get(s"${HOST}/api/feed/all")
      .recover { case err: Throwable => Option.empty }

  private def feedItemsModifier(): Modifier[HtmlElement] =
    getFeeds() --> { item =>
      item match
        case "" => Router.currentPageVar.set(LoginRoute)
        case value =>
          decode[List[FeedItemData]](value).toTry match
            case Success(xs)        => feedVar.set(xs)
            case Failure(exception) => Router.currentPageVar.set(ErrorRoute)
    }

  private def markFeed(id: String): EventStream[Long] =
    FetchStream
      .post(
        s"${HOST}/api/feed/read",
        _.body(List(id).asJson.toString),
        _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
      )
      .map(_ => id.toLong)
