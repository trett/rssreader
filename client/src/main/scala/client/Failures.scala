package client

/** Turns a thrown thing into a sentence a reader can act on.
  *
  * The old path handed `ex.getMessage` straight to the notification, so a dropped connection
  * announced itself as "TypeError: NetworkError when attempting to fetch resource." — accurate,
  * addressed to the wrong person, and silent about what to do next. The raw text is kept as
  * `detail` so it still reaches the console and a tooltip; it just stops being the headline.
  */
object Failures:

    /** @param retriable
      *   whether trying the same request again could plausibly work — decides if the caller offers
      *   a retry
      */
    case class Described(message: String, detail: Option[String], retriable: Boolean)

    private val Offline = List(
        "networkerror",
        "failed to fetch",
        "load failed",
        "network request failed",
        "err_internet_disconnected",
        "err_connection"
    )

    private val TimedOut = List("timeout", "timed out", "aborted")

    private val BadPayload = List("decodingfailure", "parsingfailure", "failed to parse", "circe")

    def describe(ex: Throwable): Described =
        val raw = Option(ex.getMessage).map(_.trim).filter(_.nonEmpty)
        val probe = raw.getOrElse(ex.getClass.getSimpleName).toLowerCase

        def described(message: String, retriable: Boolean): Described =
            Described(message, raw.filterNot(_ == message), retriable)

        if Offline.exists(probe.contains) then described("Can't reach the server", retriable = true)
        else if TimedOut.exists(probe.contains) then
            described("The server took too long to answer", retriable = true)
        else if BadPayload.exists(probe.contains) then
            described("The server sent something unexpected", retriable = false)
        else if probe.contains("unauthorized") || probe.contains("session expired") then
            described("Your session expired", retriable = false)
        else if probe.contains("forbidden") then
            described("You don't have access to that", retriable = false)
        else if probe.contains("not found") then
            described("That's no longer there", retriable = false)
        else if probe.contains("500") || probe.contains("internal server") then
            described("The server hit an error", retriable = true)
        else
            raw match
                // Server-authored messages are already meant for a person — pass them through.
                case Some(text) if text.length <= 90 && !text.contains("Error:") =>
                    Described(text, None, retriable = false)
                case _ => described("Something went wrong", retriable = true)
end Failures
