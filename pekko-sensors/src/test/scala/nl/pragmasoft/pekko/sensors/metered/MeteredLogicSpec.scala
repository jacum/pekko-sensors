package nl.pragmasoft.pekko.sensors.metered

import com.typesafe.config.ConfigFactory
import io.prometheus.metrics.model.registry.PrometheusRegistry
import nl.pragmasoft.pekko.sensors.DispatcherMetrics
import nl.pragmasoft.pekko.sensors.metered.MeteredLogicSpec._
import org.apache.pekko.actor.BootstrapSetup
import org.apache.pekko.actor.setup.ActorSystemSetup
import org.apache.pekko.actor.typed.{ActorSystem, DispatcherSelector, SpawnProtocol}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._

/**
 * This spec contains checks for metrics gathering implemented in .metered package.
 */
class MeteredLogicSpec extends AnyFreeSpec with Matchers {
  "Metered logic" - {
    "collects metrics for runnables" in {
      val cr      = new PrometheusRegistry()
      val metrics = DispatcherMetrics.make()
      metrics.allCollectors.foreach(cr.register)

      val withConfig  = BootstrapSetup(cfg)
      val withMetrics = MeteredDispatcherSetup(metrics)
      val setup       = ActorSystemSetup.create(withConfig, withMetrics)
      val actorSystem = ActorSystem[SpawnProtocol.Command](SpawnProtocol(), "test-system", setup)

      try {
        // here we get a metered dispatcher from a custom config
        // Avoid using it as the default dispatcher as it is going to be used by Pekko itself.
        // In this case that usage will affect metrics we are testing
        val dispatcher = actorSystem.dispatchers.lookup(DispatcherSelector.fromConfig("our-test-dispatcher"))

        // check that samples in the metrics are not defined before running the test task
        val prevSamples = cr.scrape().iterator().asScala.toList.map(in => (in.getMetadata.getName, in)).toMap
        prevSamples("pekko_sensors_dispatchers_queue_time_millis").getDataPoints shouldBe empty
        prevSamples("pekko_sensors_dispatchers_run_time_millis").getDataPoints shouldBe empty
        prevSamples("pekko_sensors_dispatchers_active_threads").getDataPoints shouldBe empty
        prevSamples("pekko_sensors_dispatchers_thread_states").getDataPoints shouldBe empty
        prevSamples("pekko_sensors_dispatchers_threads").getDataPoints shouldBe empty
        prevSamples("pekko_sensors_dispatchers_executor_value").getDataPoints shouldBe empty

        dispatcher.execute(() => Thread.sleep(3000))

        //Now we can check that these metrics contain some samples after 3 secs of execution
        val samples = cr.scrape().iterator().asScala.toList.map(in => (in.getMetadata.getName, in)).toMap
        samples("pekko_sensors_dispatchers_queue_time_millis").getDataPoints should not be empty
        samples("pekko_sensors_dispatchers_run_time_millis").getDataPoints should not be empty
        samples("pekko_sensors_dispatchers_active_threads").getDataPoints should not be empty
        samples("pekko_sensors_dispatchers_thread_states").getDataPoints shouldBe empty
        samples("pekko_sensors_dispatchers_threads").getDataPoints shouldBe empty
        samples("pekko_sensors_dispatchers_executor_value").getDataPoints shouldBe empty
      } finally actorSystem.terminate()
    }
  }
}

object MeteredLogicSpec {
  private val cfgStr =
    """
      |our-test-dispatcher {
      |  type = "nl.pragmasoft.pekko.sensors.metered.MeteredDispatcherConfigurator"
      |  instrumented-executor {
      |    delegate = "java.util.concurrent.ForkJoinPool"
      |    measure-runs = true
      |    watch-long-runs = false
      |  }
      |}
      |""".stripMargin

  private val cfg = ConfigFactory.parseString(cfgStr)
}
