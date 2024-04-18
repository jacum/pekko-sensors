package nl.pragmasoft.pekko.sensors.behavior

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import io.prometheus.client.CollectorRegistry
import nl.pragmasoft.pekko.sensors.{ClassNameUtil, PekkoSensorsExtension, SensorMetrics}
import org.apache.pekko.persistence.sensors.EventSourcedMetrics

import scala.reflect.ClassTag

object BehaviorMetrics {

  private type CreateBehaviorMetrics[C] = (SensorMetrics, Behavior[C]) => Behavior[C]
  private val defaultMessageLabel: Any => Option[String] = msg => Some(ClassNameUtil.simpleName(msg.getClass))

  def apply[C: ClassTag](actorLabel: String, getLabel: C => Option[String] = defaultMessageLabel): BehaviorMetricsBuilder[C] = {
    val defaultMetrics = (metrics: SensorMetrics, behavior: Behavior[C]) => BasicActorMetrics[C](actorLabel, metrics, getLabel)(behavior)
    new BehaviorMetricsBuilder(actorLabel, defaultMetrics :: Nil)
  }

  class BehaviorMetricsBuilder[C: ClassTag](
    actorLabel: String,
    createMetrics: List[CreateBehaviorMetrics[C]]
  ) { self =>

    def setup(factory: ActorContext[C] => Behavior[C]): Behavior[C] =
      Behaviors.setup { actorContext =>
        val metrics = PekkoSensorsExtension(actorContext.asScala.system).metrics
        setupWithMetrics(metrics)(factory)
      }

    def setupWithMetrics(metrics: SensorMetrics)(factory: ActorContext[C] => Behavior[C]): Behavior[C] =
      Behaviors.setup { actorContext =>
        val behavior = factory(actorContext)
        createMetrics.foldLeft(behavior)((b, createMetrics) => createMetrics(metrics, b))
      }

    def withReceiveTimeoutMetrics(timeoutCmd: C): BehaviorMetricsBuilder[C] = {
      val receiveTimeoutMetrics = (metrics: SensorMetrics, behavior: Behavior[C]) => ReceiveTimeoutMetrics[C](actorLabel, metrics, timeoutCmd)(behavior)
      new BehaviorMetricsBuilder[C](self.actorLabel, receiveTimeoutMetrics :: self.createMetrics)
    }

    def withPersistenceMetrics: BehaviorMetricsBuilder[C] = {
      val eventSourcedMetrics = (metrics: SensorMetrics, behaviorToObserve: Behavior[C]) => EventSourcedMetrics(actorLabel, metrics).apply(behaviorToObserve)
      new BehaviorMetricsBuilder[C](actorLabel, eventSourcedMetrics :: self.createMetrics)
    }
  }
}
