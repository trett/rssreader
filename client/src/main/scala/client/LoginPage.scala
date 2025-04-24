package client

import be.doeraene.webcomponents.ui5.Link
import be.doeraene.webcomponents.ui5.configkeys.IconName
import client.NetworkUtils.HOST
import com.raquo.laminar.api.L.*

object LoginPage {

    def render: Element = div(
        div(
            background := "linear-gradient(to bottom right, #d6dee6, #549a9d)",
            height := "100vh",
            justifyContent := "center",
            display := "flex",
            alignItems := "center",
            flexDirection := "column", // Stack all elements vertically
            h1("RSS Reader", fontSize := "2rem", marginBottom := "1rem"),
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
                    _.href := s"$HOST/signup",
                    typ("button"),
                    _.icon := IconName.`sys-add`
                ),
                Link(
                    cls("google-button"),
                    "Sign In",
                    _.href := s"$HOST/signin",
                    typ("button"),
                    _.icon := IconName.`sys-enter`
                )
            )
        )
    )
}
