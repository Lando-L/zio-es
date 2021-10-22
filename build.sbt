ThisBuild / organization := "dev.zio"
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.0.2"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val zioVersion = "1.0.12"
lazy val zioKafkaVersion = "0.17.0"

lazy val root = (project in file("."))
  .settings(name := "zio-es")
  .aggregate(core, coreTests)

lazy val core = (project in file("core"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"       %% "zio"              % zioVersion,
      "dev.zio"       %% "zio-streams"      % zioVersion
    )
  )

lazy val coreTests = (project in file("core-tests"))
  .dependsOn(core)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"       %% "zio"              % zioVersion,
      "dev.zio"       %% "zio-streams"      % zioVersion,
      "dev.zio"       %% "zio-test"         % zioVersion % Test,
      "dev.zio"       %% "zio-test-sbt"     % zioVersion % Test
    )
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val examples = (project in file("examples"))
  .dependsOn(core)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"       %% "zio"              % zioVersion,
      "dev.zio"       %% "zio-streams"      % zioVersion
    )
  )

lazy val kafka = (project in file("kafka"))
  .dependsOn(core)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"       %% "zio"              % zioVersion,
      "dev.zio"       %% "zio-streams"      % zioVersion,
      "dev.zio"       %% "zio-kafka"        % zioKafkaVersion
    )
  )

lazy val kafkaTest = (project in file("kafka-tests"))
  .dependsOn(kafka)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"       %% "zio"              % zioVersion,
      "dev.zio"       %% "zio-streams"      % zioVersion,
      "dev.zio"       %% "zio-kafka"        % zioKafkaVersion,
      "dev.zio"       %% "zio-test"         % zioVersion % Test,
      "dev.zio"       %% "zio-test-sbt"     % zioVersion % Test
    )
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
