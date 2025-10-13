import Dependencies._
import Keys._
import sbt.file

lazy val scala2 = "2.13.17"
lazy val scala3 = "3.3.6"

val commonSettings = Defaults.coreDefaultSettings ++ Seq(
        organization := "nl.pragmasoft",
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

lazy val `pekko-inmem-journal` = project
  .in(file("pekko-inmem-journal"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Pekko.inmemJournalDeps
  )

lazy val `pekko-sensors` = project
  .in(file("pekko-sensors"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Pekko.deps ++ Prometheus.deps ++ Logging.deps ++ TestTools.deps
  )
  .dependsOn(`pekko-inmem-journal` % Test)

lazy val `root` = project
  .in(file("."))
  .aggregate(`pekko-sensors`, `pekko-inmem-journal`)
  .settings(commonSettings ++ noPublishSettings)
  .settings(name := "Pekko Sensors")
