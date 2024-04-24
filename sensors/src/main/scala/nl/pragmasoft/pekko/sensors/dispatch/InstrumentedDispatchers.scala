package nl.pragmasoft.pekko.sensors.dispatch

import java.util.concurrent._
import java.util.concurrent.atomic.LongAdder
import org.apache.pekko.dispatch._
import org.apache.pekko.event.Logging.Warning
import org.apache.pekko.sensors.dispatch.{InstrumentedDispatcherBase, PekkoRunnableWrapper, ScalaRunnableWrapper}
import DispatcherInstrumentationWrapper.Run
import com.typesafe.config.Config
import nl.pragmasoft.pekko.sensors.PekkoSensors
import nl.pragmasoft.pekko.sensors.RunnableWatcher

import scala.concurrent.duration.Duration

class DispatcherInstrumentationWrapper(config: Config) {
  import DispatcherInstrumentationWrapper._
  import nl.pragmasoft.pekko.sensors.dispatch.Helpers._

  private val executorConfig = config.getConfig("instrumented-executor")

  private val instruments: List[InstrumentedRun] =
    List(
      if (executorConfig.getBoolean("measure-runs")) Some(meteredRun(config.getString("id"))) else None,
      if (executorConfig.getBoolean("watch-long-runs"))
        Some(watchedRun(config.getString("id"), executorConfig.getMillisDuration("watch-too-long-run"), executorConfig.getMillisDuration("watch-check-interval")))
      else None
    ) flatten

  def apply(runnable: Runnable, execute: Runnable => Unit): Unit = {
    val beforeRuns = for { f <- instruments } yield f()
    val run = new Run {
      def apply[T](run: () => T): T = {
        val afterRuns = for { f <- beforeRuns } yield f()
        try run()
        finally for { f <- afterRuns } f()
      }
    }
    execute(RunnableWrapper(runnable, run))
  }
}

object RunnableWrapper {
  def apply(runnableParam: Runnable, r: Run): Runnable =
    runnableParam match {
      case PekkoRunnableWrapper(runnable) => runnable.apply(r)
      case ScalaRunnableWrapper(runnable) => runnable.apply(r)
      case runnable                       => new Default(runnable, r)
    }

  private class Default(self: Runnable, r: Run) extends Runnable {
    def run(): Unit = r(() => self.run())
  }
}

object DispatcherInstrumentationWrapper {
  trait Run { def apply[T](f: () => T): T }

  type InstrumentedRun = () => BeforeRun
  type BeforeRun       = () => AfterRun
  type AfterRun        = () => Unit

  val Empty: InstrumentedRun = () => () => () => ()

  import DispatcherMetricsRegistration._
  def meteredRun(id: String): InstrumentedRun = {
    val currentWorkers = new LongAdder
    val queue          = queueTime.labels(id)
    val run            = runTime.labels(id)
    val active         = activeThreads.labels(id)

    () => {
      val created = System.currentTimeMillis()
      () => {
        val started = System.currentTimeMillis()
        queue.observe((started - created).toDouble)
        currentWorkers.increment()
        active.observe(currentWorkers.intValue())
        () => {
          val stopped = System.currentTimeMillis()
          run.observe((stopped - started).toDouble)
          currentWorkers.decrement()
          active.observe(currentWorkers.intValue)
          ()
        }
      }
    }
  }

  def watchedRun(id: String, tooLongThreshold: Duration, checkInterval: Duration): InstrumentedRun = {
    val watcher = RunnableWatcher(tooLongRunThreshold = tooLongThreshold, checkInterval = checkInterval)

    () => { () =>
      val stop = watcher.start()
      () => {
        stop()
        ()
      }
    }
  }
}

class InstrumentedExecutor(val config: Config, val prerequisites: DispatcherPrerequisites) extends ExecutorServiceConfigurator(config, prerequisites) {

  lazy val delegate: ExecutorServiceConfigurator =
    serviceConfigurator(config.getString("instrumented-executor.delegate"))

