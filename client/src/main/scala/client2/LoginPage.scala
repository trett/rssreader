package client2

import be.doeraene.webcomponents.ui5.Link
import be.doeraene.webcomponents.ui5.configkeys.IconName
import client2.NetworkUtils.HOST
import com.raquo.laminar.api.L.*

object LoginPage {

    // private val googleButtonStyle = Seq(
    //     backgroundColor := "#4285F4",
    //     color.white,
    //     padding := "10px 20px",
    //     borderRadius.px := 5,
    //     textDecoration.none,
    //     fontSize.rem := 1,
    //     fontWeight.lighter,
    //     textShadow := "0 0px !important",
    // )

    def render: Element = div(
        div(
            justifyContent := "center",
            display := "flex",
            alignItems := "center",
            flexDirection := "column", // Stack all elements vertically
            marginTop := "10%",
            transform := "scale(1.5)",
            h1("Welcome to RSS Reader", fontSize := "2rem", marginBottom := "1rem"),
            p(
                "Please sign up or sign in to continue.",
                fontSize := "1.2rem",
                marginBottom := "2rem",
                textAlign := "center"
            ),
            div(
                display := "flex",
                flexDirection := "column", // Stack buttons vertically
                alignItems := "center", // Center buttons horizontally
                gap := "1rem", // Add spacing between buttons

                Link(
                    cls("google-button"),
                    "Sign Up",
                    _.href := s"${HOST}/signup",
                    typ("button"),
                    _.icon := IconName.`sys-add`
                ),
                Link(
                    cls("google-button"),
                    "Sign In",
                    _.href := s"${HOST}/signin",
                    typ("button"),
                    _.icon := IconName.`sys-enter`
                )
            )
        )
    )
}
