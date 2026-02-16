import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper.*
import com.typesafe.sbt.packager.docker.*
import org.scalajs.linker.interface.ModuleSplitStyle
import com.typesafe.sbt.packager.docker.DockerApiVersion

import scala.sys.process.*

lazy val projectVersion = "2.4.4-gcr"
lazy val organizationName = "ru.trett"
lazy val scala3Version = "3.7.4"
lazy val circeVersion = "0.14.15"
lazy val htt4sVersion = "1.0.0-M45"
lazy val logs4catVersion = "2.7.1"
lazy val doobieVersion = "1.0.0-RC11"
lazy val customScalaOptions = Seq("-Wunused:imports", "-rewrite", "-source:3.4-migration")

lazy val buildClientDist = taskKey[File]("Build client optimized package")
lazy val buildImage = taskKey[Unit]("Build docker image")
lazy val pushImage = taskKey[Unit]("Push docker image to remote repository")
lazy val generateMigrationFiles = taskKey[Seq[File]]("Generate MigrationFiles.scala with list of SQL migrations")

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
    .enablePlugins(JavaAppPackaging, DockerPlugin, GraalVMNativeImagePlugin)
    .settings(
        version := projectVersion,
        organization := organizationName,
        scalaVersion := scala3Version,
        name := "server",
        dockerPermissionStrategy := DockerPermissionStrategy.None,
        dockerBaseImage := "debian:12-slim",
        dockerApiVersion := Some(DockerApiVersion(1, 40)),
        dockerCommands := {
            val commands = dockerCommands.value
            val filteredCommands = commands.filter {
                case Cmd("RUN", _*) => false
                case Cmd("USER", _*) => false
                case Cmd("ENTRYPOINT", _*) => false
                case Cmd("CMD", _*) => false
                case Cmd("WORKDIR", _*) => false
                case ExecCmd("ENTRYPOINT", _*) => false
                case ExecCmd("CMD", _*) => false
                case _ => true
            }
            filteredCommands ++ Seq(
                Cmd("RUN", "apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*"),
                Cmd("WORKDIR", "/opt/docker"),
                ExecCmd(
                    "ENTRYPOINT",
                    "/opt/docker/bin/server"
                )
            )
        },
        dockerRepository := sys.env.get("REGISTRY"),
        dockerExposedPorts := Seq(8080),
        Docker / mappings := {
            val nativeImage = (GraalVMNativeImage / packageBin).value
            val standardMappings = (Docker / mappings).value
            standardMappings.filter { case (file, path) =>
                !path.contains("bin/server") && !path.contains("lib/")
            } :+ (nativeImage -> "/opt/docker/bin/server")
        },
        graalVMNativeImageOptions ++= Seq(
            "--no-fallback",
            "-H:+ReportExceptionStackTraces",
            "--verbose",
            "--enable-https",
            "--enable-http",
            "-H:IncludeResources=application\\.conf",
            "-H:IncludeResources=logback\\.xml",
            "-H:IncludeResources=public/.*",
            "-H:IncludeResources=db/migration/.*",
            "-H:DeadlockWatchdogInterval=900",
            "-Ob",
            "-J-Xmx24G",
            "-R:MaxHeapSize=512m",
            "--initialize-at-build-time=org.slf4j.LoggerFactory,ch.qos.logback,com.fasterxml.jackson",
            "--initialize-at-run-time=io.netty.channel.epoll.Epoll,io.netty.channel.epoll.Native,io.netty.channel.epoll.EpollEventLoop,io.netty.channel.epoll.EpollEventLoopGroup,io.netty.channel.kqueue.KQueue,io.netty.channel.kqueue.Native,io.netty.channel.kqueue.KQueueEventLoopGroup,org.http4s.MimeDB"
        ),
        watchSources ++= (client / Compile / watchSources).value,
        Compile / sourceGenerators += generateMigrationFiles.taskValue,
        libraryDependencies ++= Seq(
            "org.typelevel" %% "cats-effect" % "3.6.3",
            "org.slf4j" % "slf4j-api" % "2.0.17",
            "ch.qos.logback" % "logback-classic" % "1.5.25",
            "org.flywaydb" % "flyway-core" % "11.17.2",
            "com.github.pureconfig" %% "pureconfig-core" % "0.17.9"
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
        ).map(_ % doobieVersion),
        libraryDependencies += "org.jsoup" % "jsoup" % "1.21.2",
        libraryDependencies += "io.circe" %% "circe-fs2" % "0.14.1",
        libraryDependencies += "com.github.jwt-scala" %% "jwt-circe" % "10.0.1",
        libraryDependencies += "com.google.cloud.sql" % "postgres-socket-factory" % "1.15.1",
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
            val _ = (client / Compile / fullLinkJS).value
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

// Task to generate a Scala file containing a list of all Flyway migration files.
// This is required for GraalVM Native Image support because Flyway's classpath scanning
// doesn't work out-of-the-box in a native binary.
server / generateMigrationFiles := {
    val resourceDir = (server / Compile / resourceDirectory).value
    val migrationDir = resourceDir / "db" / "migration"

    // List all .sql files in the migration directory
    val migrations = if (migrationDir.exists()) {
        migrationDir.listFiles().filter(_.getName.endsWith(".sql")).map(_.getName).toList.sorted
    } else Nil

    val file =
        (server / Compile / sourceManaged).value / "ru" / "trett" / "rss" / "server" / "db" / "MigrationFiles.scala"
    val migrationsStr = migrations.map(m => "\"" + m + "\"").mkString(", ")
    val content =
        s"""package ru.trett.rss.server.db
           |
           |/**
           | * This file is automatically generated by sbt.
           | * It contains a list of migration files to be used by Flyway at runtime.
           | */
           |object MigrationFiles {
           |  val list = List($migrationsStr)
           |}
           |""".stripMargin

    IO.write(file, content)
    Seq(file)
}
