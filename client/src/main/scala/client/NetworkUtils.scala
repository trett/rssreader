package client

import client.NotifyComponent.errorMessage
import com.raquo.airstream.core.AirstreamError
import com.raquo.airstream.core.EventStream
import com.raquo.airstream.core.Observer
import io.circe.Decoder
import io.circe.parser.decode
import org.scalajs.dom.Response
import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import ru.trett.rss.models.UserSettings

object NetworkUtils {

    val JSON_ACCEPT: (String, String) = "Accept" -> "application/json"
    val JSON_CONTENT_TYPE: (String, String) = "Content-Type" -> "application/json"

    val errorObserver: Observer[Throwable] = Observer[Throwable] {
        handleError
    }

    def responseDecoder[A](using decoder: Decoder[A]): Response => EventStream[Try[Option[A]]] =
        resp =>
            resp.status match
                case 401 => EventStream.fromValue(Failure(new RuntimeException("Unauthorized")))
                case 301 => EventStream.fromValue(Failure(new RuntimeException("Session expired")))
                case _ =>
                    EventStream.fromJsPromise(
                        resp.text()
                            .`then`(data => Success(decode[A](data).toOption))
                    )

    def handleError(ex: Throwable): Unit = ex.getMessage match
        case "Unauthorized" | "Session expired" => Router.currentPageVar.set(LoginRoute)
        case _                                  => errorMessage(ex)

    AirstreamError.registerUnhandledErrorCallback(err => errorMessage(err))

    def unsafeParseToHtmlFragment(html: String): HtmlElement = div(
        DomApi
            .unsafeParseHtmlStringIntoNodeArray(html)
            .flatMap {
                case el: dom.html.Element => Some(el)
                case raw                  => Some(div(raw.textContent).ref)
            }
            .filter(_.textContent.nonEmpty)
            .map(foreignHtmlElement)
    )

    import Decoders.given

    def ensureSettingsLoaded(): EventStream[Try[Option[UserSettings]]] =
        FetchStream
            .withDecoder(responseDecoder[Option[UserSettings]])
            .get("/api/user/settings")
            .map {
                case Success(Some(value)) => Success(value)
                case Success(None) =>
                    Failure(new RuntimeException("Failed to decode settings response"))
                case Failure(err) => Failure(err)
            }

    def logout(): EventStream[Unit] =
        FetchStream.post("/api/logout", _.body("")).mapTo(())
}
