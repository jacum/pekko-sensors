package org.apache.pekko.sensors

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.cluster.{Cluster, MemberStatus}

import scala.concurrent.Future

class ClusterHealthCheck(system: ActorSystem) extends (() => Future[Boolean]) {
  private val cluster = Cluster(system)
  override def apply(): Future[Boolean] =
    Future.successful(cluster.selfMember.status == MemberStatus.Up)
}
