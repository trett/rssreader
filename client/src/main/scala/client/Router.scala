package client

import be.doeraene.webcomponents.ui5.{Text, BusyIndicator}
import be.doeraene.webcomponents.ui5.configkeys.BusyIndicatorSize
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import org.scalajs.dom
import scala.util.{Success, Failure}

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

    val currentPageVar: Var[Option[Route]] = Var[Option[Route]](Option.empty)
    def toMainPage(): Unit = currentPageVar.set(Some(HomeRoute))

    private def login = LoginPage.render
    private def navbar = NavBar.render
    private def notifications = NotifyComponent.render
    def home: Element = Home.render
    def settings: Element = SettingsPage.render

    private val model = AppState.model

    private lazy val loadingComponent = div(
        display.flex,
        flexDirection.column,
        alignItems.center,
        justifyContent.center,
        minHeight := "100vh",
        BusyIndicator(_.active := true, _.size := BusyIndicatorSize.L),
        div(
            marginTop.px := 20,
            color := "var(--sapContent_LabelColor)",
            fontSize := "var(--sapFontSize)",
            "Loading application..."
        )
    )

    private val root = div(
        NetworkUtils.ensureSettingsLoaded() --> {
            case Success(settings) =>
                model.settingsVar.set(Some(settings))
                toMainPage()
            case Failure(err) => NetworkUtils.handleError(err)
        },
        child <-- currentPageVar.signal.map {
            case None                => loadingComponent
            case Some(LoginRoute)    => login
            case Some(HomeRoute)     => div(navbar, notifications, home)
            case Some(SettingsRoute) => div(navbar, notifications, settings)
            case Some(ErrorRoute)    => div(Text("An error occured"))
            case Some(NotFoundRoute) => div(Text("Not Found"))
        },
        className := "app-container"
    )

    def appElement(): Element = div(root)
