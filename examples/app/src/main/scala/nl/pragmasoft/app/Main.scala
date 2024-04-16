package nl.pragmasoft.app

import java.net.InetSocketAddress
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.cluster.Cluster
import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits.catsSyntaxOptionId
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.http4s.server.Server

import scala.concurrent.{ExecutionContext, Future}
import org.cassandraunit.utils.EmbeddedCassandraServerHelper.startEmbeddedCassandra

object Main extends IOApp with LazyLogging {

  override def run(args: List[String]): IO[ExitCode] = {
    val config                                      = ConfigFactory.load()
    implicit val system: ActorSystem                = ActorSystem("app", config)
    implicit val executionContext: ExecutionContext = system.dispatcher

    val mainResource: Resource[IO, Server] =
      for {
        _ <- Resource.eval(IO.async[Unit] { callback =>
          IO {
            IO(Cluster(system).registerOnMemberUp {
              logger.info("Pekko cluster is now up")
              callback(Right(()))
            }).some
          }
        })
        _ <- MetricService.resource(
          InetSocketAddress.createUnresolved("0.0.0.0", 9095)
        )
        apiService <- ApiService.resource(
          InetSocketAddress.createUnresolved("0.0.0.0", 8080),
          system
        )
      } yield apiService

    mainResource.use { s =>
      logger.info(s"App started at ${s.address}/${s.baseUri}, enabling the readiness in Pekko management")
      ReadinessCheck.enable()
      IO.never
    }
      .as(ExitCode.Success)
  }
}

object ReadinessCheck {
  var ready: Boolean = false
  def enable(): Unit = ReadinessCheck.ready = true
}

class ReadinessCheck extends (() => Future[Boolean]) {
  override def apply(): Future[Boolean] = Future.successful(ReadinessCheck.ready)
}
