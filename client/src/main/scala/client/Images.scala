package client

import com.raquo.laminar.api.L.*

/** Feed images are third-party URLs we never validate: they 404, expire, or block hotlinking. A
  * broken `<img>` still occupies its box, so a dead thumbnail renders as an empty square instead of
  * simply being absent.
  *
  * These images are decorative — the headline and description already carry the meaning — so on
  * failure the element is dropped and the row reflows exactly like an item that had no image at
  * all, which is the layout the design already uses for those.
  */
object Images:

    def decorative(url: String, className: String): HtmlElement =
        val failed = Var(false)
        img(
            cls := className,
            cls("is-broken") <-- failed.signal,
            src := url,
            alt := "",
            loadingAttr := "lazy",
            onError.mapTo(true) --> failed
        )
end Images
