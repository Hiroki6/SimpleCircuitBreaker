name := "SimpleCircuitBreaker"

version := "0.1"

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "2.1.2",
  "com.github.pureconfig" %% "pureconfig" % "0.12.3",
  "org.scalatest" %% "scalatest" % "3.1.1" % "test"
)
