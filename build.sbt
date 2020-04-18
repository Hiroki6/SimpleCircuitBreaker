name := "SimpleCircuitBreaker"

version := "0.1"

scalaVersion := "2.13.1"

val catsRetryVersion = "0.3.2"
val http4sVersion = "0.21.2"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "2.1.2",
  "com.github.pureconfig" %% "pureconfig" % "0.12.3",
  "org.scalatest" %% "scalatest" % "3.1.1" % "test",
  "com.github.cb372" %% "cats-retry-core"        % catsRetryVersion,
  "com.github.cb372" %% "cats-retry-cats-effect" % catsRetryVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion
)
