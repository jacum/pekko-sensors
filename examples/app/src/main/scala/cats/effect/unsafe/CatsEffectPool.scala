package cats.effect.unsafe

import com.typesafe.config.Config
import org.apache.pekko.dispatch.{DispatcherPrerequisites, ExecutorServiceConfigurator, ExecutorServiceFactory}

import java.util.Collections
import java.util.concurrent.{AbstractExecutorService, ExecutorService, ThreadFactory, TimeUnit}
import scala.concurrent.ExecutionContextExecutorService

class CatsEffectPool(config: Config, prerequisites: DispatcherPrerequisites) extends ExecutorServiceConfigurator(config, prerequisites) {

  protected lazy val ioRuntime: IORuntime = IORuntime.global

  override def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory =
    new ExecutorServiceFactory {
      override def createExecutorService: ExecutorService =
        new AbstractExecutorService with ExecutionContextExecutorService {
          private var terminatedState                        = false
          private var shutdownState                          = false
          override def execute(runnable: Runnable): Unit     = ioRuntime.compute execute runnable
          override def reportFailure(cause: Throwable): Unit = ioRuntime.compute reportFailure cause
          override def shutdown(): Unit = {
            terminatedState = true
            ioRuntime.shutdown.apply()
            shutdownState = true
          }
          override def shutdownNow(): java.util.List[Runnable]                  = Collections.singletonList(() => this.shutdown())
          override def isShutdown: Boolean                                      = shutdownState
          override def isTerminated: Boolean                                    = terminatedState
          override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = false
        }
    }

}
