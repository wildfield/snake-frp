enablePlugins(ScalaJSPlugin)

name := "FRP Snake"
scalaVersion := "3.2.2"

// This is an application with a main method
scalaJSUseMainModuleInitializer := true

libraryDependencies += ("org.scala-js" %%% "scalajs-dom" % "2.4.0")
