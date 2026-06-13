package ru.trett.rss.server.parser

import org.jsoup.Jsoup

object ImageExtractor:

    def firstImageFromHtml(html: String): Option[String] =
        if (html.isEmpty) None
        else
            Option(Jsoup.parseBodyFragment(html).select("img").first()).flatMap { el =>
                val src = el.attr("src")
                if (
                    src.isEmpty || src.startsWith("data:") || src.startsWith("cid:") ||
                    el.attr("width") == "1" || el.attr("height") == "1"
                ) None
                else Some(src)
            }
