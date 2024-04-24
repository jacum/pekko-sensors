package org.apache.pekko.sensors.actor

import nl.pragmasoft.pekko.sensors.{ClassNameUtil, PekkoSensorsExtension}
import org.apache.pekko.actor.{Actor, ActorLogging, ReceiveTimeout}
import org.apache.pekko.persistence.PersistentActor
import nl.pragmasoft.pekko.sensors.MetricOps._

import scala.collection.immutable
import scala.util.control.NonFatal

trait ActorMetrics extends Actor with ActorLogging {
  self: Actor =>

  protected def actorLabel: String = ClassNameUtil.simpleName(this.getClass)

  protected def messageLabel(value: Any): Option[String] = Some(ClassNameUtil.simpleName(value.getClass))

  protected val metrics       = PekkoSensorsExtension(this.context.system).metrics
  private val receiveTimeouts = metrics.receiveTimeouts.labels(actorLabel)
  private lazy val exceptions = metrics.exceptions.labels(actorLabel)
  private val activeActors    = metrics.activeActors.labels(actorLabel)

  private val activityTimer = metrics.activityTime.labels(actorLabel).startTimer()

  protected[pekko] override def aroundReceive(receive: Receive, msg: Any): Unit =
    internalAroundReceive(receive, msg)

  protected def internalAroundReceive(receive: Receive, msg: Any): Unit = {
    msg match {
      case ReceiveTimeout =>
        receiveTimeouts.inc()
      case _ =>
    }
    try messageLabel(msg)
      .map(
        metrics.receiveTime
          .labels(actorLabel, _)
          .observeExecution(super.aroundReceive(receive, msg))
      )
      .getOrElse(super.aroundReceive(receive, msg))
    catch {
      case NonFatal(e) =>
        exceptions.inc()
        throw e
    }
  }
  protected[pekko] override def aroundPreStart(): Unit = {
    super.aroundPreStart()
    activeActors.inc()
  }

  protected[pekko] override def aroundPostStop(): Unit = {
    activeActors.dec()
    activityTimer.observeDuration()
    super.aroundPostStop()
  }

  override def unhandled(message: Any): Unit = {
    messageLabel(message)
      .foreach(metrics.unhandledMessages.labels(actorLabel, _).inc())
    super.unhandled(message)
  }

}

trait PersistentActorMetrics extends ActorMetrics with PersistentActor {

  // normally we don't need to watch internal pekko persistence messages
  protected override def messageLabel(value: Any): Option[String] =
    if (!recoveryFinished) None
    else                                                                        // ignore commands while doing recovery, these are auto-stashed
    if (value.getClass.getName.startsWith("org.apache.pekko.persistence")) None // ignore akka persistence internal buzz
    else super.messageLabel(value)

  protected def eventLabel(value: Any): Option[String] = messageLabel(value)

  private var recovered: Boolean    = false
  private lazy val recoveries       = metrics.recoveries.labels(actorLabel)
  private lazy val recoveryEvents   = metrics.recoveryEvents.labels(actorLabel)
  private lazy val recoveryTime     = metrics.recoveryTime.labels(actorLabel).startTimer()
  private lazy val recoveryFailures = metrics.recoveryFailures.labels(actorLabel)
  private lazy val persistFailures  = metrics.persistFailures.labels(actorLabel)
  private lazy val persistRejects   = metrics.persistRejects.labels(actorLabel)

  protected[pekko] override def aroundReceive(receive: Receive, msg: Any): Unit = {
    if (!recoveryFinished) {
      if (ClassNameUtil.simpleName(msg.getClass).startsWith("ReplayedMessage")) recoveryEvents.inc()
    } else if (!recovered) {
      recoveries.inc()
      recoveryTime.observeDuration()
      recovered = true
    }
    internalAroundReceive(receive, msg)
  }

  override def persist[A](event: A)(handler: A => Unit): Unit =
    eventLabel(event)
      .map(label =>
        metrics.persistTime
          .labels(actorLabel, label)
          .observeExecution(
            this.internalPersist(event)(handler)
          )
      )
      .getOrElse(this.internalPersist(event)(handler))

  override def persistAll[A](events: immutable.Seq[A])(handler: A => Unit): Unit =
    metrics.persistTime
      .labels(actorLabel, "_all")
      .observeExecution(
        this.internalPersistAll(events)(handler)
      )

  override def persistAsync[A](event: A)(handler: A => Unit): Unit =
    eventLabel(event)
      .map(label =>
        metrics.persistTime
          .labels(actorLabel, label)
          .observeExecution(
            this.internalPersistAsync(event)(handler)
          )
      )
      .getOrElse(this.internalPersistAsync(event)(handler))

  override def persistAllAsync[A](events: immutable.Seq[A])(handler: A => Unit): Unit =
    metrics.persistTime
      .labels(actorLabel, "_all")
      .observeExecution(
        this.internalPersistAllAsync(events)(handler)
      )

  protected override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
    log.error(cause, "Recovery failed")
    recoveryFailures.inc()
  }
  protected override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
    log.error(cause, "Persist failed")
    persistFailures.inc()
  }
  protected override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
    log.error(cause, "Persist rejected")
    persistRejects.inc()
  }
}
