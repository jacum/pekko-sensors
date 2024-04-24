package nl.pragmasoft.pekko.sensors.metered

import com.typesafe.config.Config
import org.apache.pekko.dispatch.{DispatcherPrerequisites, MessageDispatcher, MessageDispatcherConfigurator}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

private object MeteredDispatcherConfigurator {
  final implicit class ConfigOps(val config: Config) extends AnyVal {
    def getMillisDuration(path: String): FiniteDuration = getDuration(path, TimeUnit.MILLISECONDS)

    def getNanosDuration(path: String): FiniteDuration = getDuration(path, TimeUnit.NANOSECONDS)

    private def getDuration(path: String, unit: TimeUnit): FiniteDuration =
      Duration(config.getDuration(path, unit), unit)
  }
}

class MeteredDispatcherConfigurator(config: Config, prerequisites: DispatcherPrerequisites) extends MessageDispatcherConfigurator(config, prerequisites) {
  import MeteredDispatcherConfigurator.ConfigOps

  private val instance: MessageDispatcher = {
    val _metrics = MeteredDispatcherSetup.setupOrThrow(prerequisites).metrics
    val settings = MeteredDispatcherSettings(
      name = prerequisites.mailboxes.settings.name,
      metrics = _metrics,
      _configurator = this,
      id = config.getString("id"),
      throughput = config.getInt("throughput"),
      throughputDeadlineTime = config.getNanosDuration("throughput-deadline-time"),
      executorServiceFactoryProvider = configureExecutor(),
      shutdownTimeout = config.getMillisDuration("shutdown-timeout")
    )

    new MeteredDispatcher(settings)
  }

  def dispatcher(): MessageDispatcher = instance
}
