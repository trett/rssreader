package client

import be.doeraene.webcomponents.ui5.Text
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import org.scalajs.dom
import ru.trett.rss.models.UserSettings
import scala.util.{Success, Failure}

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

    val currentPageVar: Var[Route] = Var[Route](LoginRoute)
    def toMainPage(settings: UserSettings): Unit =
        val mainPage = if settings.isAiMode then SummaryRoute else HomeRoute
        currentPageVar.set(mainPage)

    private def login = LoginPage.render
    private def navbar = NavBar.render
    private def notifications = NotifyComponent.render
    def home: Element = Home.render
    def settings: Element = SettingsPage.render
    def summary: Element = SummaryPage.render

    private val model = AppState.model

    private val root = div(
        NetworkUtils.ensureSettingsLoaded() --> {
            case Success(settings) =>
                model.settingsVar.set(Some(settings))
                toMainPage(settings)
            case Failure(err) => NetworkUtils.handleError(err)
        },
        child <-- currentPageVar.signal.map {
            case LoginRoute    => login
            case HomeRoute     => div(navbar, notifications, home)
            case SettingsRoute => div(navbar, notifications, settings)
            case SummaryRoute  => div(navbar, notifications, summary)
            case ErrorRoute    => div(Text("An error occured"))
            case NotFoundRoute => div(Text("Not Found"))
        },
        className := "app-container"
    )

    def appElement(): Element = div(root)
