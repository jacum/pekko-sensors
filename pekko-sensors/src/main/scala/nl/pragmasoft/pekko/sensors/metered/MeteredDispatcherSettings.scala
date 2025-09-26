package nl.pragmasoft.pekko.sensors.metered

import nl.pragmasoft.pekko.sensors.DispatcherMetrics
import org.apache.pekko.dispatch.{ExecutorServiceFactoryProvider, MessageDispatcherConfigurator}

import scala.concurrent.duration._

case class MeteredDispatcherSettings(
  name: String,
  metrics: DispatcherMetrics,
  _configurator: MessageDispatcherConfigurator,
  id: String,
  throughput: Int,
  throughputDeadlineTime: Duration,
  executorServiceFactoryProvider: ExecutorServiceFactoryProvider,
  shutdownTimeout: FiniteDuration
)
