package client

import be.doeraene.webcomponents.ui5.Link
import be.doeraene.webcomponents.ui5.configkeys.IconName
import client.NetworkUtils.HOST
import com.raquo.laminar.api.L.*

object LoginPage {

    def render: Element = div(
        div(
            background := "url(images/background.png)",
            backgroundSize := "cover",
            backgroundPosition := "center",
            backgroundRepeat := "no-repeat",
            height := "100vh",
            justifyContent := "center",
            display := "flex",
            alignItems := "center",
            div(
                background := "white",
                padding := "40px",
                borderRadius := "10px",
                boxShadow := "0 12px 24px rgba(0,0,0,0.5)",
                width := "400px",
                display := "flex",
                flexDirection := "column",
                alignItems := "center",
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
                        cls("google-button", "signup-button"),
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
    )
}
