package nl.pragmasoft.app

import java.io.CharArrayWriter
import java.net.InetSocketAddress
import cats.effect.{IO, Resource, Temporal}
import com.typesafe.scalalogging.LazyLogging
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot._
import io.prometheus.client.{Collector, CollectorRegistry}
import io.prometheus.jmx.JmxCollector
import org.apache.pekko.sensors.PekkoSensors
import org.http4s.dsl.io._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.{Router, Server}
import org.http4s._
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.io.Source

object MetricService {

  val registry: CollectorRegistry = PekkoSensors.prometheusRegistry
  DefaultExports.register(registry)

  def register(collector: Collector): Unit                   = registry.register(collector)
  private implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLoggerFromName[IO]("API")

  def of(socketAddress: InetSocketAddress)(implicit timer: Temporal[IO]): Resource[IO, Server] = {
    val encoder = EntityEncoder.stringEncoder

    def exportMetrics: String = {
      val writer = new CharArrayWriter(16 * 1024)
      TextFormat.write004(writer, registry.metricFamilySamples)
      writer.toString
    }

    for {
      server <-
        BlazeServerBuilder[IO]
          .bindSocketAddress(socketAddress)
          .withHttpApp(
            Router(
              "/metrics" ->
                  HttpRoutes.of[IO] {
                    case GET -> Root =>
                      IO(Response(status = Ok, body = encoder.toEntity(exportMetrics).body, headers = Headers(Header.Raw(ci"Content-Type", TextFormat.CONTENT_TYPE_004))))
                  }
            ) orNotFound
          )
          .resource
      _ <- logger.info("Metric service initialised").toResource
    } yield server
  }

}
