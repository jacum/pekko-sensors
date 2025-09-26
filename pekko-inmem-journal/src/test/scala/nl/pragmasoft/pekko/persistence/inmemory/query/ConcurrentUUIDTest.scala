package nl.pragmasoft.pekko.persistence.inmemory.query

import nl.pragmasoft.pekko.persistence.inmemory.TestSpec
import org.scalatest.Ignore

import java.util.UUID
import scala.concurrent.Future

@Ignore
class ConcurrentUUIDTest extends TestSpec {
  def getNow(x: Any): Future[UUID] = Future(nl.pragmasoft.pekko.persistence.inmemory.nowUuid)
  it should "get uuids concurrently" in {
    Future.sequence((1 to 1000).map(getNow)).futureValue
  }
}
