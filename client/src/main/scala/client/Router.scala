package client

import be.doeraene.webcomponents.ui5.Text
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import org.scalajs.dom

@main
def createApp(): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), Router.appElement())

sealed trait Route
case object LoginRoute extends Route
case object HomeRoute extends Route
case object ErrorRoute extends Route
case object SettingsRoute extends Route
case object SummaryRoute extends Route
case object NotFoundRoute extends Route

object Router:

    val currentPageVar: Var[Route] = Var[Route](SummaryRoute)
    private val initialRouteSetVar: Var[Boolean] = Var(false)

    private def login = LoginPage.render
    private def navbar = NavBar.render
    private def notifications = NotifyComponent.render
    def home: Element = Home.render
    def settings: Element = SettingsPage.render
    def summary: Element = SummaryPage.render

    private val root = div(
        child <-- currentPageVar.signal.map {
            case LoginRoute    => login
            case HomeRoute     => div(navbar, notifications, home)
            case SettingsRoute => div(navbar, notifications, settings)
            case SummaryRoute  => div(navbar, notifications, summary)
            case ErrorRoute    => div(Text("An error occured"))
            case NotFoundRoute => div(Text("Not Found"))
        },
        className := "app-container",
        AppState.model.settingsSignal --> { settings =>
            if (!initialRouteSetVar.now() && settings.isDefined) {
                val isRegularMode = settings.flatMap(_.aiMode).contains(false)
                val currentRoute = currentPageVar.now()
                // Only redirect if we're still on the default route
                if (currentRoute == SummaryRoute && isRegularMode) {
                    currentPageVar.set(HomeRoute)
                } else if (currentRoute == LoginRoute) {
                    // After login, navigate to the appropriate page
                    currentPageVar.set(if (isRegularMode) HomeRoute else SummaryRoute)
                }
                initialRouteSetVar.set(true)
            }
        }
    )

    def appElement(): Element = div(root)
