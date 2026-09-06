package client

import be.doeraene.webcomponents.ui5.Avatar
import be.doeraene.webcomponents.ui5.Button
import be.doeraene.webcomponents.ui5.Icon
import be.doeraene.webcomponents.ui5.Popover
import be.doeraene.webcomponents.ui5.ShellBar
import be.doeraene.webcomponents.ui5.UList
import be.doeraene.webcomponents.ui5.configkeys.ButtonDesign
import be.doeraene.webcomponents.ui5.configkeys.IconName
import be.doeraene.webcomponents.ui5.configkeys.ListSeparator
import be.doeraene.webcomponents.ui5.configkeys.PopoverPlacementType
import client.NetworkUtils.{responseDecoder}
import com.raquo.airstream.eventbus.EventBus
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import org.scalajs.dom.HTMLElement

object NavBar {

    private val popoverBus: EventBus[Option[HTMLElement]] = new EventBus
    private val profileId = "shellbar-profile-id"
    private val model = AppState.model
    import model.*

    def render: Element = div(
        cls := "sticky-navbar",
        ShellBar(
            _.primaryTitle := "RSS Reader",
            _.notificationsCount <-- unreadCountSignal.map(count =>
                if count > 0 then count.toString else ""
            ),
            _.showNotifications <-- unreadCountSignal.map(_ > 0),
            _.slots.profile := Avatar(_.icon := IconName.customer, idAttr := profileId),
            _.slots.logo := Icon(_.name := IconName.feed),
            _.slots.startButton := Button(
                cls := "sidebar-toggle",
                _.design := ButtonDesign.Transparent,
                _.icon := IconName.menu2,
                _.tooltip := "Show feeds",
                hidden <-- Router.currentPageVar.signal.map(!_.contains(HomeRoute)),
                onClick
                    .filter(_ => Router.currentPageVar.now().contains(HomeRoute))
                    .mapTo(!sidebarOpenSignal.now()) --> sidebarOpenVar
            ),
            _.events.onProfileClick.map(item => Some(item.detail.targetRef)) --> popoverBus.writer,
            _.events.onLogoClick.mapTo(()) --> { _ =>
                sidebarOpenVar.set(false)
                if settingsSignal.now().isDefined then Router.toMainPage()
                else Router.currentPageVar.set(Some(LoginRoute))
            },
            _.events.onNotificationsClick.mapTo(()) --> {
                EventBus.emit(Home.markAllAsReadBus -> ())
            }
        ),
        Popover(
            cls := "nav-menu",
            _.openerId := profileId,
            _.showAtAndCloseFromEvents(popoverBus.events),
            _.placement := PopoverPlacementType.Bottom,
            div(
                UList(
                    _.separators := ListSeparator.None,
                    _.item(
                        _.icon := IconName.settings,
                        "Settings",
                        onClick.mapTo(()) --> {
                            sidebarOpenVar.set(false)
                            Router.currentPageVar.set(Some(SettingsRoute))
                        }
                    ),
                    _.item(
                        _.icon := IconName.refresh,
                        "Update feeds",
                        onClick
                            .mapTo(())
                            .flatMap(_ => refreshFeedsRequest()) --> { _ =>
                            EventBus.emit(
                                Home.refreshFeedsBus -> 1,
                                Home.refreshUnreadCountBus -> (),
                                popoverBus -> None
                            )
                        }
                    ),
                    _.item(
                        _.icon := IconName.log,
                        "Sign out",
                        onClick.flatMap(_ => NetworkUtils.logout()) --> { _ =>
                            sidebarOpenVar.set(false)
                            Router.currentPageVar.set(Some(LoginRoute))
                        }
                    )
                )
            )
        )
    )

    private def refreshFeedsRequest(): EventStream[Unit] =
        FetchStream
            .withDecoder(responseDecoder[Unit])
            .post("/api/channels/refresh")
            .mapTo(())
}
