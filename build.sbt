import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper.*
import com.typesafe.sbt.packager.docker.Cmd
import org.scalajs.linker.interface.ModuleSplitStyle

import scala.sys.process.*

lazy val scala3Version = "3.3.5"
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
        scalacOptions += "-Wunused:imports"
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
                .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("client2")))
        },
        Compile / sourceGenerators += Def.task {
            val out =
                (Compile / sourceManaged).value / "scala/client2/AppConfig.scala"
            IO.write(
                out,
                s"""
        package client2
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
            Cmd("FROM", "nginx:1.26-alpine"),
            Cmd("COPY", "opt/docker/dist/", "/usr/share/nginx/html/")
        ),
        dockerExposedPorts := Seq(80),
        libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.0",
        libraryDependencies += "com.raquo" %%% "laminar" % "17.2.0",
        libraryDependencies += "be.doeraene" %%% "web-components-ui5" % "2.0.0",
        libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.6.0",
        libraryDependencies ++= Seq(
            "io.circe" %%% "circe-core",
            "io.circe" %%% "circe-generic",
            "io.circe" %%% "circe-parser"
        ).map(_ % circeVersion),
        scalacOptions += "-Wunused:imports",
        inThisBuild(
            List(
                scalaVersion := "3.3.5",
                semanticdbEnabled := true,
                semanticdbVersion := scalafixSemanticdb.revision
            )
        )
    )
lazy val server2 = project
    .in(file("server2"))
    .dependsOn(shared.jvm)
    .enablePlugins(JavaAppPackaging, DockerPlugin)
    .settings(
        version := projectVersion,
        organization := organizationName,
        scalaVersion := scala3Version,
        name := "server2",
        dockerBaseImage := "eclipse-temurin:17-jre-noble",
        dockerRepository := sys.env.get("REGISTRY"),
        dockerExposedPorts := Seq(8080),
        libraryDependencies ++= Seq(
            "org.typelevel" %% "cats-effect" % "3.5.0",
            "org.slf4j" % "slf4j-api" % "2.0.9",
            "ch.qos.logback" % "logback-classic" % "1.4.11",
            "org.flywaydb" % "flyway-core" % "9.22.3",
            "com.github.pureconfig" %% "pureconfig-core" % "0.17.8",
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
        ).map(_ % catsEffectVersion),
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
        scalacOptions += "-Wunused:imports",
        inThisBuild(
            List(
                scalaVersion := "3.3.5",
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
val organizationName = "ru.trett"
buildImages := {
    (client / Docker / publishLocal).value
    (server2 / Docker / publishLocal).value
}
val circeVersion = "0.14.9"
pushImages := {
    (client / Docker / publish).value
    (server2 / Docker / publish).value
}
val projectVersion = "1.0.7-2"
val htt4sVersion = "1.0.0-M39"
val catsEffectVersion = "2.7.0"
