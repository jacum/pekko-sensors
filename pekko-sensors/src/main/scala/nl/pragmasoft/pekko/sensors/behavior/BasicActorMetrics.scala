package nl.pragmasoft.pekko.sensors.behavior

import nl.pragmasoft.pekko.sensors.{ClassNameUtil, PekkoSensorsExtension, SensorMetrics}
import org.apache.pekko.actor.typed._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import nl.pragmasoft.pekko.sensors.MetricOps._

import scala.reflect.ClassTag
import scala.util.control.NonFatal

final case class BasicActorMetrics[C](
  actorLabel: String,
  metrics: SensorMetrics,
  messageLabel: C => Option[String]
) {

  private lazy val exceptions = metrics.exceptions.labelValues(actorLabel)
  private val activeActors    = metrics.activeActors.labelValues(actorLabel)
  private val activityTimer   = metrics.activityTime.labelValues(actorLabel).startTimer()

  def apply(behavior: Behavior[C])(implicit ct: ClassTag[C]): Behavior[C] = {

    val interceptor = () =>
      new BehaviorInterceptor[C, C] {
        override def aroundSignal(
          ctx: TypedActorContext[C],
          signal: Signal,
          target: BehaviorInterceptor.SignalTarget[C]
        ): Behavior[C] = {
          signal match {
            case PostStop =>
              activeActors.dec()
              activityTimer.observeDuration()

            case _ =>
          }

          target(ctx, signal)
        }

        override def aroundStart(
          ctx: TypedActorContext[C],
          target: BehaviorInterceptor.PreStartTarget[C]
        ): Behavior[C] = {
          activeActors.inc()
          target.start(ctx)
        }

        @SuppressWarnings(Array("org.wartremover.warts.Throw"))
        override def aroundReceive(
          ctx: TypedActorContext[C],
          msg: C,
          target: BehaviorInterceptor.ReceiveTarget[C]
        ): Behavior[C] =
          try {
            val next = messageLabel(msg).map {
              metrics.receiveTime
                .labelValues(actorLabel, _)
                .observeExecution(target(ctx, msg))
            }
              .getOrElse(target(ctx, msg))

            if (Behavior.isUnhandled(next))
              messageLabel(msg)
                .foreach(metrics.unhandledMessages.labelValues(actorLabel, _).inc())
            next
          } catch {
            case NonFatal(e) =>
              exceptions.inc()
              throw e
          }
      }

    Behaviors.intercept(interceptor)(behavior)
  }
}
