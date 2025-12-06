[![Build and Publish](https://github.com/trett/rssreader/actions/workflows/build.yml/badge.svg)](https://github.com/trett/rssreader/actions/workflows/build.yml)

# RSS Reader

A modern, web-based RSS reader application built with Scala, Scala.js, and Laminar. It provides a simple and clean interface for reading your favorite RSS feeds, with the ability to generate summaries of unread articles.

## Features

-   **Feed Management**: Add and manage your RSS feed subscriptions.
-   **Clean Reading Interface**: A simple and uncluttered interface for reading articles.
-   **Automatic Updates**: Feeds are automatically fetched in the background to keep your content up-to-date.
-   **AI-Powered Summaries**: Generate summaries of all your unread articles using Google's Generative AI.
-   **Secure Authentication**: Authentication is handled securely via Google OAuth2.
-   **Responsive Design**: The application is designed to work on both desktop and mobile browsers.
-   **Observability**: Built-in metrics collection with OpenTelemetry, exportable to Prometheus and visualizable in Grafana.

## Tech Stack

### Backend

-   [Scala](https://www.scala-lang.org/)
-   [http4s](https://http4s.org/): A functional, type-safe HTTP library.
-   [doobie](https://tpolecat.github.io/doobie/): A functional JDBC layer for Scala.
-   [PostgreSQL](https://www.postgresql.org/): The application uses a PostgreSQL database.
-   [Flyway](https://flywaydb.org/): For database migrations.
-   [circe](https://circe.github.io/circe/): For JSON manipulation.
-   [PureConfig](https://pureconfig.github.io/): For loading configuration.
-   [OpenTelemetry](https://opentelemetry.io/): For metrics collection and observability.
-   [Prometheus](https://prometheus.io/): For metrics storage and querying.
-   [Grafana](https://grafana.com/): For metrics visualization and dashboards.

### Frontend

-   [Scala.js](https://www.scala-js.org/): To compile Scala code to JavaScript.
-   [Laminar](https://laminar.dev/): A reactive UI library for Scala.js.
-   [UI5 Web Components](https://sap.github.io/ui5-webcomponents/): A set of enterprise-grade UI components.
-   [Vite](https://vitejs.dev/): For frontend tooling and development server.

## Getting Started

### Prerequisites

-   [Java 17+](https://www.oracle.com/java/technologies/downloads/)
-   [sbt](https://www.scala-sbt.org/)
-   [Node.js](https://nodejs.org/) and [npm](https://www.npmjs.com/)
-   [Docker](https://www.docker.com/) and [Docker Compose](https://docs.docker.com/compose/)

### Running with Docker (Recommended)

This is the easiest way to run the application.

1.  **Set up environment variables**:
    You'll need to provide your Google OAuth credentials. Create a `.env` file in the `scripts/local-docker` directory with the following content:
    ```
    CLIENT_ID=your_google_client_id
    CLIENT_SECRET=your_google_client_secret
    GOOGLE_API_KEY=your_google_ai_api_key
    ```

2.  **Build Docker images**:
    This command will build the Docker image.
    ```bash
    sbt buildImage
    ```

3.  **Run the application**:
    Use Docker Compose to start all the services.
    ```bash
    docker-compose -f scripts/local-docker/docker-compose.yml up
    ```
    The application will be available at:
    - Main app: `http://localhost`
    - Prometheus: `http://localhost:9090`
    - Grafana: `http://localhost:3000` (default credentials: admin/admin)
    - Metrics endpoint: `http://localhost:9464/metrics`

### Local Development

This setup is for actively developing the application.

1.  **Start the database**:
    Prepare and start a PostgreSQL database instance.


2.  **Run the backend server**:
    In a new terminal, start the backend server using sbt. You need to set the required environment variables.
    ```bash
    export CLIENT_ID=your_google_client_id
    export CLIENT_SECRET=your_google_client_secret
    export GOOGLE_API_KEY=your_google_ai_api_key
    sbt server/run
    ```
    The server will be running on `http://localhost`.


## Metrics and Monitoring

The application includes OpenTelemetry metrics collection with Prometheus and Grafana support.

When running with Docker Compose:
- **Prometheus**: `http://localhost:9090`
- **Grafana**: `http://localhost:3000` (admin/admin)
- **Metrics endpoint**: `http://localhost:9464/metrics`


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
| `GOOGLE_API_KEY`  | The API key for Google's Generative AI.                                                  | -                              | For summary feature |
| `REGISTRY`        | The Docker registry to push the image to                                                 | -                              | No                 |

## Deployment

The Docker image built with `sbt buildImage` can be used for production deployment. The image includes both the backend server and frontend assets. You can adapt the `scripts/local-docker/docker-compose.yml` file for your production environment. Remember to configure all the necessary environment variables.

To push the image to a registry, set the `REGISTRY` environment variable and run:
```bash
sbt pushImage
```

## License

This project is licensed under the MIT License.
