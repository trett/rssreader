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
        case "Unauthorized" | "Session expired" => Router.currentPageVar.set(Some(LoginRoute))
        case _                                  => errorMessage(ex)

    AirstreamError.registerUnhandledErrorCallback(err => errorMessage(err))

    /** Renders feed description HTML.
      *
      * The server derives a feed item's `imageUrl` from the first `<img>` of the description, so
      * that same picture would otherwise be shown twice: once as the card image and once inline.
      * Pass it as `excludedImage` to drop the inline copy.
      */
    def unsafeParseToHtmlFragment(html: String, excludedImage: Option[String] = None): HtmlElement =
        val nodes = DomApi.unsafeParseHtmlStringIntoNodeArray(html)
        excludedImage.filter(_.nonEmpty).foreach(url => nodes.foreach(removeImage(_, url)))
        div(
            nodes
                .flatMap {
                    case el: dom.html.Element => Some(el)
                    case raw                  => Some(div(raw.textContent).ref)
                }
                .filter(node => node.textContent.nonEmpty || hasMedia(node))
                .map(foreignHtmlElement)
        )

    private def isImage(node: dom.Node, url: String): Boolean = node match
        case img: dom.html.Image => img.getAttribute("src") == url || img.src == url
        case _                   => false

    private def removeImage(node: dom.Node, url: String): Unit =
        if isImage(node, url) then dropWithEmptyWrappers(node)
        else
            node match
                case el: dom.html.Element =>
                    el.querySelectorAll("img")
                        .filter(isImage(_, url))
                        .foreach(dropWithEmptyWrappers)
                case _ => ()

    /** Feeds often wrap the lead image in layout scaffolding — linux.org.ru, for one, uses a
      * `figure` whose `padding-bottom` reserves the image's aspect ratio. Dropping just the `img`
      * would leave that spacer behind as a tall empty block, so take the emptied wrappers too.
      */
    private def dropWithEmptyWrappers(node: dom.Node): Unit =
        val parent = Option(node.parentNode)
        parent.foreach(_.removeChild(node))
        parent.filter(isEmptyWrapper).foreach(dropWithEmptyWrappers)

    private def isEmptyWrapper(node: dom.Node): Boolean = node match
        case el: dom.html.Element => el.textContent.trim.isEmpty && !hasMedia(el)
        case _                    => false

    private def hasMedia(node: dom.Node): Boolean = node match
        case el: dom.html.Element => el.querySelectorAll("img, video, iframe").nonEmpty
        case _                    => false

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
}
