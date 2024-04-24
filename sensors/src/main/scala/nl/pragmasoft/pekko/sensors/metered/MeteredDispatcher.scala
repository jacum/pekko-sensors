package nl.pragmasoft.pekko.sensors.metered

import nl.pragmasoft.pekko.sensors.DispatcherMetrics
import org.apache.pekko.dispatch.Dispatcher
import org.apache.pekko.sensors.metered.MeteredDispatcherInstrumentation

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
