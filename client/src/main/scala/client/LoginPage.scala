package client

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.svg as S

object LoginPage {

    private val ringCount = 4

    /** RSS wave mark, matching the glyph used in the app's shell bar. */
    private def rssMark: Element = S.svg(
        S.cls := "landing-brand-mark",
        S.viewBox := "0 0 24 24",
        S.fill := "currentColor",
        S.circle(S.cx := "6.18", S.cy := "17.82", S.r := "2.18"),
        S.path(
            S.d := "M4 4.44v2.83c7.03 0 12.73 5.7 12.73 12.73h2.83C19.56 11.41 12.59 4.44 4 4.44z"
        ),
        S.path(S.d := "M4 10.1v2.83c3.9 0 7.07 3.17 7.07 7.07h2.83c0-5.47-4.43-9.9-9.9-9.9z")
    )

    def render: Element = div(
        cls := "landing",
        div(cls := "landing-bar", div(cls := "landing-brand", rssMark, span("RSS Reader"))),
        div(
            cls := "landing-body",
            div(
                cls := "landing-copy",
                span(cls := "landing-eyebrow", "Your feeds, unified"),
                h1(cls := "landing-title", "Every source you follow, in one calm reading list."),
                p(
                    cls := "landing-lead",
                    "Subscribe to news, blogs and podcasts, label what matters, and keep your ",
                    "unread count honest. No algorithm, no noise."
                ),
                a(cls := "landing-button landing-button-primary", href := "/signin", "Sign in"),
                p(
                    cls := "landing-alt",
                    "New here? ",
                    a(cls := "landing-alt-link", href := "/signup", "Create an account")
                )
            ),
            div(
                cls := "landing-panel",
                // Signal waves radiating from the corner dot, drawn as concentric rings.
                List.tabulate(ringCount)(i =>
                    div(cls := "landing-ring", cls := s"landing-ring-${i + 1}")
                ),
                div(cls := "landing-dot"),
                div(
                    cls := "landing-stat",
                    div(cls := "landing-stat-value", "47"),
                    div(cls := "landing-stat-label", "Unread today")
                )
            )
        ),
        div(cls := "landing-footer", "RSS Reader")
    )
}
