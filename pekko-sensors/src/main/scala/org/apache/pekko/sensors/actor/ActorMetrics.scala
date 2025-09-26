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
  private val receiveTimeouts = metrics.receiveTimeouts.labelValues(actorLabel)
  private lazy val exceptions = metrics.exceptions.labelValues(actorLabel)
  private val activeActors    = metrics.activeActors.labelValues(actorLabel)

  private val activityTimer = metrics.activityTime.labelValues(actorLabel).startTimer()

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
          .labelValues(actorLabel, _)
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
      .foreach(metrics.unhandledMessages.labelValues(actorLabel, _).inc())
    super.unhandled(message)
  }

}

trait PersistentActorMetrics extends ActorMetrics with PersistentActor {

  // normally we don't need to watch internal pekko persistence messages
  protected override def messageLabel(value: Any): Option[String] =
    if (!recoveryFinished) None
    else                                                             // ignore commands while doing recovery, these are auto-stashed
    if (value.getClass.getName.startsWith("pekko.persistence")) None // ignore pekko persistence internal buzz
    else super.messageLabel(value)

  protected def eventLabel(value: Any): Option[String] = messageLabel(value)

  private var recovered: Boolean        = false
  private var firstEventPassed: Boolean = false
  private lazy val recoveries           = metrics.recoveries.labelValues(actorLabel)
  private lazy val recoveryEvents       = metrics.recoveryEvents.labelValues(actorLabel)
  private val recoveryTime              = metrics.recoveryTime.labelValues(actorLabel).startTimer()
  private val recoveryToFirstEventTime  = metrics.recoveryTime.labelValues(actorLabel).startTimer()
  private lazy val recoveryFailures     = metrics.recoveryFailures.labelValues(actorLabel)
  private lazy val persistFailures      = metrics.persistFailures.labelValues(actorLabel)
  private lazy val persistRejects       = metrics.persistRejects.labelValues(actorLabel)
  private val waitingForRecoveryGauge   = metrics.waitingForRecovery.labelValues(actorLabel)
  private val waitingForRecoveryTime    = metrics.waitingForRecoveryTime.labelValues(actorLabel).startTimer()

  waitingForRecoveryGauge.inc()

  protected[pekko] override def aroundReceive(receive: Receive, msg: Any): Unit = {
    if (!recoveryFinished)
      ClassNameUtil.simpleName(msg.getClass) match {
        case msg if msg.startsWith("ReplayedMessage") =>
          if (!firstEventPassed) {
            recoveryToFirstEventTime.observeDuration()
            firstEventPassed = true
          }
          recoveryEvents.inc()

        case msg if msg.startsWith("RecoveryPermitGranted") =>
          waitingForRecoveryGauge.dec()
          waitingForRecoveryTime.observeDuration()

        case _ => ()
      }
    else if (!recovered) {
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
          .labelValues(actorLabel, label)
          .observeExecution(
            this.internalPersist(event)(handler)
          )
      )
      .getOrElse(this.internalPersist(event)(handler))

  override def persistAll[A](events: immutable.Seq[A])(handler: A => Unit): Unit =
    metrics.persistTime
      .labelValues(actorLabel, "_all")
      .observeExecution(
        this.internalPersistAll(events)(handler)
      )

  override def persistAsync[A](event: A)(handler: A => Unit): Unit =
    eventLabel(event)
      .map(label =>
        metrics.persistTime
          .labelValues(actorLabel, label)
          .observeExecution(
            this.internalPersistAsync(event)(handler)
          )
      )
      .getOrElse(this.internalPersistAsync(event)(handler))

  override def persistAllAsync[A](events: immutable.Seq[A])(handler: A => Unit): Unit =
    metrics.persistTime
      .labelValues(actorLabel, "_all")
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
