package org.apache.pekko.sensors.behavior

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{Behavior, BehaviorInterceptor, TypedActorContext}
import org.apache.pekko.sensors.{PekkoSensorsExtension, SensorMetrics}

import scala.reflect.ClassTag

final case class ReceiveTimeoutMetrics[C](
  actorLabel: String,
  metrics: SensorMetrics,
  timeoutCmd: C
) {

  private val receiveTimeouts = metrics.receiveTimeouts.labels(actorLabel)

  def apply(behavior: Behavior[C])(implicit ct: ClassTag[C]): Behavior[C] = {

    val interceptor = () =>
      new BehaviorInterceptor[C, C] {
        @SuppressWarnings(Array("org.wartremover.warts.Equals"))
        def aroundReceive(
          ctx: TypedActorContext[C],
          msg: C,
          target: BehaviorInterceptor.ReceiveTarget[C]
        ): Behavior[C] = {
          if (msg == timeoutCmd) receiveTimeouts.inc()
          target(ctx, msg)
        }
      }

    Behaviors.intercept(interceptor)(behavior)
  }
}
