package org.apache.pekko.sensors.dispatch

import nl.pragmasoft.pekko.sensors.dispatch.DispatcherInstrumentationWrapper.Run
import org.apache.pekko.dispatch.{Batchable, Mailbox}

import java.util.concurrent.ForkJoinTask
import scala.PartialFunction.condOpt

object PekkoRunnableWrapper {
  def unapply(runnable: Runnable): Option[Run => Runnable] =
    condOpt(runnable) {
      case runnable: Batchable => new BatchableWrapper(runnable, _)
      case runnable: Mailbox   => new MailboxWrapper(runnable, _)
    }

  class BatchableWrapper(self: Batchable, r: Run) extends Batchable {
    def run(): Unit          = r(() => self.run())
    def isBatchable: Boolean = self.isBatchable
  }

  class MailboxWrapper(self: Mailbox, r: Run) extends ForkJoinTask[Unit] with Runnable {
    def getRawResult: Unit          = self.getRawResult()
    def setRawResult(v: Unit): Unit = self.setRawResult(v)
    def exec(): Boolean             = r(() => self.exec())
    def run(): Unit = { exec(); () }
  }
}
