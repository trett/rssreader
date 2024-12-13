import com.typesafe.sbt.packager.docker.ExecCmd
import com.typesafe.sbt.packager.docker.Cmd
import org.scalajs.linker.interface.ModuleSplitStyle
import scala.sys.process._
import java.io._
import NativePackagerHelper._

val circeVersion = "0.14.9"
val projectVersion = "1.0.4"

lazy val buildClientDist = taskKey[File]("Build client optimized package")
ThisBuild / buildClientDist := {
  Process("npm install", client.base).!
  Process("npm run build", client.base).!
  new java.io.File(client.base.getPath + "/dist")
}

lazy val buildImages = taskKey[Unit]("Build docker images")
buildImages := {
  (client / Docker / publishLocal).value
  (server / Docker / publishLocal).value
}

lazy val pushImages = taskKey[Unit]("Push docker images to remote repository")
pushImages := {
  (client / Docker / publish).value
  (server / Docker / publish).value
}

lazy val client = project
  .in(file("client"))
  .enablePlugins(ScalaJSPlugin, DockerPlugin)
  .settings(
    version := projectVersion,
    scalaVersion := "3.3.4",
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
    libraryDependencies += "com.raquo" %%% "laminar" % "17.0.0",
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
        scalaVersion := "3.3.3",
        semanticdbEnabled := true,
        semanticdbVersion := scalafixSemanticdb.revision
      )
    )
  )

lazy val server = project
  .in(file("server"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    version := projectVersion,
    scriptClasspath := Seq("*"),
    javacOptions ++= Seq("-source", "17", "-target", "17"),
    Compile / mainClass := Some("ru.trett.rss.RssApplication"),
    Compile / packageDoc / mappings := Seq(),
    dockerRepository := sys.env.get("REGISTRY"),
    dockerBaseImage := "eclipse-temurin:17-jre-noble",
    dockerExposedPorts := Seq(8080),
    excludeDependencies +=
      ExclusionRule("ch.qos.logback", "logback-classic"),
    libraryDependencies ++= Seq(
      "org.postgresql" % "postgresql" % "42.7.3",
      "com.zaxxer" % "HikariCP" % "5.1.0",
      "org.springframework.boot" % "spring-boot" % "3.2.5",
      "org.springframework.boot" % "spring-boot-loader" % "3.2.5",
      "org.springframework.boot" % "spring-boot-autoconfigure" % "3.2.5",
      "org.springframework.boot" % "spring-boot-starter-test" % "3.1.0",
      "org.springframework.boot" % "spring-boot-starter-jdbc" % "3.2.5",
      "org.springframework.boot" % "spring-boot-starter-web" % "3.2.5",
      "org.springframework.boot" % "spring-boot-starter-security" % "3.2.5",
      "org.springframework.boot" % "spring-boot-starter-oauth2-client" % "3.2.5",
      "org.springframework.boot" % "spring-boot-starter-validation" % "3.2.5",
      "com.h2database" % "h2" % "1.4.200",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.17.0",
      "org.flywaydb" % "flyway-database-postgresql" % "10.11.1",
      "org.apache.httpcomponents.client5" % "httpclient5" % "5.3.1",
      "org.apache.commons" % "commons-lang3" % "3.14.0",
      "com.rometools" % "rome" % "2.1.0",
      "org.slf4j" % "slf4j-simple" % "2.0.13",
      "junit" % "junit" % "4.13.2",
      "org.flywaydb" % "flyway-core" % "10.11.1",
      "jakarta.validation" % "jakarta.validation-api" % "3.0.2",
      "jakarta.servlet" % "jakarta.servlet-api" % "6.0.0"
    )
  )
