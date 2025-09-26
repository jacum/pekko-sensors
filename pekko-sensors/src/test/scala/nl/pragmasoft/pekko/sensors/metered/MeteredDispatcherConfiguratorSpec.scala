package nl.pragmasoft.pekko.sensors.metered

import org.apache.pekko.ConfigurationException
import org.apache.pekko.actor.BootstrapSetup
import org.apache.pekko.actor.setup.ActorSystemSetup
import org.apache.pekko.actor.typed.{ActorSystem, DispatcherSelector, SpawnProtocol}
import MeteredDispatcherConfiguratorSpec._
import com.typesafe.config.ConfigFactory
import nl.pragmasoft.pekko.sensors.DispatcherMetrics
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class MeteredDispatcherConfiguratorSpec extends AnyFreeSpec with Matchers {
  "MeteredDispatcherConfigurator" - {
    "is returned if configured(MeteredDispatcherSetup is defined)" in {
      val metrics     = DispatcherMetrics.make()
      val withConfig  = BootstrapSetup(cfg)
      val withMetrics = MeteredDispatcherSetup(metrics)
      val setup       = ActorSystemSetup.create(withConfig, withMetrics)
      val actorSystem = ActorSystem[SpawnProtocol.Command](SpawnProtocol(), "test-system", setup)
      val dispatcher  = actorSystem.dispatchers.lookup(DispatcherSelector.defaultDispatcher())

      try dispatcher shouldBe a[MeteredDispatcher]
      finally actorSystem.terminate()
    }

    "throws SetupNotFound if MeteredDispatcherSetup is not defined" in {
      val withConfig  = BootstrapSetup(cfg)
      val setup       = ActorSystemSetup.create(withConfig)
      def actorSystem = ActorSystem[SpawnProtocol.Command](SpawnProtocol(), "test-system", setup)

      val exception = the[ConfigurationException] thrownBy actorSystem
      exception.getCause shouldBe a[SetupNotFound]
    }
  }
}

object MeteredDispatcherConfiguratorSpec {
  private val cfgStr =
    """
      |pekko.actor.default-dispatcher {
      |  type = "nl.pragmasoft.pekko.sensors.metered.MeteredDispatcherConfigurator"
      |  instrumented-executor {
      |    delegate = "java.util.concurrent.ForkJoinPool"
      |    measure-runs = false
      |    watch-long-runs = false
      |  }
      |}
      |""".stripMargin

  private val cfg = ConfigFactory.parseString(cfgStr)
}
