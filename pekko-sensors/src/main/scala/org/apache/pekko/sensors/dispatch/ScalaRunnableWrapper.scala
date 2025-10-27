package org.apache.pekko.sensors.dispatch

import org.apache.pekko.dispatch.Batchable
import nl.pragmasoft.pekko.sensors.dispatch.DispatcherInstrumentationWrapper.Run

import scala.PartialFunction.condOpt

object ScalaRunnableWrapper {
  def unapply(runnable: Runnable): Option[Run => Runnable] =
    condOpt(runnable) { case runnable: Batchable =>
      new OverrideBatchable(runnable, _)
    }

  class OverrideBatchable(self: Runnable, r: Run) extends Batchable with Runnable {
    def run(): Unit          = r(() => self.run())
    def isBatchable: Boolean = true
  }
}
