package client2

import be.doeraene.webcomponents.ui5.Button
import be.doeraene.webcomponents.ui5.CheckBox
import be.doeraene.webcomponents.ui5.Dialog
import be.doeraene.webcomponents.ui5.Input
import be.doeraene.webcomponents.ui5.Label
import be.doeraene.webcomponents.ui5.StepInput
import be.doeraene.webcomponents.ui5.UList
import be.doeraene.webcomponents.ui5.configkeys.*
import client2.NetworkUtils.HOST
import client2.NetworkUtils.JSON_ACCEPT
import client2.NetworkUtils.JSON_CONTENT_TYPE
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.*
import io.circe.parser.decode
import io.circe.syntax.*

import scala.language.implicitConversions
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object SettingsPage {

  val model = new Model
  import model.*
  import NotifyComponent._

  given channelDecoder: Decoder[ChannelData] = deriveDecoder
  given settingsDecoder: Decoder[SettingsData] = deriveDecoder
  given settingsEncoder: Encoder[SettingsData] = deriveEncoder

  private val formStateBus: EventBus[SettingsData] = new EventBus
  private val openDialogBus: EventBus[Boolean] = new EventBus
  private val channelsBus: EventBus[String] = new EventBus
  private val addChannelVar = Var[String]("")

  private val formBlockStyle =
    Seq(display.flex, alignItems.center, justifyContent.spaceBetween)

  def render: Element =
    div(
      cls := "container",
      display.flex,
      flexWrap.wrap,
      settings(),
      div(flexBasis.percent := 100),
      channels(),
      addChannelComponent()
    )

  private def settings(): HtmlElement =
    div(
      padding.px := 40,
      form(
        onSubmit.preventDefault
          .mapTo(settingsSignal.now())
          .flatMap(updateSettings(_)) --> notifyVar.updater[Notify]((xs, x) =>
          xs :+ x
        ),
        div(
          formBlockStyle,
          Label("Hide read", _.forId := "hide-read-cb", _.showColon := true),
          CheckBox(
            _.id := "hide-read-cb",
            _.checked <-- settingsSignal.map(x =>
              x.map(_.hideRead).getOrElse(true)
            ),
            _.events.onChange.mapToChecked --> settingsVar.updater[Boolean](
              (a, b) => a.map(x => x.copy(hideRead = b))
            )
          )
        ),
        br(),
        div(
          formBlockStyle,
          Label(
            "Days to keep",
            _.forId := "days-to-keep-cmb",
            _.showColon := true,
            _.wrappingType := WrappingType.None,
            paddingRight.px := 20
          ),
          StepInput(
            _.id := "days-to-keep-cmb",
            _.value <-- settingsSignal.map(x =>
              x.map(_.deleteAfter.toDouble).getOrElse(3)
            ),
            _.min := 1,
            _.max := 14,
            _.step := 1,
            _.events.onChange
              .map(x => x.target.value) --> settingsVar.updater[Double](
              (a, b) => a.map(x => x.copy(deleteAfter = b.toInt))
            )
          )
        ),
        div(
          paddingTop.px := 10,
          Button(
            _.design := ButtonDesign.Emphasized,
            "Save",
            _.icon := IconName.save,
            _.tpe := ButtonType.Submit
          )
        )
      ),
      getSettings()
    )

  private def channels(): HtmlElement =
    div(
      borderTopStyle.ridge,
      padding.px := 40,
      div(
        formBlockStyle,
        Label("Your channels", _.forId := "channels-list", _.showColon := true),
        Button(
          _.design := ButtonDesign.Default,
          _.icon := IconName.add,
          _.iconOnly := true,
          _.events.onClick.mapTo(true) --> openDialogBus
        )
      ),
      UList(
        _.id := "channels-list",
        _.events.onItemDelete
          .map(
            _.detail.item.dataset.get("channelId").get
          )
          .flatMap(deleteChannel(_)) --> deleteChannelObserver,
        _.selectionMode := ListMode.Delete,
        children <-- channelSignal.split(_.id)(renderChannel)
      ),
      getChannels()
    )

  private def renderChannel(
      id: Long,
      item: ChannelData,
      itemSignal: Signal[ChannelData]
  ): HtmlElement =
    UList.item(
      _.icon := IconName.list,
      dataAttr("channel-id") <-- itemSignal.map(_.id.text),
      child <-- itemSignal.map(_.title)
    )

  private def addChannelComponent(): HtmlElement =
    div(
      Dialog(
        _.showFromEvents(openDialogBus.events.filter(identity).mapTo(())),
        _.closeFromEvents(
          openDialogBus.events.map(!_).filter(identity).mapTo(())
        ),
        _.events.onClose.mapTo(addChannelVar.now()) --> channelsBus,
        _.headerText := "New channel",
        sectionTag(
          div(
            formBlockStyle,
            Label(_.forId := "rss_url", "RSS Url:"),
            Input(
              _.id := "rss_url",
              _.events.onInput.mapToValue --> addChannelVar
            )
          )
        ),
        _.slots.footer := div(
          paddingTop.px := 10,
          div(flex := "1"),
          Button(
            _.design := ButtonDesign.Emphasized,
            "Add",
            _.icon := IconName.save,
            _.events.onClick.mapTo(false) --> openDialogBus.writer
          )
        )
      ),
      updateChannels()
    )

  private val deleteChannelObserver = Observer[Try[Long]] {
    case Success(id) => {
      channelVar.update(xs => xs.filterNot(_.id == id))
      notifyVar.update(_ :+ infoMessage("Channel deleted"))
    }
    case Failure(exception) =>
      notifyVar.update(_ :+ errorMessage("Error"))
  }

  private def updateSettings(
      settings: Option[SettingsData]
  ): EventStream[Notify] =
    settings
      .map(s =>
        FetchStream
          .post(
            s"${HOST}/api/settings",
            _.body(s.asJson.toString),
            _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
          )
          .recoverToTry
          .map {
            case Success(_)  => infoMessage("Settings saved")
            case Failure(ex) => errorMessage("Error")
          }
      )
      .getOrElse(EventStream.empty)

  private def deleteChannel(id: String): EventStream[Try[Long]] =
    FetchStream
      .post(
        s"${HOST}/api/channel/delete",
        _.body(id),
        _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
      )
      .map(response => Try(response.split(" ")(1).toLong))

  private def getChannels(): Modifier[HtmlElement] =
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

  private def getSettings(): Modifier[HtmlElement] =
    FetchStream
      .get(s"${HOST}/api/settings")
      .recover { case err: Throwable => Option.empty } --> { item =>
      item match
        case "" => Router.currentPageVar.set(LoginRoute)
        case value =>
          decode[SettingsData](value).toTry match
            case Success(x)         => settingsVar.set(Some(x))
            case Failure(exception) => Router.currentPageVar.set(ErrorRoute)
    }

  private def updateChannels() =
    onMountBind(ctx =>
      channelsBus.events.filter(!_.isEmpty()) --> { link =>
        FetchStream
          .post(
            s"${HOST}/api/channel/add",
            _.body(link),
            _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
          )
          .addObserver(
            Observer.fromTry {
              case Success(value)     => infoMessage("Saved")
              case Failure(exception) => errorMessage("Error")
            }
          )(ctx.owner)
      }
    )
}
