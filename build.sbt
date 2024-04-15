import Dependencies._
import Keys._
import sbt.file

lazy val scala2 = "2.13.13"

val commonSettings = Defaults.coreDefaultSettings ++ Seq(
        organization := "nl.pragmasoft.sensors",
        scalaVersion := scala2,
        testOptions += Tests.Argument(TestFrameworks.JUnit, "-v"),
        Test / parallelExecution := false,
        Test / fork := true,
        scalacOptions := Seq(
              s"-unchecked",
              "-deprecation",
              "-feature",
              "-language:higherKinds",
              "-language:existentials",
              "-language:implicitConversions",
              "-language:postfixOps",
              "-encoding",
              "utf8",
              "-Xfatal-warnings"
            ),
        Compile / packageBin / packageOptions
          +=
            Package.ManifestAttributes(
              "Build-Time"   -> new java.util.Date().toString,
              "Build-Commit" -> git.gitHeadCommit.value.getOrElse("No Git Revision Found")
            ),
        doc / sources := Seq.empty,
        packageSrc / publishArtifact := false,
        packageDoc / publishArtifact := true
      ) ++ Publish.settings

lazy val noPublishSettings = Seq(
  publish / skip := true,
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val `pekko-inmem-journal` = project
  .in(file("pekko-inmem-journal"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Pekko.inmemJournalDeps
  )

lazy val `pekko-core` = project
  .in(file("pekko-core"))
  .settings(commonSettings)
  .settings(
    moduleName := "pekko-core",
    libraryDependencies ++= Pekko.deps ++ Prometheus.deps ++ Logging.deps ++ TestTools.deps
  )
  .dependsOn(`pekko-inmem-journal` % Test)

lazy val `pekko-cassandra` = project
  .in(file("pekko-cassandra"))
  .settings(commonSettings)
  .settings(
    moduleName := "sensors-cassandra",
    libraryDependencies ++= Pekko.deps ++ Prometheus.deps ++
            (Cassandra.deps :+ Cassandra.cassandraUnit % Test) ++ Logging.deps ++ TestTools.deps
  )
  .dependsOn(`pekko-core`)

lazy val `app` = project
  .in(file("examples/app"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(commonSettings ++ noPublishSettings)
  .settings(
    moduleName := "app",
    Compile / mainClass := Some("nl.pragmasoft.app.Main"),
    Docker / version := Keys.version.value,
    dockerUpdateLatest := true,
    libraryDependencies ++= App.deps :+ Cassandra.cassandraUnit
  )
  .dependsOn(`pekko-core`, `pekko-cassandra`)

lazy val `root` = project
  .in(file("."))
  .aggregate(app, `pekko-core`, `pekko-cassandra`)
  .settings(commonSettings ++ noPublishSettings)
  .settings(name := "Pekko Sensors")
