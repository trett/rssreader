# Project Overview

This is a web-based RSS reader application. It consists of a Scala-based backend and a Scala.js-based frontend. The application allows users to subscribe to RSS feeds and read articles.

## Build System

The project uses a hybrid build system:
- **sbt (Simple Build Tool)** is the primary build tool for the Scala projects (server, client, and shared modules). The main build configuration is in `build.sbt`.
- **npm/Vite** is used for the frontend development workflow. `package.json` in the `client` directory defines frontend dependencies and scripts. Vite is used for the development server and for bundling frontend assets for production.
- building the frontend is integrated into the sbt build process using the `@scala-js/vite-plugin-scalajs` plugin. `sbt client/fullOptJS`command uses to build the optimized frontend assets.
- there are Scalafmt and Scalastyle configurations for code formatting and style checking.

## Language

The project uses **Scala 3**. Always use Scala 3 syntax and keywords:
- Use `given` instead of `implicit` for implicit values and conversions
- Use `using` instead of `implicit` for context parameters
- Use the new control structure syntax (optional braces when appropriate)
- Use `extension` methods instead of implicit classes
- Prefer end markers for better readability in longer code blocks

## Frameworks & Libraries

### Backend (Server)

- **http4s**: The server is built using http4s, a functional, type-safe HTTP library for Scala.
- **doobie**: Database access is handled with doobie, a functional JDBC layer for Scala, connecting to a PostgreSQL database.
- **Flyway**: Database migrations are managed with Flyway.
- **circe**: Used for JSON serialization and deserialization.
- **PureConfig**: For loading configuration from files.
- **log4cats/Logback**: Used for logging.
- **ScalaTest/ScalaMock**: Used for testing.

### Frontend (Client)

- **Scala.js**: The frontend is written in Scala and compiled to JavaScript using Scala.js.
- **Laminar**: The UI is built using Laminar, a reactive UI library for Scala.js.
- **UI5 Web Components**: The project uses UI5 Web Components for a consistent and modern UI. There is a binding to `Laminar` that is used to interact with UI5. https://github.com/sherpal/LaminarSAPUI5Bindings 
- **Vite**: The `@scala-js/vite-plugin-scalajs` plugin integrates Scala.js with Vite for a fast development experience and optimized builds.

## Project Structure

The project is a multi-module sbt project with the following key directories:

- `server/`: Contains the backend Scala application.
- `client/`: Contains the frontend Scala.js application and its related web assets and npm configuration.
- `shared/`: Contains Scala code (e.g., data models) that is shared between the server and the client.
- `scripts/`: Contains Docker Compose files for local development environments.
- `.github/workflows/`: Contains CI/CD workflows for GitHub Actions, defining the build and test processes.
- `build.sbt`: The central sbt build definition file.

## Workflows

- **Building**: The project is built using `sbt`. The frontend is built via an `npm` command triggered from within sbt.
- **Testing**: Tests are run using `sbt test`.
- **Running Locally**: The `scripts` directory contains `docker-compose.yml` files to run the application stack (backend, frontend, database) locally.
- **CI/CD**: GitHub Actions are configured to build and test the application on push and pull requests.
- **Code style**: Run `scalafixAll` and `scalafmtAll` every time before make a commit to make the style consistently.

## Repository

- The main branch for this project is called "main". Never push directly to main; always use feature branches and create pull requests for changes.
- Commit messages should follow the Conventional Commits specification for clarity and consistency.

