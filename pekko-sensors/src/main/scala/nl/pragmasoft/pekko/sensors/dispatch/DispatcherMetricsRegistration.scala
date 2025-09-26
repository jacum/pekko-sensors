package nl.pragmasoft.pekko.sensors.dispatch

import io.prometheus.metrics.core.metrics.{Gauge, Histogram}
import io.prometheus.metrics.model.registry.PrometheusRegistry
import nl.pragmasoft.pekko.sensors.{DispatcherMetrics, PekkoSensors}

/** Creates and registers Dispatcher metrics in the global registry */
object DispatcherMetricsRegistration {
  val registry: PrometheusRegistry = PekkoSensors.prometheusRegistry

  private val metrics          = DispatcherMetrics.makeAndRegister(registry)
  def queueTime: Histogram     = metrics.queueTime
  def runTime: Histogram       = metrics.runTime
  def activeThreads: Histogram = metrics.activeThreads
  def threadStates: Gauge      = metrics.threadStates
  def threads: Gauge           = metrics.threads
  def executorValue: Gauge     = metrics.executorValue
}
