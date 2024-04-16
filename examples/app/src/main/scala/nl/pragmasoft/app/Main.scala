package nl.pragmasoft.app

import java.net.InetSocketAddress
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.cluster.Cluster
import cats.effect.{Async, ExitCode, IO, IOApp, Resource}
import cats.implicits.catsSyntaxOptionId
import com.typesafe.config.ConfigFactory
import org.http4s.server.Server
import cats.syntax.all._
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.{ExecutionContext, Future}

object Main extends IOApp {
  private implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLoggerFromName[IO]("Main")

  override def run(args: List[String]): IO[ExitCode] =
    service
      .onError(e => logger.error(e)("Application failed acquiring resource").toResource)
      .useForever
      .onError(e => logger.error(e)("Application failed to start"))
      .as(ExitCode.Success)

  def service: Resource[IO, Unit] =
    for {
      system <- IO {
        val config                       = ConfigFactory.load()
        implicit val system: ActorSystem = ActorSystem("app", config)
        system
      }.toResource
      _ <- logger.info("Starting services").toResource
      _ <- MetricService.of(
        InetSocketAddress.createUnresolved("0.0.0.0", 9095)
      )
      _ <- ApiService.of(
        InetSocketAddress.createUnresolved("0.0.0.0", 8080),
        system
      )
    } yield ()
}
