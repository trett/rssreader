package client

import client.NotifyComponent.errorMessage
import com.raquo.airstream.core.AirstreamError
import com.raquo.airstream.core.EventStream
import com.raquo.airstream.core.Observer
import io.circe.Decoder
import io.circe.parser.decode
import org.scalajs.dom.Response

import scala.util.Failure
import scala.util.Success
import scala.util.Try

object NetworkUtils {

    def HOST: String = AppConfig.BASE_URI

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
}
