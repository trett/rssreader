package client2

import be.doeraene.webcomponents.ui5.Link
import be.doeraene.webcomponents.ui5.configkeys.IconName
import be.doeraene.webcomponents.ui5.configkeys.LinkDesign
import client2.NetworkUtils.HOST
import com.raquo.laminar.api.L.*

object LoginPage {

    def render: Element = div(
        div(
            justifyContent := "center",
            display := "flex",
            alignItems := "center",
            marginTop := "20%",
            transform := "scale(2)",
            Link(
                "Sign Up",
                _.href := s"${HOST}/signup",
                typ("button"),
                _.design := LinkDesign.Emphasized,
                _.icon := IconName.`sys-add`
            ),
            div(
                Link(
                    "Sign In",
                    _.href := s"${HOST}/signin",
                    typ("button"),
                    _.design := LinkDesign.Emphasized,
                    _.icon := IconName.`sys-enter`
                )
            )
        )
    )
}
