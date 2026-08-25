package client

import java.time.temporal.ChronoUnit
import java.time.{OffsetDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter

/** "18m", "4h", "2d", then an absolute date. Relative time belongs next to the feed name; the
  * absolute stamp only matters once an item is old enough that "6d" stops being useful.
  */
object RelativeTime:

    private val absolute = DateTimeFormatter.ofPattern("d MMM")
    private val absoluteWithYear = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val fullStamp = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")

    def short(
        date: OffsetDateTime,
        now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
    ): String =
        val minutes = ChronoUnit.MINUTES.between(date, now)
        if minutes < 1 then "now"
        else if minutes < 60 then s"${minutes}m"
        else if minutes < 60 * 24 then s"${minutes / 60}h"
        else if minutes < 60 * 24 * 7 then s"${minutes / (60 * 24)}d"
        else
            val local = date.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime
            if local.getYear == now.getYear then absolute.format(local)
            else absoluteWithYear.format(local)

    /** Full stamp for the article header. */
    def full(date: OffsetDateTime): String =
        fullStamp.format(date.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime)
end RelativeTime
