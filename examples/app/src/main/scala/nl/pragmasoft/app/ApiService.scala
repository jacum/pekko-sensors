package nl.pragmasoft.app

import java.net.InetSocketAddress
import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, NoSerializationVerificationNeeded, Props, ReceiveTimeout}
import org.apache.pekko.pattern.ask
import org.apache.pekko.persistence.PersistentActor
import org.apache.pekko.util.Timeout
import cats.effect.kernel.Temporal
import cats.effect.{IO, Resource}
import nl.pragmasoft.app.ResponderActor._
import org.apache.pekko.sensors.actor.{ActorMetrics, PersistentActorMetrics}
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.io._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.{Router, Server}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._
import scala.util.Random

object ApiService {
  private implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLoggerFromName[IO]("API")

  def of(socketAddress: InetSocketAddress, system: ActorSystem)(implicit cs: Temporal[IO]): Resource[IO, Server] = {

    def pingActor(event: Any, actor: ActorRef): IO[Response[IO]] = {
      val actorResponse = actor.ask(event)(Timeout.durationToTimeout(10 seconds))

      if (Random.nextInt(100) <= 3) actor ! UnknownMessage

      IO.fromFuture(IO(actorResponse)).attempt.flatMap {
        case Left(e: Exception) => InternalServerError(e.getMessage)
        case _                  => Ok()
      }
    }

    for {
      server <-
        BlazeServerBuilder[IO]
          .bindSocketAddress(socketAddress)
          .withHttpApp(
            Router("/api" -> HttpRoutes.of[IO] {
                  case GET -> Root / "health" => Ok()

                  case POST -> Root / "ping" / actorId / maxSleep =>
                    val actor = system.actorOf(Props(classOf[ResponderActor]), s"responder-default-$actorId")
                    pingActor(Ping(maxSleep.toInt), actor)

                  case POST -> Root / "ping-tp" / actorId / maxSleep =>
                    val actor = system.actorOf(Props(classOf[ResponderActor]).withDispatcher("pekko.actor.default-blocking-io-dispatcher"), s"responder-tp-$actorId")
                    pingActor(Ping(maxSleep.toInt), actor)

                  case POST -> Root / "ping-persistence" / actorId / maxSleep =>
                    val actor = system.actorOf(Props(classOf[PersistentResponderActor]), s"persistent-responder-$actorId")
                    pingActor(ValidCommand, actor)

                }) orNotFound
          )
          .resource
      _ <- logger.info("Metric service initialised").toResource
    } yield server
  }
}

object ResponderActor {

  case class Ping(maxSleep: Int)    extends NoSerializationVerificationNeeded
  case object KnownError            extends NoSerializationVerificationNeeded
  case object UnknownMessage        extends NoSerializationVerificationNeeded
  case object BlockTooLong          extends NoSerializationVerificationNeeded
  case object Pong                  extends NoSerializationVerificationNeeded
  case object ValidCommand          extends NoSerializationVerificationNeeded
  case class ValidEvent(id: String) extends NoSerializationVerificationNeeded
}

class ResponderActor extends Actor with ActorMetrics {
  context.setReceiveTimeout(30 + Random.nextInt(90) seconds)

  def receive: Receive = {
    case Ping(maxSleep) =>
      Thread.sleep(Random.nextInt(maxSleep))
      sender() ! Pong
      if (Random.nextInt(100) <= 5)
        throw new IllegalStateException("boom")

    case KnownError =>
      throw new Exception("known")
    case BlockTooLong =>
      Thread.sleep(6000)
    case ReceiveTimeout =>
      context.stop(self)
  }
}

class PersistentResponderActor extends PersistentActor with PersistentActorMetrics {
  var counter = 0
  context.setReceiveTimeout(30 + Random.nextInt(90) seconds)

  def receiveRecover: Receive = {
    case ValidEvent(e) => counter = e.toInt
  }

  def receiveCommand: Receive = {
    case ValidCommand =>
      val replyTo = sender()
      persist(ValidEvent(counter.toString)) { _ =>
        counter += 1
        replyTo ! Pong
      }
    case ReceiveTimeout =>
      context.stop(self)
  }

  def persistenceId: String = context.self.actorRef.path.name
}
