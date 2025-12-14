import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper.*
import com.typesafe.sbt.packager.docker.Cmd
import org.scalajs.linker.interface.ModuleSplitStyle

import scala.sys.process.*

lazy val projectVersion = "2.3.2"
lazy val organizationName = "ru.trett"
lazy val scala3Version = "3.7.4"
lazy val circeVersion = "0.14.15"
lazy val htt4sVersion = "1.0.0-M39"
lazy val logs4catVersion = "2.7.1"
lazy val otel4sVersion = "0.14.0"
lazy val customScalaOptions = Seq("-Wunused:imports", "-rewrite", "-source:3.4-migration")

lazy val buildClientDist = taskKey[File]("Build client optimized package")
lazy val buildImage = taskKey[Unit]("Build docker image")
lazy val pushImage = taskKey[Unit]("Push docker image to remote repository")

lazy val shared = crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Pure)
    .in(file("shared"))
    .settings(
        name := "shared",
        version := projectVersion,
        organization := organizationName,
        scalaVersion := scala3Version,
        scalacOptions ++= customScalaOptions
    )
    .jsSettings()
    .jvmSettings()

lazy val client = project
    .in(file("client"))
    .dependsOn(shared.js)
    .enablePlugins(ScalaJSPlugin, UniversalPlugin)
    .settings(
        version := projectVersion,
        organization := organizationName,
        scalaVersion := scala3Version,
        scalaJSUseMainModuleInitializer := true,
        scalaJSLinkerConfig ~= {
            _.withModuleKind(ModuleKind.ESModule)
                .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("client")))
        },
        Universal / mappings ++= directory(buildClientDist.value),
        libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.1",
        libraryDependencies += "com.raquo" %%% "laminar" % "17.2.1",
        libraryDependencies += "be.doeraene" %%% "web-components-ui5" % "2.12.1",
        libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.6.0",
        libraryDependencies ++= Seq(
            "io.circe" %%% "circe-core",
            "io.circe" %%% "circe-generic",
            "io.circe" %%% "circe-parser"
        ).map(_ % circeVersion),
        scalacOptions ++= customScalaOptions,
        Compile / packageDoc / mappings := Seq(),
        inThisBuild(
            List(
                scalaVersion := scala3Version,
                semanticdbEnabled := true,
                semanticdbVersion := scalafixSemanticdb.revision
            )
        )
    )

lazy val server = project
    .in(file("server"))
    .dependsOn(shared.jvm)
    .enablePlugins(JavaAppPackaging, DockerPlugin)
    .settings(
        version := projectVersion,
        organization := organizationName,
        scalaVersion := scala3Version,
        name := "server",
        dockerBaseImage := "eclipse-temurin:17-jre-noble",
        dockerRepository := sys.env.get("REGISTRY"),
        dockerExposedPorts := Seq(8080),
        watchSources ++= (client / Compile / watchSources).value,
        Compile / compile := ((Compile / compile).dependsOn(client / Compile / fastLinkJS)).value,
        javaOptions += "-Dotel.java.global-autoconfigure.enabled=true",
        libraryDependencies ++= Seq(
            "org.typelevel" %% "cats-effect" % "3.6.3",
            "org.slf4j" % "slf4j-api" % "2.0.17",
            "ch.qos.logback" % "logback-classic" % "1.5.21",
            "org.flywaydb" % "flyway-core" % "11.17.2",
            "com.github.pureconfig" %% "pureconfig-core" % "0.17.9",
            "com.rometools" % "rome" % "2.1.0"
        ),
        libraryDependencies ++= Seq(
            "org.http4s" %% "http4s-ember-server",
            "org.http4s" %% "http4s-ember-client",
            "org.http4s" %% "http4s-circe",
            "org.http4s" %% "http4s-dsl"
        ).map(_ % htt4sVersion),
        libraryDependencies ++= Seq(
            "org.typelevel" %% "log4cats-core",
            "org.typelevel" %% "log4cats-slf4j"
        ).map(_ % logs4catVersion),
        libraryDependencies ++= Seq(
            "org.typelevel" %% "otel4s-oteljava",
            "org.typelevel" %% "otel4s-instrumentation-metrics"
        ).map(_ % otel4sVersion),
        libraryDependencies ++= Seq(
            "org.typelevel" %% "otel4s-oteljava" % "0.14.0",
            "io.opentelemetry.instrumentation" % "opentelemetry-runtime-telemetry-java17" % "2.22.0-alpha",
            "io.opentelemetry" % "opentelemetry-exporter-prometheus" % "1.45.0-alpha",
            "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.56.0" % Runtime,
            "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % "1.56.0" % Runtime
        ),
        libraryDependencies ++= Seq(
            "io.circe" %%% "circe-core",
            "io.circe" %%% "circe-generic",
            "io.circe" %%% "circe-parser"
        ).map(_ % circeVersion),
        libraryDependencies ++= Seq(
            "org.tpolecat" %% "doobie-core",
            "org.tpolecat" %% "doobie-hikari",
            "org.tpolecat" %% "doobie-postgres",
            "org.tpolecat" %% "doobie-postgres-circe"
        ).map(_ % "1.0.0-RC5"),
        libraryDependencies += "org.jsoup" % "jsoup" % "1.21.2",
        libraryDependencies += "com.github.blemale" %% "scaffeine" % "5.3.0",
        libraryDependencies += "org.flywaydb" % "flyway-database-postgresql" % "11.17.2" % "runtime",
        libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test,
        libraryDependencies += "org.scalamock" %% "scalamock" % "7.5.2" % Test,
        libraryDependencies += "org.testcontainers" % "testcontainers" % "2.0.2" % Test,
        libraryDependencies += "org.testcontainers" % "postgresql" % "1.21.3" % Test,
        libraryDependencies += "org.postgresql" % "postgresql" % "42.7.8" % Test,
        scalacOptions ++= customScalaOptions,
        Compile / run / fork := true,
        Compile / packageDoc / mappings := Seq(),
        Compile / resourceGenerators += Def.task {
            val _ = (client / Compile / fastLinkJS).value
            val distDir = buildClientDist.value
            val targetDir = (Compile / resourceManaged).value / "public"
            IO.copyDirectory(distDir, targetDir)
            (targetDir ** "*").get
        }.taskValue,
        inThisBuild(
            List(
                scalaVersion := scala3Version,
                semanticdbEnabled := true,
                semanticdbVersion := scalafixSemanticdb.revision
            )
        )
    )
ThisBuild / buildClientDist := {
    Process("npm install", client.base).!
    Process("npm run build", client.base).!
    client.base / "dist"
}
buildImage := {
    (server / Docker / publishLocal).value
}
pushImage := {
    (server / Docker / publish).value
}
