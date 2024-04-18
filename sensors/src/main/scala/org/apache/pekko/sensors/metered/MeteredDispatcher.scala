package org.apache.pekko.sensors.metered

import org.apache.pekko.dispatch.Dispatcher
import org.apache.pekko.sensors.DispatcherMetrics

class MeteredDispatcher(settings: MeteredDispatcherSettings)
    extends Dispatcher(
      settings._configurator,
      settings.id,
      settings.throughput,
      settings.throughputDeadlineTime,
      executorServiceFactoryProvider = settings.executorServiceFactoryProvider,
      shutdownTimeout = settings.shutdownTimeout
    )
    with MeteredDispatcherInstrumentation {
  protected override val actorSystemName: String    = settings.name
  protected override val metrics: DispatcherMetrics = settings.metrics
}
