package client

import be.doeraene.webcomponents.ui5.Text
import com.raquo
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
case object NotFoundRoute extends Route

object Router:

    val currentPageVar: Var[Route] = Var[Route](HomeRoute)

    private def login = LoginPage.render
    private def navbar = NavBar.render
    private def notifications = NotifyComponent.render
    def home: Element = Home.render
    def settings: Element = SettingsPage.render

    private val root = div(child <-- currentPageVar.signal.map {
        case LoginRoute    => login
        case HomeRoute     => div(navbar, notifications, home)
        case SettingsRoute => div(navbar, notifications, settings)
        case ErrorRoute    => div(Text("An error occured"))
        case NotFoundRoute => div(Text("Not Found"))
    })

    def appElement(): Element = div(root)
