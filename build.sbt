import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper.*
import com.typesafe.sbt.packager.docker.Cmd
import org.scalajs.linker.interface.ModuleSplitStyle

import scala.sys.process.*

lazy val projectVersion = "2.2.1"
lazy val organizationName = "ru.trett"
lazy val scala3Version = "3.7.2"
lazy val circeVersion = "0.14.14"
lazy val htt4sVersion = "1.0.0-M39"
lazy val logs4catVersion = "2.7.1"
lazy val customScalaOptions = Seq("-Wunused:imports", "-rewrite", "-source:3.4-migration")

lazy val buildClientDist = taskKey[File]("Build client optimized package")
lazy val buildImages = taskKey[Unit]("Build docker images")
lazy val pushImages = taskKey[Unit]("Push docker images to remote repository")

lazy val shared = crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Pure)
    .in(file("shared"))
    .settings(
        name := "shared",
        version := projectVersion,
        organization := organizationName,
        scalaVersion := scala3Version,
        scalacOptions ++= customScalaOptions,
    )
    .jsSettings()
    .jvmSettings()

lazy val client = project
    .in(file("client"))
    .dependsOn(shared.js)
    .enablePlugins(ScalaJSPlugin, DockerPlugin)
    .settings(
        version := projectVersion,
        organization := organizationName,
        scalaVersion := scala3Version,
        scalaJSUseMainModuleInitializer := true,
        scalaJSLinkerConfig ~= {
            _.withModuleKind(ModuleKind.ESModule)
                .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("client")))
        },
        Compile / sourceGenerators += Def.task {
            val out =
                (Compile / sourceManaged).value / "scala/client/AppConfig.scala"
            IO.write(
                out,
                s"""
        package client
        object AppConfig {
          val BASE_URI="${sys.env.getOrElse("SERVER_URL", "https://localhost")}"
        }
        """
            )
            Seq(out)
        },
        Universal / mappings ++= directory(buildClientDist.value),
        dockerRepository := sys.env.get("REGISTRY"),
        dockerCommands := Seq(
            Cmd("FROM", "nginx:1.29.1-alpine"),
            Cmd("COPY", "opt/docker/dist/", "/usr/share/nginx/html/")
        ),
        dockerExposedPorts := Seq(80),
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
        libraryDependencies ++= Seq(
            "org.typelevel" %% "cats-effect" % "3.6.3",
            "org.slf4j" % "slf4j-api" % "2.0.13",
            "ch.qos.logback" % "logback-classic" % "1.5.6",
            "org.flywaydb" % "flyway-core" % "10.15.2",
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
        libraryDependencies += "org.jsoup" % "jsoup" % "1.21.1", 
        libraryDependencies += "org.flywaydb" % "flyway-database-postgresql" % "11.10.4" % "runtime",
        libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test,
        libraryDependencies += "org.scalamock" %% "scalamock" % "7.4.0" % Test,
        libraryDependencies += "org.testcontainers" % "testcontainers" % "1.19.3" % Test,
        libraryDependencies += "org.testcontainers" % "postgresql" % "1.19.3" % Test,
        libraryDependencies += "org.postgresql" % "postgresql" % "42.7.1" % Test,
        scalacOptions ++= customScalaOptions,
        Compile / run / fork := true,
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
    new java.io.File(client.base.getPath + "/dist")
}
buildImages := {
    (client / Docker / publishLocal).value
    (server / Docker / publishLocal).value
}
pushImages := {
    (client / Docker / publish).value
    (server / Docker / publish).value
}
