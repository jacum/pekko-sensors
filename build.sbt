import Dependencies._
import Keys._
import sbt.file

lazy val scala2 = "2.13.14"
lazy val scala3 = "3.3.3"

val commonSettings = Defaults.coreDefaultSettings ++ Seq(
        organization := "nl.pragmasoft.pekko",
        scalaVersion := scala2,
        crossScalaVersions := Seq(scala2, scala3),
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

lazy val `inmem-journal` = project
  .in(file("inmem-journal"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Pekko.inmemJournalDeps
  )

lazy val sensors = project
  .in(file("sensors"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Pekko.deps ++ Prometheus.deps ++ Logging.deps ++ TestTools.deps
  )
  .dependsOn(`inmem-journal` % Test)

lazy val `app` = project
  .in(file("examples/app"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(commonSettings ++ noPublishSettings)
  .settings(
    dockerBaseImage := "openjdk",
    Compile / mainClass := Some("nl.pragmasoft.app.Main"),
    Docker / version := Keys.version.value,
    dockerUpdateLatest := true,
    libraryDependencies ++= App.deps
  )
  .dependsOn(sensors, `inmem-journal`)

lazy val `root` = project
  .in(file("."))
  .aggregate(app, sensors, `inmem-journal`)
  .settings(commonSettings ++ noPublishSettings)
  .settings(name := "Pekko Sensors")
