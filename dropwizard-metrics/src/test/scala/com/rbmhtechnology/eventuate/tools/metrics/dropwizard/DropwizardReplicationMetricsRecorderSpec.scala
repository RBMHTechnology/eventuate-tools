package com.rbmhtechnology.eventuate.tools.metrics.dropwizard

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.MetricRegistry.name
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.tools.metrics.dropwizard.DropwizardReplicationMetricsRecorder.replicationProgressName
import com.rbmhtechnology.eventuate.tools.metrics.dropwizard.DropwizardReplicationMetricsRecorder.sequenceNoName
import com.rbmhtechnology.eventuate.tools.metrics.dropwizard.DropwizardReplicationMetricsRecorder.versionVectorName
import com.rbmhtechnology.eventuate.tools.test.EventLogs._
import com.rbmhtechnology.eventuate.tools.test.EventuallyWithDefaultTiming
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints._
import org.scalatest.Matchers
import org.scalatest.WordSpec

import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt

class DropwizardReplicationMetricsRecorderSpec extends WordSpec with Matchers with EventuallyWithDefaultTiming {

  import DropwizardReplicationMetricsRecorderSpec._

  "ReplicationMetricsRecorder" when {
    "when initialized with an replication endpoint that replicates with local filters" must {
      "record replication metrics for the endpoint" in
        withBidirectionalReplicationEndpoints(aFilters = defaultLogFilter(evenFilter), bFilters = defaultLogFilter(oddFilter)) {
          (endpointA, endpointB) =>
            val logAId = defaultLogId(endpointA)
            val logBId = defaultLogId(endpointB)

            val metricsA = new MetricRegistry
            new DropwizardReplicationMetricsRecorder(endpointA, metricsA, pollMetricsMinDelay = 100.millis)

            val metricsB = new MetricRegistry
            new DropwizardReplicationMetricsRecorder(endpointB, metricsB, pollMetricsMinDelay = 100.millis)

            eventInspector(endpointA).emitAndWait(1 to 3)
            eventually {
              checkMetrics(metricsA, endpointA,
                localSeqNo = 3, Map(logAId -> 3), endpointB, replicationProgress = 1)
              checkMetrics(metricsB, endpointB,
                localSeqNo = 1, Map(logAId -> 2), endpointA, replicationProgress = 3)
            }

            eventInspector(endpointB).emitAndWait(1 to 3)
            eventually {
              checkMetrics(metricsA, endpointA,
                localSeqNo = 5, Map(logAId -> 3, logBId -> 4), endpointB, replicationProgress = 4)
              checkMetrics(metricsB, endpointB,
                localSeqNo = 4, Map(logAId -> 2, logBId -> 4), endpointA, replicationProgress = 5)
            }
        }
    }
  }

  private def checkMetrics(
    metricRegistry: MetricRegistry,
    endpoint: ReplicationEndpoint,
    localSeqNo: Long,
    versionVector: Map[String, Long],
    remoteEndpoint: ReplicationEndpoint,
    replicationProgress: Long
  ): Unit =
    {
      getGauge(metricRegistry, name(defaultLogId(endpoint), sequenceNoName)) shouldBe localSeqNo
      versionVector.foreach {
        case (processId, time) =>
          getGauge(metricRegistry, name(defaultLogId(endpoint), versionVectorName(processId))) shouldBe time
      }
      getGauge(metricRegistry, name(defaultLogId(endpoint), replicationProgressName(defaultLogId(remoteEndpoint)))) shouldBe replicationProgress
    }
}

private object DropwizardReplicationMetricsRecorderSpec {
  def getGauge(metrics: MetricRegistry, name: String) = metrics.getGauges.asScala(name).getValue
}