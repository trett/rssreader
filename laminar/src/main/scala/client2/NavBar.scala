package client2

import be.doeraene.webcomponents.ui5.Avatar
import be.doeraene.webcomponents.ui5.Icon
import be.doeraene.webcomponents.ui5.Popover
import be.doeraene.webcomponents.ui5.ShellBar
import be.doeraene.webcomponents.ui5.UList
import be.doeraene.webcomponents.ui5.configkeys.IconName
import be.doeraene.webcomponents.ui5.configkeys.ListSeparator
import be.doeraene.webcomponents.ui5.configkeys.PopoverPlacementType
import com.raquo.airstream.eventbus.EventBus
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import org.scalajs.dom
import org.scalajs.dom.HTMLElement

object NavBar {

  val model = new Model

  val openPopoverBus: EventBus[HTMLElement] = new EventBus
  val channelsBus: EventBus[String] = new EventBus
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
              _.icon := IconName.settings,
              "Settings",
              onClick.mapTo(()) --> { Router.currentPageVar.set(SettingsRoute) }
            ),
            _.item(_.icon := IconName.log, "Sign out")
          )
        )
      )
    )
}
