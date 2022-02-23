export class OAuthService {

    // private static readonly SERVER_PORT = 8080;

    public static doAuth(): void {
        if (window.location.hash) {
            const searchParams = new URLSearchParams(window.location.hash.substring(2)); // removing leading ("/#")
            const accessToken = searchParams.get("access_token");
            const state = searchParams.get("state");
            // check if redirect from oauth
            if (state === "authorized" && accessToken) {
                localStorage.setItem("access_token", accessToken as string);
                window.location.href = "http://localhost:" + process.env.SERVER_PORT;
                return;
            }
        }
        this.oauthSignIn();
    }

    private static authorized = false;

    private static oauthSignIn() {
        // Google's OAuth 2.0 endpoint for requesting an access token
        const oauth2Endpoint = "https://accounts.google.com/o/oauth2/v2/auth";

        // Create <form> element to submit parameters to OAuth 2.0 endpoint.
        const form = document.createElement("form");
        form.setAttribute("method", "GET"); // Send as a GET request.
        form.setAttribute("action", oauth2Endpoint);

        // Parameters to pass to OAuth 2.0 endpoint.
        const params = new Map<string, string>([
            ["client_id", process.env.CLIENT_ID as string],
            ["redirect_uri", "http://localhost:" + process.env.SERVER_PORT as string],
            ["response_type", "token"],
            ["scope", "https://www.googleapis.com/auth/drive.metadata.readonly"],
            ["include_granted_scopes", "true"],
            ["state", "authorized"],
        ]);

        // Add form parameters as hidden input values.
        params.forEach((k, v) => {
            const input = document.createElement("input");
            input.setAttribute("type", "hidden");
            input.setAttribute("name", v);
            input.setAttribute("value", k);
            form.appendChild(input);
        });

        // Add form to page and submit it to open the OAuth 2.0 endpoint.
        document.body.appendChild(form);
        form.submit();
    }

}
