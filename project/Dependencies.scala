import sbt._

object Dependencies {

  object Logging {
    val slf4jversion = "2.0.17"
    val slf4jApi     = "org.slf4j"                   % "slf4j-api"       % slf4jversion
    val logback      = "ch.qos.logback"              % "logback-classic" % "1.5.23"
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.6"
    val deps         = Seq(slf4jApi, scalaLogging, logback)
  }

  object Pekko {
    val pekkoVersion = "1.2.1"

    val actor            = "org.apache.pekko" %% "pekko-actor"             % pekkoVersion
    val typed            = "org.apache.pekko" %% "pekko-actor-typed"       % pekkoVersion
    val persistence      = "org.apache.pekko" %% "pekko-persistence"       % pekkoVersion
    val persistenceTyped = "org.apache.pekko" %% "pekko-persistence-typed" % pekkoVersion
    val persistenceQuery = "org.apache.pekko" %% "pekko-persistence-query" % pekkoVersion

    val cluster      = "org.apache.pekko" %% "pekko-cluster"       % pekkoVersion
    val clusterTyped = "org.apache.pekko" %% "pekko-cluster-typed" % pekkoVersion
    val clusterTools = "org.apache.pekko" %% "pekko-cluster-tools" % pekkoVersion
    val slf4j        = "org.apache.pekko" %% "pekko-slf4j"         % pekkoVersion

    val stream = "org.apache.pekko" %% "pekko-stream" % pekkoVersion
    val scalaz = "org.scalaz"       %% "scalaz-core"  % "7.3.8"

    val persistenceTck = "org.apache.pekko" %% "pekko-persistence-tck" % pekkoVersion % Test
    val streamTestKit  = "org.apache.pekko" %% "pekko-stream-testkit"  % pekkoVersion % Test
    val testkit        = "org.apache.pekko" %% "pekko-testkit"         % pekkoVersion % Test

    val deps             = Seq(actor, typed, persistence, persistenceTyped, persistenceQuery, cluster, clusterTyped, clusterTools, slf4j) ++ Logging.deps
    val inmemJournalDeps = deps ++ Seq(stream, scalaz, persistenceTck, streamTestKit, testkit)
  }

  object Prometheus {
    val PrometheusVersion = "1.4.3"
    val hotspot        = "io.prometheus" % "prometheus-metrics-instrumentation-jvm"    % PrometheusVersion
    val common         = "io.prometheus" % "prometheus-metrics-core"                   % PrometheusVersion
    val exposition     = "io.prometheus" % "prometheus-metrics-exposition-textformats" % PrometheusVersion
    val exporterCommon = "io.prometheus" % "prometheus-metrics-exporter-common"        % PrometheusVersion

    val jmx       = "io.prometheus.jmx" % "collector" % "1.5.0"
    val snakeYaml = "org.yaml"          % "snakeyaml" % "2.5"

    val deps = Seq(hotspot, common, exporterCommon, jmx, exposition, snakeYaml)
  }

  object TestTools {
    val log       = "ch.qos.logback"                           % "logback-classic" % "1.5.20"
    val scalaTest = "org.scalatest"                           %% "scalatest"       % "3.2.19"
    val deps      = Logging.deps ++ Seq(scalaTest, log) map (_ % Test)
  }
}
