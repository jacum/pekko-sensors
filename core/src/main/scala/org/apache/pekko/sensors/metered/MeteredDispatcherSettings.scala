package org.apache.pekko.sensors.metered

import org.apache.pekko.dispatch.MessageDispatcherConfigurator

import scala.concurrent.duration._
import org.apache.pekko.dispatch.ExecutorServiceFactoryProvider
import org.apache.pekko.sensors.DispatcherMetrics

private[metered] case class MeteredDispatcherSettings(
  name: String,
  metrics: DispatcherMetrics,
  _configurator: MessageDispatcherConfigurator,
  id: String,
  throughput: Int,
  throughputDeadlineTime: Duration,
  executorServiceFactoryProvider: ExecutorServiceFactoryProvider,
  shutdownTimeout: FiniteDuration
)
