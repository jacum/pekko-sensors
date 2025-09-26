package nl.pragmasoft.pekko.sensors.dispatch

import com.typesafe.config.Config

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

object Helpers {

  /**
   * INTERNAL API
   */
  implicit final class ConfigOps(val config: Config) extends AnyVal {
    def getMillisDuration(path: String): FiniteDuration = getDuration(path, TimeUnit.MILLISECONDS)

    def getNanosDuration(path: String): FiniteDuration = getDuration(path, TimeUnit.NANOSECONDS)

    private def getDuration(path: String, unit: TimeUnit): FiniteDuration =
      Duration(config.getDuration(path, unit), unit)
  }
}
