package client2

import com.raquo
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import io.circe.generic.semiauto.*
import org.scalajs.dom
import be.doeraene.webcomponents.ui5.Text


@main
def createApp(): Unit =
  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    Router.appElement()
  )

sealed trait Route
case object LoginRoute extends Route
case object HomeRoute extends Route
case object ErrorRoute extends Route
case object SettingsRoute extends Route
case object NotFoundRoute extends Route

object Router:

  val currentPageVar = Var[Route](HomeRoute)

  lazy val home = Home.render
  lazy val login = LoginPage.render
  lazy val settings = SettingsPage.render
  lazy val navbar = NavBar.render

  val root = div(
    child <-- currentPageVar.signal.map {
      case LoginRoute => login
      case HomeRoute  => div(navbar, home)
      case SettingsRoute => div(navbar, settings)
      case ErrorRoute => div(Text("An error occured"))
      case NotFoundRoute => div(Text("Not Found"))
    }
  )

  def appElement(): Element = div(root)