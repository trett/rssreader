[![Build and Publish](https://github.com/trett/rssreader/actions/workflows/build.yml/badge.svg)](https://github.com/trett/rssreader/actions/workflows/build.yml)

# RSS Reader

A modern, containerized RSS reader application built with Scala, Scala.js, and Laminar. Designed for cloud-native deployment (optimized for Google Cloud Run), it provides a simple and clean interface for reading your favorite RSS feeds.

## Features

-   **Feed Management**: Add and manage your RSS feed subscriptions.
-   **Clean Reading Interface**: A simple and uncluttered interface for reading articles.
-   **Automatic Updates**: Feeds are automatically fetched in the background to keep your content up-to-date.
-   **Secure Authentication**: Authentication is handled securely via Google OAuth2.
-   **Responsive Design**: The application is designed to work on both desktop and mobile browsers.

## Tech Stack

### Backend

-   [Scala](https://www.scala-lang.org/)
-   [http4s](https://http4s.org/): A functional, type-safe HTTP library.
-   [doobie](https://tpolecat.github.io/doobie/): A functional JDBC layer for Scala.
-   [PostgreSQL](https://www.postgresql.org/): The application uses a PostgreSQL database.
-   [circe](https://circe.github.io/circe/): For JSON manipulation.
-   [PureConfig](https://pureconfig.github.io/): For loading configuration.

### Frontend

-   [Scala.js](https://www.scala-js.org/): To compile Scala code to JavaScript.
-   [Laminar](https://laminar.dev/): A reactive UI library for Scala.js.
-   [UI5 Web Components](https://sap.github.io/ui5-webcomponents/): A set of enterprise-grade UI components.
-   [Vite](https://vitejs.dev/): For frontend tooling and development server.

## Getting Started

### Prerequisites

-   [Java 17+](https://www.oracle.com/java/technologies/downloads/)
-   [sbt](https://www.scala-sbt.org/)
-   [Node.js 20](https://nodejs.org/) and [npm](https://www.npmjs.com/) (version specified in `.nvmrc`)
-   [Docker](https://www.docker.com/) and [Docker Compose](https://docs.docker.com/compose/)

### Running Locally with Docker

This is the easiest way to test the application locally in a production-like environment.

1.  **Set up environment variables**:
    You'll need to provide your Google OAuth credentials. Create a `.env` file in the `scripts/local-docker` directory with the following content:
    ```
    CLIENT_ID=your_google_client_id
    CLIENT_SECRET=your_google_client_secret
    JWT_SECRET=your_secure_jwt_secret
    ```

2.  **Build Docker images**:
    This command will build the Docker image locally.
    ```bash
    sbt buildImage
    ```

3.  **Run the application**:
    Use Docker Compose to start all the services (App, Postgres, Caddy).
    ```bash
    docker-compose -f scripts/local-docker/docker-compose.yml up
    ```
    The application will be available at:
    - Main app: `http://localhost`

### Local Development

This setup is for actively developing the application with hot-reloading where possible.

1.  **Start the database**:
    Prepare and start a PostgreSQL database instance.


2.  **Run the backend server**:
    In a new terminal, start the backend server using sbt. You need to set the required environment variables.
    ```bash
    export CLIENT_ID=your_google_client_id
    export CLIENT_SECRET=your_google_client_secret
    export JWT_SECRET=your_secure_jwt_secret
    sbt server/run
    ```
    The server will be running on `http://localhost`.

## Claude Desktop (MCP)

The server exposes a [Model Context Protocol](https://modelcontextprotocol.io/) endpoint so
Claude can query your news by date. It speaks JSON-RPC 2.0 over `POST /mcp` and offers three
tools:

-   **`get_current_time`** — the server's current time as an ISO-8601 UTC datetime, so Claude can
    resolve relative dates like "today" or "last 24 hours".
-   **`get_news_by_date`** — fetch the **important** items (flagged important, or from a
    highlighted channel) across **all your channels in one call, grouped by channel**, newest
    first. **All arguments are optional:**
    - with no arguments it returns the **last 24 hours** (this is the way to get "latest news");
    - `from`/`to` set the range (defaults to `now − 24h … now`, max span **24 hours**);
    - `limit` caps items **per channel** (default 100).

Because results are grouped per channel — each group a single feed — Claude can detect that feed's
language and translate accurately, all from one call, so it never queries channels individually.

Requests are authenticated with your JWT sent as an `Authorization: Bearer <token>` header. While
logged in to the web app, mint a long-lived token from:

```
GET /api/user/mcp-token   ->   { "token": "<jwt>" }
```

Claude Desktop connects through the [`mcp-remote`](https://www.npmjs.com/package/mcp-remote)
bridge. Add this to `claude_desktop_config.json` (macOS:
`~/Library/Application Support/Claude/claude_desktop_config.json`), then restart Claude Desktop:

```jsonc
{
  "mcpServers": {
    "rssreader": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "https://<your-host>/mcp",
        "--header",
        "Authorization:Bearer ${RSS_MCP_TOKEN}"
      ],
      "env": { "RSS_MCP_TOKEN": "<token from /api/user/mcp-token>" }
    }
  }
}
```

You can then ask Claude things like *"summarize today's important news from each of my feeds."*

### Claude.ai (web) custom connector

The web app's custom connectors cannot set an `Authorization` header, so the same endpoint
also accepts the token **in the URL path**: `POST /mcp/<token>`. Mint a token from
`GET /api/user/mcp-token` as above, then in Claude.ai go to **Settings → Connectors → Add
custom connector** and paste:

```
https://<your-host>/mcp/<token from /api/user/mcp-token>
```

No header or OAuth flow is required. Note the token is embedded in the URL, so treat it as a
secret (it can appear in logs and browser history); revoke it by rotating `JWT_SECRET` if it
leaks.

## Configuration

The application is configured using environment variables.

| Variable          | Description                                                                              | Default Value                  | Required           |
| ----------------- | ---------------------------------------------------------------------------------------- | ------------------------------ | ------------------ |
| `SERVER_PORT`     | The port for the backend server.                                                         | `8080`                         | No                 |
| `DATASOURCE_URL`  | The JDBC URL for the PostgreSQL database.                                                | `jdbc:postgresql://localhost:5432/rss` | No                 |
| `DATASOURCE_USER` | The username for the database.                                                           | `rss_user`                     | No                 |
| `DATASOURCE_PASS` | The password for the database.                                                           | `123456`                       | No                 |
| `CLIENT_ID`       | The client ID for Google OAuth2.                                                         | -                              | **Yes**            |
| `CLIENT_SECRET`   | The client secret for Google OAuth2.                                                     | -                              | **Yes**            |
| `SERVER_URL`      | The public URL of the server. Used for OAuth redirect URI.                               | `https://localhost`            | No                 |
| `CORS_URL`        | The allowed origin for CORS requests.                                                    | `https://localhost`            | No                 |
| `JOB_TOKEN`       | Secret token for triggering background jobs via HTTP.                                    | -                              | No                 |
| `JWT_SECRET`      | Secret string used for signing JWT tokens.                                               | -                              | **Yes**            |
| `REGISTRY`        | The Docker registry to push the image to                                                 | -                              | No                 |

## Deployment

The application is optimized for deployment as a **Google Cloud Run** service. The container image includes both the backend server and the pre-built frontend assets.

For a comprehensive step-by-step deployment guide, including Cloud SQL and Cloud Scheduler setup, please refer to **[DEPLOY.md](DEPLOY.md)**.

### Building and Pushing to Registry

To build the production image and push it to your configured container registry:

1. Set the `REGISTRY` environment variable:
   ```bash
   export REGISTRY=docker.pkg.dev/your-project/your-repo/rss-reader
   ```

2. Run the push command:
   ```bash
   sbt pushImage
   ```

## License

This project is licensed under the MIT License.
