package client2

import be.doeraene.webcomponents.ui5.Avatar
import be.doeraene.webcomponents.ui5.Button
import be.doeraene.webcomponents.ui5.Dialog
import be.doeraene.webcomponents.ui5.Icon
import be.doeraene.webcomponents.ui5.Input
import be.doeraene.webcomponents.ui5.Label
import be.doeraene.webcomponents.ui5.Popover
import be.doeraene.webcomponents.ui5.ShellBar
import be.doeraene.webcomponents.ui5.UList
import be.doeraene.webcomponents.ui5.configkeys.ButtonDesign
import be.doeraene.webcomponents.ui5.configkeys.IconName
import be.doeraene.webcomponents.ui5.configkeys.ListSeparator
import be.doeraene.webcomponents.ui5.configkeys.PopoverPlacementType
import com.raquo.airstream.core.Observer
import com.raquo.airstream.eventbus.EventBus
import com.raquo.airstream.ownership.OneTimeOwner
import com.raquo.laminar.api.L.{*}
import com.raquo.laminar.api.features.unitArrows
import org.scalajs.dom
import org.scalajs.dom.HTMLElement

import scala.util.Failure
import scala.util.Success
import scala.util.Try

object NavBar {

  val model = new Model

  val openPopoverBus: EventBus[HTMLElement] = new EventBus
  val openDialogBus: EventBus[Boolean] = new EventBus
  val channelsBus: EventBus[String] = new EventBus

  val addChannelVar = Var[String]("")

  val profileId = "shellbar-profile-id"

  def render: Element =
    div(
      ShellBar(
        _.primaryTitle := "RSS Reader",
        _.slots.profile := Avatar(
          _.icon := IconName.customer,
          idAttr := profileId
        ),
        _.slots.logo := Icon(_.name := IconName.home),
        _.events.onProfileClick
          .map(_.detail.targetRef) --> openPopoverBus.writer,
        _.events.onLogoClick.mapTo(()) --> {
          Router.currentPageVar.set(HomeRoute)
        }
      ),
      Popover(
        _.openerId := profileId,
        _.open <-- openPopoverBus.events.mapTo(true),
        _.placement := PopoverPlacementType.Bottom,
        div(
          UList(
            _.separators := ListSeparator.None,
            _.item(
              _.icon := IconName.edit,
              "Add channel",
              _.id := "add-channel-item",
              onClick.mapTo(true) --> openDialogBus.writer
            ),
            _.item(
              _.icon := IconName.settings,
              "Settings",
              onClick.mapTo(()) --> { Router.currentPageVar.set(SettingsRoute) }
            ),
            _.item(_.icon := IconName.log, "Sign out")
          )
        )
      ),
      Dialog(
        _.showFromEvents(openDialogBus.events.filter(identity).mapTo(())),
        _.closeFromEvents(
          openDialogBus.events.map(!_).filter(identity).mapTo(())
        ),
        _.events.onClose.mapTo(addChannelVar.now()) --> channelsBus,
        _.headerText := "RSS URL",
        sectionTag(
          div(
            Label(_.forId := "rss_url", "RSS Url:"),
            Input(
              _.id := "rss_url",
              _.events.onInput.mapToValue --> addChannelVar
            )
          )
        ),
        _.slots.footer := div(
          div(flex := "1"),
          Button(
            _.design := ButtonDesign.Emphasized,
            "Add",
            _.events.onClick.mapTo(false) --> openDialogBus.writer
          )
        )
      ),
      addChannelFun()
    )

  def addChannelFun() =
    channelsBus.events.filter(!_.isEmpty()) --> { url =>
      FetchStream
        .post(
          "https://localhost/api/channel/add",
          _.body(url),
          _.headers(
            "Accept" -> "application/json",
            "Content-Type" -> "application/json"
          )
        )
        .addObserver(
          Observer.fromTry(response =>
            response match
              case Success(value)     => println(value)
              case Failure(exception) => println(exception)
          )
        )(new OneTimeOwner(() => {}))
    }
}
