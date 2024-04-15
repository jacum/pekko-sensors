package org.apache.pekko.sensors.metered

import org.apache.pekko.actor.setup.Setup
import org.apache.pekko.dispatch.DispatcherPrerequisites
import org.apache.pekko.sensors.DispatcherMetrics

final case class MeteredDispatcherSetup(metrics: DispatcherMetrics) extends Setup

object MeteredDispatcherSetup {

  /** Extract LocalDispatcherSetup out from DispatcherPrerequisites or throw an exception */
  def setupOrThrow(prereq: DispatcherPrerequisites): MeteredDispatcherSetup =
    prereq.settings.setup
      .get[MeteredDispatcherSetup]
      .getOrElse(throw SetupNotFound[MeteredDispatcherSetup])
}
