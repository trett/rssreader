package client2

import be.doeraene.webcomponents.ui5.Card
import be.doeraene.webcomponents.ui5.CardHeader
import be.doeraene.webcomponents.ui5.Icon
import be.doeraene.webcomponents.ui5.UList
import be.doeraene.webcomponents.ui5.configkeys.*
import be.doeraene.webcomponents.ui5.configkeys.WrappingType.Normal
import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.circe.Decoder
import io.circe.generic.semiauto.*
import io.circe.parser.decode
import org.scalajs.dom

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.language.implicitConversions
import scala.scalajs.js
import scala.scalajs.js.URIUtils
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object Home:
  val model = new Model
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

  def render: Element = div(feeds())

  private def feeds(): Element =
    div(children <-- feedSignal.split(_.id)(renderItem), getFeedItems())

  private val wrappingTypeProp = htmlAttr("wrapping-type", StringAsIsCodec)

  private def renderItem(
      id: Long,
      item: FeedItemData,
      itemSignal: Signal[FeedItemData]
  ): HtmlElement =
    div(
      Card(
        _.slots.header := CardHeader(
          _.interactive := true,
          _.slots.avatar := Icon(_.name := IconName.feed),
          _.titleText <-- itemSignal.map(_.title),
          _.subtitleText <-- itemSignal.map(_.channelTitle),
          _.additionalText <-- itemSignal.map(_.pubDate)
        ),
        UList(
          _.separators := ListSeparator.None,
          child <-- itemSignal.map(x =>
            UList.item(
              unsafeParseToHtmlFragment(x.description),
              wrappingTypeProp := Normal.value
            )
          )
        )
      )
    )

  private def unsafeParseToHtmlFragment(html: String): HtmlElement =
    div(
      DomApi
        .unsafeParseHtmlStringIntoNodeArray(URIUtils.decodeURIComponent(html))
        .collect { case a: dom.html.Element => a }
        .map(foreignHtmlElement(_))
    )

  private def getFeedItems(): Binder.Base =
    FetchStream
      .get("https://localhost/api/feed/all")
      .recover { case err: Throwable => Option.empty } --> { item =>
      item match
        case "" => Router.currentPageVar.set(LoginRoute)
        case value =>
          decode[List[FeedItemData]](value).toTry match
            case Success(xs)        => feedVar.set(xs)
            case Failure(exception) => Router.currentPageVar.set(ErrorRoute)
    }
