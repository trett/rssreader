import org.scalajs.linker.interface.ModuleSplitStyle

val circeVersion = "0.14.9"
lazy val client2 = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin) // Enable the Scala.js plugin in this project
  .settings(
    scalaVersion := "3.3.4",

    // Tell Scala.js that this is an application with a main method
    scalaJSUseMainModuleInitializer := true,

    /* Configure Scala.js to emit modules in the optimal way to
     * connect to Vite's incremental reload.
     * - emit ECMAScript modules
     * - emit as many small modules as possible for classes in the "livechart" package
     * - emit as few (large) modules as possible for all other classes
     *   (in particular, for the standard library)
     */
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("client2")))
    },

    /* Depend on the scalajs-dom library.
     * It provides static types for the browser DOM APIs.
     */
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.0",
    // Depend on Laminar
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