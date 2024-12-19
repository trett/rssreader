package client2

import org.scalajs.dom.Response
import com.raquo.airstream.core.EventStream
import scala.util.Failure
import scala.util.Success

import io.circe.Decoder
import io.circe.parser.decode
import scala.util.Try
import client2.NotifyComponent.errorMessage
import com.raquo.airstream.core.Observer
import com.raquo.airstream.core.AirstreamError

object NetworkUtils {

  def HOST = AppConfig.BASE_URI

  val JSON_ACCEPT: (String, String) =
    ("Accept" -> "application/json")
  val JSON_CONTENT_TYPE: (String, String) =
    ("Content-Type" -> "application/json")

  val errorObserver = Observer[Throwable] { handleError(_) }

  def responseDecoder[A](using
      decoder: Decoder[A]
  ): Response => EventStream[Try[Option[A]]] =
    resp =>
      resp.status match
        case 401 =>
          EventStream.fromValue(Failure(new RuntimeException("Unauthorized")))
        case 301 =>
          EventStream.fromValue(
            Failure(new RuntimeException("Session expired"))
          )
        case _ =>
          EventStream.fromJsPromise(
            resp
              .text()
              .`then`(x => Success(decode[A](x).toOption))
          )

  def handleError(ex: Throwable): Unit =
    ex.getMessage() match
      case "Unauthorized" | "Session expired" =>
        Router.currentPageVar.set(LoginRoute)
      case _ => errorMessage(ex)

  AirstreamError.registerUnhandledErrorCallback(err => errorMessage(err))
}
