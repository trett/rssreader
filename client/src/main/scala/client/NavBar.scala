package client

import be.doeraene.webcomponents.ui5.Avatar
import be.doeraene.webcomponents.ui5.Icon
import be.doeraene.webcomponents.ui5.Popover
import be.doeraene.webcomponents.ui5.ShellBar
import be.doeraene.webcomponents.ui5.UList
import be.doeraene.webcomponents.ui5.configkeys.IconName
import be.doeraene.webcomponents.ui5.configkeys.ListSeparator
import be.doeraene.webcomponents.ui5.configkeys.PopoverPlacementType
import client.AppConfig.BASE_URI
import client.NetworkUtils.{responseDecoder}
import com.raquo.airstream.eventbus.EventBus
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import org.scalajs.dom
import org.scalajs.dom.HTMLElement

object NavBar {

    private val popoverBus: EventBus[Option[HTMLElement]] = new EventBus
    private val profileId = "shellbar-profile-id"

    def render: Element = div(
        cls := "sticky-navbar",
        ShellBar(
            _.primaryTitle := "RSS Reader",
            _.slots.profile := Avatar(_.icon := IconName.customer, idAttr := profileId),
            _.slots.logo := Icon(_.name := IconName.home),
            _.item(
                _.icon := IconName.ai,
                _.text := "Summary",
                onClick.mapTo(()) --> { Router.currentPageVar.set(SummaryRoute) }
            ),
            _.events.onProfileClick.map(item => Some(item.detail.targetRef)) --> popoverBus.writer,
            _.events.onLogoClick.mapTo(()) --> { Router.currentPageVar.set(HomeRoute) }
        ),
        Popover(
            _.openerId := profileId,
            _.showAtAndCloseFromEvents(popoverBus.events),
            _.placement := PopoverPlacementType.Bottom,
            div(
                UList(
                    _.separators := ListSeparator.None,
                    _.item(
                        _.icon := IconName.settings,
                        "Settings",
                        onClick.mapTo(()) --> { Router.currentPageVar.set(SettingsRoute) }
                    ),
                    _.item(
                        _.icon := IconName.refresh,
                        "Update feeds",
                        onClick
                            .mapTo(())
                            // TODO: show loading spinner
                            .flatMap(_ => refreshFeedsRequest()) --> { _ =>
                            EventBus.emit(Home.refreshFeedsBus -> 1, popoverBus -> None)
                        }
                    ),
                    _.item(
                        _.icon := IconName.complete,
                        "Mark all as read",
                        onClick.mapTo(()) --> { _ =>
                            EventBus.emit(Home.markAllAsReadBus -> (), popoverBus -> None)
                        }
                    ),
                    // _.item(
                    //     _.icon := IconName.`batch-payments`,
                    //     "Summary",
                    //     onClick
                    //         .mapTo(())
                    //         .flatMap(_ => summarizeRequest()) --> { summary =>
                    //         Router.currentPageVar.set(SummaryRoute(summary))
                    //         popoverBus.writer.onNext(None)
                    //     }
                    // ),
                    _.item(_.icon := IconName.log, "Sign out") // TODO
                )
            )
        )
    )

    private def refreshFeedsRequest(): EventStream[Unit] =
        FetchStream
            .withDecoder(responseDecoder[Unit])
            .post(s"$BASE_URI/api/channels/refresh")
            .mapTo(())
}
