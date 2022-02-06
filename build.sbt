enablePlugins(ScalaJSPlugin)

name := "FRP Snake"
scalaVersion := "3.1.0"

// This is an application with a main method
scalaJSUseMainModuleInitializer := true

libraryDependencies += ("org.scala-js" %%% "scalajs-dom" % "2.0.0")