  override def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory = {
    val esf = delegate.createExecutorServiceFactory(id, threadFactory)
    import DispatcherMetricsRegistration._
    new ExecutorServiceFactory {
      def createExecutorService: ExecutorService = {
        val es = esf.createExecutorService

        lazy val activeCount       = executorValue.labels(id, "activeCount")
        lazy val corePoolSize      = executorValue.labels(id, "corePoolSize")
        lazy val largestPoolSize   = executorValue.labels(id, "largestPoolSize")
        lazy val maximumPoolSize   = executorValue.labels(id, "maximumPoolSize")
        lazy val queueSize         = executorValue.labels(id, "queueSize")
        lazy val completedTasks    = executorValue.labels(id, "completedTasks")
        lazy val poolSize          = executorValue.labels(id, "poolSize")
        lazy val steals            = executorValue.labels(id, "steals")
        lazy val parallelism       = executorValue.labels(id, "parallelism")
        lazy val queuedSubmissions = executorValue.labels(id, "queuedSubmissions")
        lazy val queuedTasks       = executorValue.labels(id, "queuedTasks")
        lazy val runningThreads    = executorValue.labels(id, "runningThreads")

        es match {
          case tp: ThreadPoolExecutor =>
            PekkoSensors.schedule(
              id,
              () => {
                activeCount.set(tp.getActiveCount)
                corePoolSize.set(tp.getCorePoolSize)
                largestPoolSize.set(tp.getLargestPoolSize)
                maximumPoolSize.set(tp.getMaximumPoolSize)
                queueSize.set(tp.getQueue.size())
                completedTasks.set(tp.getCompletedTaskCount.toDouble)
                poolSize.set(tp.getPoolSize)
              }
            )

          case fj: ForkJoinPool =>
            PekkoSensors.schedule(
              id,
              () => {
                poolSize.set(fj.getPoolSize)
                steals.set(fj.getStealCount.toDouble)
                parallelism.set(fj.getParallelism)
                activeCount.set(fj.getActiveThreadCount)
                queuedSubmissions.set(fj.getQueuedSubmissionCount)
                queuedTasks.set(fj.getQueuedTaskCount.toDouble)
                runningThreads.set(fj.getRunningThreadCount)
              }
            )

          case _ =>

        }

        es
      }
    }
  }

  def serviceConfigurator(executor: String): ExecutorServiceConfigurator =
    executor match {
      case null | "" | "fork-join-executor" => new ForkJoinExecutorConfigurator(config.getConfig("fork-join-executor"), prerequisites)
      case "thread-pool-executor"           => new ThreadPoolExecutorConfigurator(config.getConfig("thread-pool-executor"), prerequisites)
      case fqcn =>
        val args = List(classOf[Config] -> config, classOf[DispatcherPrerequisites] -> prerequisites)
        prerequisites.dynamicAccess
          .createInstanceFor[ExecutorServiceConfigurator](fqcn, args)
          .recover({
            case exception =>
              throw new IllegalArgumentException(
                """Cannot instantiate ExecutorServiceConfigurator ("executor = [%s]"), defined in [%s],
                make sure it has an accessible constructor with a [%s,%s] signature"""
                  .format(fqcn, config.getString("id"), classOf[Config], classOf[DispatcherPrerequisites]),
                exception
              )
          })
          .get
    }

}

trait InstrumentedDispatcher extends InstrumentedDispatcherBase

class InstrumentedDispatcherConfigurator(config: Config, prerequisites: DispatcherPrerequisites) extends MessageDispatcherConfigurator(config, prerequisites) {

  import nl.pragmasoft.pekko.sensors.dispatch.Helpers._

  private val instance = new Dispatcher(
    this,
    config.getString("id"),
    config.getInt("throughput"),
    config.getNanosDuration("throughput-deadline-time"),
    configureExecutor(),
    config.getMillisDuration("shutdown-timeout")
  ) with InstrumentedDispatcher {
    def actorSystemName: String = prerequisites.mailboxes.settings.name
  }

  def dispatcher(): MessageDispatcher = instance

}

class InstrumentedPinnedDispatcherConfigurator(config: Config, prerequisites: DispatcherPrerequisites) extends MessageDispatcherConfigurator(config, prerequisites) {
  import nl.pragmasoft.pekko.sensors.dispatch.Helpers._

  private val threadPoolConfig: ThreadPoolConfig = configureExecutor() match {
    case e: ThreadPoolExecutorConfigurator => e.threadPoolConfig
    case _ =>
      prerequisites.eventStream.publish(
        Warning(
          "PinnedDispatcherConfigurator",
          this.getClass,
          "PinnedDispatcher [%s] not configured to use ThreadPoolExecutor, falling back to default config.".format(config.getString("id"))
        )
      )
      ThreadPoolConfig()
  }

  override def dispatcher(): MessageDispatcher =
    new PinnedDispatcher(this, null, config.getString("id"), config.getMillisDuration("shutdown-timeout"), threadPoolConfig) with InstrumentedDispatcher {
      def actorSystemName: String = prerequisites.mailboxes.settings.name
    }

}
