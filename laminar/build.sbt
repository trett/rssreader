import org.scalajs.linker.interface.ModuleSplitStyle

val circeVersion = "0.14.9"
lazy val client2 = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaVersion := "3.3.4",
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("client2")))
    },
    sourceGenerators in Compile += Def.task {
      val out = (sourceManaged in Compile).value / "scala/client2/AppConfig.scala"
      IO.write(
        out,
        s"""
        package client2
        object AppConfig {
          val BASE_URI="${sys.env.getOrElse("BASE_URI", "https://localhost")}"
        }
        """
      )
      Seq(out)
    },
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
