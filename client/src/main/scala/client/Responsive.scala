package client

import com.raquo.airstream.state.Var
import org.scalajs.dom

/** Single source of truth for "is this a phone". A media query listener rather than a resize
  * handler, so it fires once per breakpoint crossing. Breakpoint matches client/style.css (767px).
  */
object Responsive:

    private val query = "(max-width: 767px)"
    private val mql = dom.window.matchMedia(query)
    private val isMobileVar: Var[Boolean] = Var(mql.matches)

    mql.addEventListener("change", (_: dom.Event) => isMobileVar.set(mql.matches))

    def isMobile: Boolean = isMobileVar.now()
end Responsive
