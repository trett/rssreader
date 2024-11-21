package client2

import be.doeraene.webcomponents.ui5.UList
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.circe.Decoder
import io.circe.generic.semiauto.*
import io.circe.parser.decode

import scala.language.implicitConversions
import scala.util.Failure
import scala.util.Success
import be.doeraene.webcomponents.ui5.CheckBox
import be.doeraene.webcomponents.ui5.Label
import be.doeraene.webcomponents.ui5.StepInput
import scala.util.Try

object SettingsPage {

  val model = new Model
  import model.*

  given channelDecoder: Decoder[ChannelData] = deriveDecoder
  given settingsDecoder: Decoder[SettingsData] = deriveDecoder
  private val deletedChannelBus: EventBus[String] = new EventBus

  val deleteChannel: Modifier[HtmlElement] =
    onMountBind(ctx =>
      deletedChannelBus --> { id =>
        FetchStream
          .post(
            "https://localhost/api/channel/delete",
            _.body(id),
            _.headers(
              "Accept" -> "application/json",
              "Content-Type" -> "application/json"
            )
          )
          .recover { case err: Throwable => Some(err.getMessage) }
          .map(x => Try(x.toLong))
          .addObserver(
            channelVar.updater((xs, x) =>
              x match
                case Success(value) => xs.filterNot(_.id == value)
                case Failure(ex)    => List()
            )
          )(ctx.owner)
      }
    )

  def render: Element =
    div(
      width := "fit-content",
      settings(),
      br(),
      channels()
    )

  private def settings(): HtmlElement =
    div(
      display := "grid",
      Label("Hide read", _.forId := "hide-read-cb", _.showColon := true),
      CheckBox(
        _.id := "hide-read-cb",
        _.checked <-- settingsSignal.map(x => x.map(_.hideRead).getOrElse(true))
      ),
      br(),
      div(
        display := "grid",
        Label(
          "Days to keep",
          _.forId := "days-to-keep-cmb",
          _.showColon := true
        ),
        StepInput(
          _.id := "days-to-keep-cmb",
          _.value <-- settingsSignal.map(x =>
            x.map(_.deleteAfter.toDouble).getOrElse(3)
          ),
          _.min := 1,
          _.max := 14,
          _.step := 1
        )
      ),
      getSettings()
    )

  private def channels(): HtmlElement =
    div(
      display := "grid",
      deleteChannel,
      Label("Channels", _.forId := "channels-list", _.showColon := true),
      UList(
        _.id := "channels-list",
        _.events.onItemDelete
          .map(
            _.detail.item.dataset.get("channelId")
          ) --> deletedChannelBus.writer.contramap[Option[String]](_.get),
        _.selectionMode := ListMode.Delete,
        children <-- channelSignal.split(_.id)(renderChannel)
      ),
      getChannels()
    )

  private def renderChannel(
      id: Long,
      item: ChannelData,
      itemSignal: Signal[ChannelData]
  ) =
    UList.item(
      _.icon := IconName.list,
      dataAttr("channel-id") <-- itemSignal.map(_.id.text),
      child <-- itemSignal.map(_.title)
    )

  private def getChannels(): Binder.Base =
    FetchStream
      .get("https://localhost/api/channel/all")
      .recover { case err: Throwable => Option.empty } --> { item =>
      item match
        case "" => Router.currentPageVar.set(LoginRoute)
        case value =>
          decode[List[ChannelData]](value).toTry match
            case Success(xs)        => channelVar.set(xs)
            case Failure(exception) => Router.currentPageVar.set(ErrorRoute)
    }

  private def getSettings(): Binder.Base =
    FetchStream
      .get("https://localhost/api/settings")
      .recover { case err: Throwable => Option.empty } --> { item =>
      item match
        case "" => Router.currentPageVar.set(LoginRoute)
        case value =>
          decode[SettingsData](value).toTry match
            case Success(x)         => settingsVar.set(Some(x))
            case Failure(exception) => Router.currentPageVar.set(ErrorRoute)
    }
}
