package com.rbmhtechnology.eventuate.tools.metrics.kamon

import akka.testkit.TestProbe
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.tools.metrics.kamon.ReplicatedLogMetrics.category
import com.rbmhtechnology.eventuate.tools.metrics.kamon.ReplicatedLogMetrics.replicationProgressName
import com.rbmhtechnology.eventuate.tools.metrics.kamon.ReplicatedLogMetrics.sequenceNoName
import com.rbmhtechnology.eventuate.tools.metrics.kamon.ReplicatedLogMetrics.versionVectorName
import com.rbmhtechnology.eventuate.tools.test.EventLogs._
import com.rbmhtechnology.eventuate.tools.test.EventuallyWithDefaultTiming
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.defaultLogId
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.evenFilter
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.oddFilter
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.withBidirectionalReplicationEndpoints
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.withLevelDbReplicationEndpoint
import kamon.Kamon
import kamon.metric.Entity
import kamon.metric.EntitySnapshot
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpec

class KamonReplicationEndpointMetricsSpec extends WordSpec with Matchers with EventuallyWithDefaultTiming with BeforeAndAfterAll {

  import KamonReplicationEndpointMetricsSpec._

  override protected def beforeAll(): Unit = Kamon.start()

  "KamonReplicationEndpointMetrics.startRecording" when {
    "called for an ReplicationEndpoint" must {
      "record metrics for all logs of the endpoint" in {
        val logNames = Set("log1", "log2")
        withBidirectionalReplicationEndpoints(logNames, aFilters = logNames.map(_ -> evenFilter).toMap, bFilters = logNames.map(_ -> oddFilter).toMap) {
          (endpointA, endpointB) =>
            val logIds = logNames.map(endpointA.logId) zip logNames.map(endpointB.logId)
            new KamonReplicationEndpointMetrics(endpointA).startRecording()
            new KamonReplicationEndpointMetrics(endpointB).startRecording()

            logNames.foreach(eventInspector(endpointA, _).emitAndWait(1 to 3))

            val subscriber = snapshotSubscriber(endpointA)
            eventually {
              val snapshot = subscriber.expectMsgClass(classOf[TickMetricSnapshot])
              logIds.foreach {
                case (logAId, logBId) =>
                  checkLogSnapshot(
                    snapshot.metrics(entity(logAId)),
                    localSequenceNo = 3, Map(logAId -> 3), logBId, replicationProgress = 1
                  )
                  checkLogSnapshot(
                    snapshot.metrics(entity(logBId)),
                    localSequenceNo = 1, Map(logAId -> 2), logAId, replicationProgress = 3
                  )
              }
            }

            logNames.foreach(eventInspector(endpointB, _).emitAndWait(1 to 3))

            eventually {
              val snapshot = subscriber.expectMsgClass(classOf[TickMetricSnapshot])
              logIds.foreach {
                case (logAId, logBId) =>
                  checkLogSnapshot(
                    snapshot.metrics(entity(logAId)),
                    localSequenceNo = 5, Map(logAId -> 3, logBId -> 4), logBId, replicationProgress = 4
                  )
                  checkLogSnapshot(
                    snapshot.metrics(entity(logBId)),
                    localSequenceNo = 4, Map(logAId -> 2, logBId -> 4), logAId, replicationProgress = 5
                  )
              }
            }
        }
      }
    }

    "called twice" must {
      "throw IllegalArgumentException" in withLevelDbReplicationEndpoint() { endpoint =>
        val metrics = new KamonReplicationEndpointMetrics(endpoint)
        metrics.startRecording()
        an[IllegalArgumentException] shouldBe thrownBy(metrics.startRecording())
      }
    }
  }

  "KamonMetrics.stopRecording" when {
    "called after recording started" must {
      "stop recording metrics" in withBidirectionalReplicationEndpoints() { (endpointA, _) =>
        val metrics = new KamonReplicationEndpointMetrics(endpointA)
        val subscriber = snapshotSubscriber(endpointA)
        metrics.startRecording()
        metrics.stopRecording()
        eventInspector(endpointA).emitAndWait(1 to 3)
        val snapshot = subscriber.expectMsgClass(classOf[TickMetricSnapshot])
        snapshot.metrics.get(entity(defaultLogId(endpointA))) shouldBe None
      }
    }
  }

  private def checkLogSnapshot(
    logSnapshot: EntitySnapshot,
    localSequenceNo: Long,
    versionVector: Map[String, Long],
    remoteLogId: String,
    replicationProgress: Long
  ): Unit = {
    histogramMax(logSnapshot, sequenceNoName) shouldBe Some(localSequenceNo)
    versionVector.foreach {
      case (processId, time) =>
        histogramMax(logSnapshot, versionVectorName(processId)) shouldBe Some(time)
    }
    histogramMax(logSnapshot, replicationProgressName(remoteLogId)) shouldBe Some(replicationProgress)
  }
}

object KamonReplicationEndpointMetricsSpec {

  def histogramMax(snapshot: EntitySnapshot, name: String) =
    snapshot.histogram(name).map(_.max)

  def snapshotSubscriber(endpointA: ReplicationEndpoint): TestProbe = {
    val subscriber = new TestProbe(endpointA.system)
    Kamon.metrics.subscribe(category, "**", subscriber.ref)
    subscriber
  }

  def entity(name: String): Entity = Entity(name, category)
}
