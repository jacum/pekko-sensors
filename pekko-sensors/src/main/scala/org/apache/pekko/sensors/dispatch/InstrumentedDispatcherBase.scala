package org.apache.pekko.sensors.dispatch

import nl.pragmasoft.pekko.sensors.PekkoSensors
import nl.pragmasoft.pekko.sensors.dispatch.{DispatcherInstrumentationWrapper, DispatcherMetricsRegistration}
import org.apache.pekko.dispatch.{Dispatcher, Mailbox}
import org.apache.pekko.event.Logging.Error

import java.lang.management.{ManagementFactory, ThreadMXBean}
import java.util.concurrent.RejectedExecutionException

trait InstrumentedDispatcherBase extends Dispatcher {

  def actorSystemName: String
  private lazy val wrapper = new DispatcherInstrumentationWrapper(configurator.config)

  private val threadMXBean: ThreadMXBean = ManagementFactory.getThreadMXBean
  private val interestingStateNames      = Set("runnable", "waiting", "timed_waiting", "blocked")
  private val interestingStates          = Thread.State.values.filter(s => interestingStateNames.contains(s.name().toLowerCase))

  PekkoSensors.schedule(
    s"$id-states",
    () => {
      val threads = threadMXBean
        .getThreadInfo(threadMXBean.getAllThreadIds, 0)
        .filter(t =>
          t != null
            && t.getThreadName.startsWith(s"$actorSystemName-$id")
        )

      interestingStates foreach { state =>
        val stateLabel = state.toString.toLowerCase
        DispatcherMetricsRegistration.threadStates
          .labelValues(id, stateLabel)
          .set(threads.count(_.getThreadState.name().equalsIgnoreCase(stateLabel)))
      }
      DispatcherMetricsRegistration.threads
        .labelValues(id)
        .set(threads.length)
    }
  )

  override def execute(runnable: Runnable): Unit = wrapper(runnable, super.execute)

  protected[pekko] override def registerForExecution(mbox: Mailbox, hasMessageHint: Boolean, hasSystemMessageHint: Boolean): Boolean =
    if (mbox.canBeScheduledForExecution(hasMessageHint, hasSystemMessageHint))
      if (mbox.setAsScheduled())
        try {
          wrapper(mbox, executorService.execute)
          true
        } catch {
          case _: RejectedExecutionException =>
            try {
              wrapper(mbox, executorService.execute)
              true
            } catch { //Retry once
              case e: RejectedExecutionException =>
                mbox.setAsIdle()
                eventStream.publish(Error(e, getClass.getName, getClass, "registerForExecution was rejected twice!"))
                throw e
            }
        }
      else false
    else false
}
