package org.apache.pekko.sensors.actor

import org.apache.pekko.actor.{Actor, ActorLogging}
import org.apache.pekko.cluster.ClusterEvent._
import org.apache.pekko.cluster.{Cluster, Member}
import org.apache.pekko.sensors.{ClassNameUtil, PekkoSensorsExtension}

class ClusterEventWatchActor extends Actor with ActorLogging {

  private val cluster                        = Cluster(context.system)
  private val metrics: PekkoSensorsExtension = PekkoSensorsExtension(this.context.system)
  private val clusterEvents                  = metrics.clusterEvents

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[ClusterDomainEvent])
    log.info("Starting cluster event watch")
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  private def registerEvent(e: ClusterDomainEvent, member: Option[Member] = None): Unit =
    clusterEvents
      .labels(ClassNameUtil.simpleName(e.getClass), member.map(_.address.toString).getOrElse(""))
      .inc()

  def receive: Receive = {
    case e @ MemberUp(member) =>
      registerEvent(e, Some(member))
      log.info("Member is Up: {}", member.address)
    case e @ UnreachableMember(member) =>
      registerEvent(e, Some(member))
      log.info("Member detected as unreachable: {}", member)
    case e @ MemberRemoved(member, previousStatus) =>
      registerEvent(e, Some(member))
      log.info("Member is Removed: {} after {}", member.address, previousStatus)
    case e @ MemberDowned(member) =>
      registerEvent(e, Some(member))
      log.info("Member is Down: {}", member.address)
    case e: ClusterDomainEvent =>
      registerEvent(e)
      log.info(s"Cluster domain event: $e")
  }

}
