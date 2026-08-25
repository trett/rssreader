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
import ru.trett.rss.models.{ChannelData, UserSettings}

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

    /** Auth failures are a navigation, not a message. Everything else is translated out of
      * exception-speak by [[Failures]] before it reaches a human; the original text stays in the
      * console and in the notification's tooltip.
      */
    def handleError(ex: Throwable): Unit = ex.getMessage match
        case "Unauthorized" | "Session expired" => Router.currentPageVar.set(Some(LoginRoute))
        case _ =>
            val described = Failures.describe(ex)
            dom.console.error(ex)
            errorMessage(described.message, described.detail)

    AirstreamError.registerUnhandledErrorCallback(handleError)

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

    def ensureSettingsLoaded(): EventStream[Try[UserSettings]] =
        FetchStream
            .withDecoder(responseDecoder[UserSettings])
            .get("/api/user/settings")
            .map {
                case Success(Some(value)) => Success(value)
                case Success(None) =>
                    Failure(new RuntimeException("Failed to parse settings response"))
                case Failure(err) => Failure(err)
            }

    def logout(): EventStream[Unit] =
        FetchStream.post("/api/logout", _.body("")).mapTo(())

    def getChannels(): EventStream[Try[List[ChannelData]]] =
        FetchStream
            .withDecoder(responseDecoder[List[ChannelData]])
            .get("/api/channels")
            .mapSuccess(_.getOrElse(List.empty))

    def refreshFeeds(): EventStream[Unit] =
        FetchStream
            .withDecoder(responseDecoder[Unit])
            .post("/api/channels/refresh")
            .mapTo(())
}
