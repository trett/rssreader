package client

import be.doeraene.webcomponents.ui5.{Button, Input, Label, *}
import be.doeraene.webcomponents.ui5.configkeys.*
import client.NetworkUtils.*
import client.NotifyComponent.infoMessage
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import ru.trett.rss.models.*

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

object SettingsPage {

    private val model = new Model
    import model.*
    private val openDialogBus: EventBus[Boolean] = new EventBus
    private val newChannelBus: EventBus[Unit] = new EventBus
    private val addChannelVar = Var[String]("")
    private val formBlockStyle = Seq(display.flex, alignItems.center, justifyContent.spaceBetween)
    private val channelObserver = Observer[ChannelList](channelVar.set)
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

    given feedItemDecoder: Decoder[FeedItemData] = deriveDecoder

    given channelDecoder: Decoder[ChannelData] = deriveDecoder

    given settingsDecoder: Decoder[UserSettings] = deriveDecoder

    given settingsEncoder: Encoder[UserSettings] = deriveEncoder

    def render: Element = div(
        cls := "cards main-content",
        display.flex,
        flexWrap.wrap,
        settingsCard(),
        newChannelDialog()
    )

    private def settingsCard(): HtmlElement =
        val settingsResponse = getSettingsRequest
        val settingsData = settingsResponse.collectSuccess
        val settingsErrors = settingsResponse.collectFailure
        val channelsResponse = getChannelsRequest
        val channels = channelsResponse.collectSuccess
        val channelsErrors = channelsResponse.collectFailure
        Card(
            div(
                padding.px := 20,
                Link(
                    "Return to feeds",
                    _.icon := IconName.`nav-back`,
                    _.events.onClick.mapTo(HomeRoute) --> {
                        Router.currentPageVar.set
                    },
                    marginBottom.px := 20
                ),
                form(
                    onSubmit.preventDefault
                        .mapTo(settingsSignal.now())
                        .flatMap(updateSettingsRequest) --> updateSettingsObserver,
                    div(
                        formBlockStyle,
                        marginBottom.px := 16,
                        Label("Hide read", _.forId := "hide-read-cb", _.showColon := true),
                        CheckBox(
                            _.id := "hide-read-cb",
                            _.checked <-- settingsSignal.map(x => x.forall(_.hideRead)),
                            _.events.onChange.mapToChecked --> settingsVar
                                .updater[Boolean]((a, b) => a.map(x => x.copy(hideRead = b)))
                        )
                    ),
                    div(
                        formBlockStyle,
                        marginBottom.px := 16,
                        Label(
                            "Summary language",
                            _.forId := "summary-language-cmb",
                            _.showColon := true,
                            _.wrappingType := WrappingType.None,
                            paddingRight.px := 20
                        ),
                        Select(
                            _.id := "summary-language-cmb",
                            _.events.onChange
                                .map(_.detail.selectedOption.textContent) --> settingsVar
                                .updater[String]((a, b) =>
                                    a.map(x => x.copy(summaryLanguage = Some(b)))
                                ),
                            List("English", "Serbian", "Russian", "German", "Spanish").map(lang =>
                                Select.option(
                                    _.selected <-- settingsSignal.map(x =>
                                        x.flatMap(_.summaryLanguage).contains(lang)
                                    ),
                                    lang
                                )
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
                    settingsData --> settingsVar.writer,
                    settingsErrors --> errorObserver
                ),
                div(
                    formBlockStyle,
                    marginTop.px := 32,
                    marginBottom.px := 16,
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
                        .flatMap(deleteChannelRequest) --> deleteChannelObserver,
                    _.selectionMode := ListMode.Delete,
                    children <-- channelSignal.split(_.id)(renderChannel),
                    channels --> channelObserver,
                    channelsErrors --> errorObserver
                )
            )
        )

    private def updateSettingsRequest(settings: Option[UserSettings]): EventStream[Try[Unit]] = {
        settings match
            case Some(s) =>
                FetchStream
                    .withDecoder(responseDecoder[String])
                    .post(
                        s"$HOST/api/user/settings",
                        _.body(s.asJson.toString),
                        _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
                    )
                    .mapSuccess(_ => ())
            case None => EventStream.empty
    }

    private def getSettingsRequest: EventStream[Try[Option[UserSettings]]] =
        FetchStream
            .withDecoder(responseDecoder[Option[UserSettings]])
            .get(s"$HOST/api/user/settings")
            .mapSuccess(_.get)

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

    private def deleteChannelRequest(id: String): EventStream[Try[Long]] =
        FetchStream
            .withDecoder(responseDecoder[Long])
            .apply(_.DELETE, s"$HOST/api/channels/$id")
            .mapSuccess(_.get)

    private def newChannelDialog(): HtmlElement = div(
        onMountBind(ctx =>
            newChannelBus --> { _ =>
                val response = getChannelsRequest
                val channels = response.collectSuccess
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
                .flatMapStream(updateChannelRequest) --> updateChannelObserver,
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

    private def getChannelsRequest: EventStream[Try[ChannelList]] =
        FetchStream
            .withDecoder(responseDecoder[ChannelList])
            .get(s"$HOST/api/channels")
            .mapSuccess(_.get)

    private def updateChannelRequest(link: String): EventStream[Try[Unit]] =
        FetchStream
            .withDecoder(responseDecoder[String])
            .post(
                s"$HOST/api/channels",
                _.body(link.asJson.toString),
                _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
            )
            .mapSuccess(_ => ())
}
