package nl.pragmasoft.pekko.sensors.dispatch

import io.prometheus.client.{Gauge, Histogram}
import nl.pragmasoft.pekko.sensors.{DispatcherMetrics, MetricsBuilders}

/** Creates and registers Dispatcher metrics in the global registry */
object DispatcherMetricsRegistration extends MetricsBuilders {
  def namespace: String = "pekko_sensors"
  def subsystem: String = "dispatchers"

  private val metrics          = DispatcherMetrics.makeAndRegister(this, registry)
  def queueTime: Histogram     = metrics.queueTime
  def runTime: Histogram       = metrics.runTime
  def activeThreads: Histogram = metrics.activeThreads
  def threadStates: Gauge      = metrics.threadStates
  def threads: Gauge           = metrics.threads
  def executorValue: Gauge     = metrics.executorValue
}
