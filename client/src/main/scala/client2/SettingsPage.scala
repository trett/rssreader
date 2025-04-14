package client2

import be.doeraene.webcomponents.ui5.Button
import be.doeraene.webcomponents.ui5.CheckBox
import be.doeraene.webcomponents.ui5.Dialog
import be.doeraene.webcomponents.ui5.Input
import be.doeraene.webcomponents.ui5.Label
import be.doeraene.webcomponents.ui5.Link
import be.doeraene.webcomponents.ui5.StepInput
import be.doeraene.webcomponents.ui5.UList
import be.doeraene.webcomponents.ui5.configkeys.*
import client2.NetworkUtils.HOST
import client2.NetworkUtils.JSON_ACCEPT
import client2.NetworkUtils.JSON_CONTENT_TYPE
import client2.NetworkUtils.errorObserver
import client2.NetworkUtils.handleError
import client2.NetworkUtils.responseDecoder
import client2.NotifyComponent.infoMessage
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import ru.trett.rss.models.*

import scala.language.implicitConversions
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object SettingsPage {

    private val model = new Model
    import model.*

    given feedItemDecoder: Decoder[FeedItemData] = deriveDecoder
    given channelDecoder: Decoder[ChannelData] = deriveDecoder
    given settingsDecoder: Decoder[UserSettings] = deriveDecoder
    given settingsEncoder: Encoder[UserSettings] = deriveEncoder

    private val formStateBus: EventBus[UserSettings] = new EventBus
    private val openDialogBus: EventBus[Boolean] = new EventBus
    private val newChannelBus: EventBus[Unit] = new EventBus
    private val addChannelVar = Var[String]("")

    private val formBlockStyle = Seq(display.flex, alignItems.center, justifyContent.spaceBetween)

    private val channelObserver = Observer[ChannelList](channelVar.set(_))

    private val updateChannelObserver = Observer[Try[Unit]] {
        case Success(_) =>
            newChannelBus.emit(())
            infoMessage("Settings saved")
        case Failure(err) => handleError(err)
    }

    private val deleteChannelObserver = Observer[Try[Long]] {
        case Success(id) =>
            channelVar.update(xs => xs.filterNot(_.id == id))
            infoMessage("Channel deleted")
        case Failure(err) => handleError(err)
    }

    private val updateSettingsObserver = Observer[Try[Unit]] {
        case Success(_)   => infoMessage("Settings saved")
        case Failure(err) => handleError(err)
    }

    def render: Element = div(
        cls := "container",
        display.flex,
        flexWrap.wrap,
        settingsForm(),
        div(flexBasis.percent := 100),
        channelsList(),
        newChannelDialog()
    )

    private def settingsForm(): HtmlElement =
        val response = getSettingsRequest(); val data = response.collectSuccess
        val errors = response.collectFailure
        div(
            paddingLeft.px := 40,
            paddingBottom.px := 40,
            Link(
                "Return to feeds",
                _.icon := IconName.`nav-back`,
                _.events.onClick.mapTo(HomeRoute) --> { Router.currentPageVar.set(_) },
                marginBottom.px := 10
            ),
            form(
                onSubmit.preventDefault
                    .mapTo(settingsSignal.now())
                    .flatMap(updateSettingsRequest(_)) --> updateSettingsObserver,
                div(
                    formBlockStyle,
                    Label("Hide read", _.forId := "hide-read-cb", _.showColon := true),
                    CheckBox(
                        _.id := "hide-read-cb",
                        _.checked <-- settingsSignal.map(x => x.map(_.read).getOrElse(true)),
                        _.events.onChange.mapToChecked --> settingsVar.updater[Boolean]((a, b) =>
                            a.map(x => x.copy(read = b))
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
                            x.map(_.retentionDays.toDouble).getOrElse(3)
                        ),
                        _.min := 1,
                        _.max := 14,
                        _.step := 1,
                        _.events.onChange.map(x => x.target.value) --> settingsVar.updater[Double](
                            (a, b) => a.map(x => x.copy(retentionDays = b.toInt))
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
                ),
                data --> settingsVar.writer,
                errors --> errorObserver
            )
        )

    private def channelsList(): HtmlElement =
        val response = getChannelsRequest()
        val channels = response.collectSuccess
        val errors = response.collectFailure
        div(
            borderTopStyle.ridge,
            padding.px := 40,
            minWidth.px := 200,
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
                    .map(_.detail.item.dataset.get("channelId").get)
                    .flatMap(deleteChannelRequest(_)) --> deleteChannelObserver,
                _.selectionMode := ListMode.Delete,
                children <-- channelSignal.split(_.id)(renderChannel),
                channels --> channelObserver,
                errors --> errorObserver
            )
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

    private def newChannelDialog(): HtmlElement = div(
        onMountBind(ctx =>
            newChannelBus --> { _ =>
                val response = getChannelsRequest(); val channels = response.collectSuccess
                val errors = response.collectFailure
                channels.addObserver(channelObserver)(ctx.owner)
                errors.addObserver(errorObserver)(ctx.owner)
            }
        ),
        Dialog(
            _.showFromEvents(openDialogBus.events.filter(identity).mapTo(())),
            _.closeFromEvents(openDialogBus.events.map(!_).filter(identity).mapTo(())),
            _.events.onClose
                .mapTo(addChannelVar.now())
                .flatMapStream(updateChannelRequest(_)) --> updateChannelObserver,
            _.headerText := "New channel",
            sectionTag(
                div(
                    formBlockStyle,
                    Label(_.forId := "rss_url", "RSS Url:"),
                    Input(_.id := "rss_url", _.events.onInput.mapToValue --> addChannelVar)
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
        )
    )

    private def updateSettingsRequest(settings: Option[UserSettings]): EventStream[Try[Unit]] = {
        settings match
            case Some(s) => {
                FetchStream
                    .withDecoder(responseDecoder[String])
                    .post(
                        s"${HOST}/api/user/settings",
                        _.body(s.asJson.toString),
                        _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
                    )
                    .mapSuccess(_ => ())
            }
            case None => EventStream.empty
    }

    private def deleteChannelRequest(id: String): EventStream[Try[Long]] = FetchStream
        .withDecoder(responseDecoder[Long])
        .apply(_.DELETE, s"${HOST}/api/channels/delete${id}")
        .mapSuccess(_.get)

    private def getSettingsRequest(): EventStream[Try[Option[UserSettings]]] =
        FetchStream
            .withDecoder(responseDecoder[Option[UserSettings]])
            .get(s"${HOST}/api/user/settings")
            .mapSuccess(_.get)

    private def getChannelsRequest(): EventStream[Try[ChannelList]] =
        FetchStream
            .withDecoder(responseDecoder[ChannelList])
            .get(s"${HOST}/api/channels")
            .mapSuccess(_.get)

    private def updateChannelRequest(link: String): EventStream[Try[Unit]] = FetchStream
        .withDecoder(responseDecoder[String])
        .post(
            s"${HOST}/api/channels",
            _.body(link),
            _.headers(JSON_ACCEPT, "Content-Type" -> "text/plain")
        )
        .mapSuccess(_ => ())
}
