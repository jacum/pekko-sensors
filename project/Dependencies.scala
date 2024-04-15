import sbt._

//noinspection TypeAnnotation
object Dependencies {

  object Logging {
    val slf4jversion = "2.0.7"
    val slf4jApi     = "org.slf4j"                   % "slf4j-api"     % slf4jversion
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
    val deps         = Seq(slf4jApi, scalaLogging)
  }

  object Pekko {
    val pekkoVersion = "1.0.3-M1"

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
    val scalaz = "org.scalaz"       %% "scalaz-core"  % "7.3.7"

    val persistenceTck = "org.apache.pekko" %% "pekko-persistence-tck" % pekkoVersion % Test
    val streamTestKit  = "org.apache.pekko" %% "pekko-stream-testkit"  % pekkoVersion % Test
    val testkit        = "org.apache.pekko" %% "pekko-testkit"         % pekkoVersion % Test

    val deps             = Seq(actor, typed, persistence, persistenceTyped, persistenceQuery, cluster, clusterTyped, clusterTools, slf4j) ++ Logging.deps
    val inmemJournalDeps = deps ++ Seq(stream, scalaz, persistenceTck, streamTestKit, testkit)
  }

  object Prometheus {
    val hotspot   = "io.prometheus"     % "simpleclient_hotspot" % "0.16.0"
    val common    = "io.prometheus"     % "simpleclient_common"  % "0.16.0"
    val jmx       = "io.prometheus.jmx" % "collector"            % "0.17.1" exclude ("org.yaml", "snakeyaml")
    val snakeYaml = "org.yaml"          % "snakeyaml"            % "2.0"

    val deps = Seq(hotspot, common, jmx, snakeYaml)
  }

  object Http4s {
    // unfortunately, http4s modules' versions not synced anymore
    val http4sVersionBase    = "0.23.15"
    val http4sVersionModules = "0.23.26"
    val http4sVersionMetrics = "0.24.4"
    val server               = "org.http4s"       %% "http4s-blaze-server"       % http4sVersionBase
    val client               = "org.http4s"       %% "http4s-blaze-client"       % http4sVersionBase
    val jdkClient            = "org.http4s"       %% "http4s-jdk-http-client"    % "0.7.0"
    val circe                = "org.http4s"       %% "http4s-circe"              % http4sVersionModules
    val dsl                  = "org.http4s"       %% "http4s-dsl"                % http4sVersionModules
    val metrics              = "org.http4s"       %% "http4s-prometheus-metrics" % http4sVersionMetrics
    val prometheusJmx        = "io.prometheus.jmx" % "collector"                 % "0.19.0"
    val deps: Seq[ModuleID]  = Seq(server, client, circe, dsl, metrics, prometheusJmx)
  }

  object App {

    val deps = Seq(
        Cassandra.cassandraUnit
      ) ++ Http4s.deps ++ Pekko.deps ++ Cassandra.deps ++ Logging.deps
  }

  object Cassandra {
    val pekkoPersistenceCassandraVersion = "1.0.0"
    val cassandraDriverVersion           = "4.17.0"

    val cassandraDriverCore         = "com.datastax.oss"      % "java-driver-core"            % cassandraDriverVersion
    val cassandraDriverQueryBuilder = "com.datastax.oss"      % "java-driver-query-builder"   % cassandraDriverVersion
    val cassandraDriverMetrics      = "io.dropwizard.metrics" % "metrics-jmx"                 % "4.2.19"
    val pekkoPersistenceCassandra   = "org.apache.pekko"     %% "pekko-persistence-cassandra" % pekkoPersistenceCassandraVersion
    val cassandraUnit               = "org.cassandraunit"     % "cassandra-unit"              % "4.3.1.0"

    val deps = Seq(pekkoPersistenceCassandra, cassandraDriverCore, cassandraDriverQueryBuilder, cassandraDriverMetrics)
  }

  object TestTools {
    val log       = "ch.qos.logback" % "logback-classic" % "1.4.8"
    val scalaTest = "org.scalatest" %% "scalatest"       % "3.2.16"
    val deps      = Logging.deps ++ testDeps(scalaTest, log)
  }

  def scopeDeps(scope: String, modules: Seq[ModuleID]) = modules.map(m => m % scope)
  def testDeps(modules: ModuleID*)                     = scopeDeps("test", modules)

}
