name := "SimpleCircuitBreaker"

version := "1.0"

scalaVersion := "3.0.2"

val catsRetryVersion = "3.0.0"
val http4sVersion = "0.23.4"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "3.2.9",
  "org.scalatest" %% "scalatest" % "3.1.1" % "test" cross CrossVersion.for3Use2_13,
  "com.github.cb372" %% "cats-retry"        % "3.1.0",
  "org.http4s" %% "http4s-dsl" % http4sVersion cross CrossVersion.for3Use2_13,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion cross CrossVersion.for3Use2_13,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion cross CrossVersion.for3Use2_13,
  "org.typelevel" %% "munit-cats-effect-3" % "1.0.6" % Test,
  "com.typesafe" % "config" % "1.4.1"
)
