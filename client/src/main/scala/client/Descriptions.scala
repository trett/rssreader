package client

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** Feed `description` is raw HTML from someone else's CMS. Two jobs here: a plain-text one-liner
  * for the list pane, and a cleaned fragment for the reading pane with the aggregator boilerplate
  * ("submitted by /u/x [link] [comments]") and stray anchors removed, so nothing renders as
  * default-blue underlined text.
  */
object Descriptions:

    private val boilerplate = List(
        """(?is)\s*submitted by\s*/u/\S+.*$""".r,
        """(?is)\s*\[link\]\s*""".r,
        """(?is)\s*\[comments\]\s*""".r,
        """(?is)\s*read more\s*$""".r,
        """(?is)\s*continue reading\.*\s*$""".r,
        """(?is)\s*the post .{0,120} appeared first on .{0,80}\.?\s*$""".r
    )

    private def strip(text: String): String =
        boilerplate.foldLeft(text)((acc, re) => re.replaceAllIn(acc, "")).trim

    /** Parses without loading anything. Assigning `innerHTML`, even on a detached element, still
      * kicks off requests for every `<img>` in the markup — and feed descriptions are full of
      * tracking pixels. A DOMParser document is inert, so nothing is fetched for descriptions we
      * only ever read text out of.
      */
    private def inert(html: String): dom.HTMLElement =
        new dom.DOMParser()
            .parseFromString(html, dom.MIMEType.`text/html`)
            .asInstanceOf[dom.HTMLDocument]
            .body

    /** Tags out, entities decoded, whitespace collapsed, boilerplate gone. */
    def plain(html: String): String =
        val text = Option(inert(html).textContent).getOrElse("").replaceAll("\\s+", " ")
        strip(text)

    /** The article body: keep structure, drop the aggregator tail and any links that survive it. */
    def fragment(html: String): HtmlElement =
        val body = inert(html)
        // Unwrap anchors whose text is pure boilerplate; keep real inline links as text otherwise.
        val anchors = body.querySelectorAll("a")
        (0 until anchors.length).foreach { i =>
            val a = anchors(i).asInstanceOf[dom.html.Anchor]
            val label = Option(a.textContent).getOrElse("").trim.toLowerCase
            if label == "[link]" || label == "[comments]" || label.isEmpty then
                Option(a.parentNode).foreach(_.removeChild(a))
        }
        NetworkUtils
            .unsafeParseToHtmlFragment(strip(body.innerHTML))
            .amend(cls := "rr-article-body")
end Descriptions
