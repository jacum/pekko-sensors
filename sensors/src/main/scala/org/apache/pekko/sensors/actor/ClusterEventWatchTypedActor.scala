package org.apache.pekko.sensors.actor

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{Behavior, PostStop, PreRestart}
import org.apache.pekko.cluster.ClusterEvent._
import org.apache.pekko.cluster.Member
import org.apache.pekko.cluster.typed.{Cluster, Subscribe, Unsubscribe}
import io.prometheus.client.Counter
object ClusterEventWatchTypedActor {
  def apply(clusterEvents: Counter): Behavior[ClusterDomainEvent] =
    Behaviors.setup { context =>
      val cluster    = Cluster(context.system)
      val log        = context.log
      val meterEvent = registerEvent(clusterEvents) _

      cluster.subscriptions ! Subscribe(context.self, classOf[MemberEvent])

      Behaviors
        .receiveMessage[ClusterDomainEvent] { event =>
          event match {
            case e @ MemberUp(member) =>
              meterEvent(e, Some(member))
              log.info("Member is Up: {}", member.address)
            case e @ UnreachableMember(member) =>
              meterEvent(e, Some(member))
              log.info("Member detected as unreachable: {}", member)
            case e @ MemberRemoved(member, previousStatus) =>
              meterEvent(e, Some(member))
              log.info("Member is Removed: {} after {}", member.address, previousStatus)
            case e @ MemberDowned(member) =>
              meterEvent(e, Some(member))
              log.info("Member is Down: {}", member.address)
            case e =>
              meterEvent(e, None)
              log.info(s"Cluster domain event: $e")
          }
          Behaviors.same
        }
        .receiveSignal {
          case (_, PostStop) =>
            cluster.subscriptions ! Unsubscribe(context.self)
            Behaviors.same
        }
    }

  private def registerEvent(clusterEvents: Counter)(e: ClusterDomainEvent, member: Option[Member]): Unit =
    clusterEvents
      .labels(e.getClass.getSimpleName, member.map(_.address.toString).getOrElse(""))
      .inc()
}
