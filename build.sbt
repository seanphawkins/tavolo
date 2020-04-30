name := "tavolo"

version := "0.1"

scalaVersion := "2.13.1"

val zio = "1.0.0-RC18-2"
val quill = "3.5.1"
val akka = "2.6.4"
val akkaHttp = "10.1.11"
val jwt = "4.2.0"
val postgres = "42.2.8"
val logback = "1.2.3"
val jsonIter = "2.1.12"
val zioLogging = "0.2.6"
val refined = "0.9.14"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % zio,

  "dev.zio" %% "zio-logging" % zioLogging,
  "dev.zio" %% "zio-logging-slf4j" % zioLogging,

  "eu.timepit" %% "refined" % refined,

  "com.typesafe.akka" %% "akka-actor-typed" % akka,
  "com.typesafe.akka" %% "akka-stream" % akka,
  "com.typesafe.akka" %% "akka-stream-typed" % akka,
  "com.typesafe.akka" %% "akka-slf4j" % akka,
  "com.typesafe.akka" %% "akka-http"   % akkaHttp,

  "ch.megard" %% "akka-http-cors" % "0.4.2",

  "ch.qos.logback" % "logback-classic" % logback,

  "io.getquill" %% "quill-core" % quill,
  "io.getquill" %% "quill-jdbc" % quill,

  "org.postgresql" % "postgresql" % postgres,

  "com.pauldijou" %% "jwt-core" % jwt,

  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % jsonIter,
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsonIter,

  "dev.zio" %% "zio-test" % zio % Test,
)