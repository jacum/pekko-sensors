package nl.pragmasoft.pekko.sensors.metered

import nl.pragmasoft.pekko.sensors.DispatcherMetrics
import org.apache.pekko.actor.setup.Setup
import org.apache.pekko.dispatch.DispatcherPrerequisites

final case class MeteredDispatcherSetup(metrics: DispatcherMetrics) extends Setup

object MeteredDispatcherSetup {

  /** Extract LocalDispatcherSetup out from DispatcherPrerequisites or throw an exception */
  def setupOrThrow(prereq: DispatcherPrerequisites): MeteredDispatcherSetup =
    prereq.settings.setup
      .get[MeteredDispatcherSetup]
      .getOrElse(throw SetupNotFound[MeteredDispatcherSetup])
}
