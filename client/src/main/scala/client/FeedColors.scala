package client

/** Stable per-feed accent colour derived from the channel title, so every feed reads as itself
  * without the API having to carry a colour or a favicon. Hue is a hash; lightness and chroma are
  * fixed, which keeps every colour legible on the paper background and against white text.
  */
object FeedColors:

    private val Lightness = 48
    private val Chroma = 0.11

    private def hue(title: String): Int =
        title.foldLeft(0)((h, c) => (h * 31 + c.toInt) & 0x7fffffff) % 360

    /** Dot / accent colour. */
    def of(title: String): String =
        s"oklch($Lightness% $Chroma ${hue(title)})"
end FeedColors
