package com.rbmhtechnology.eventuate.tools.metrics.dropwizard

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.servlets.MetricsServlet
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.ReplicationFilter
import com.rbmhtechnology.eventuate.tools.test.EventLogs.eventInspector
import com.rbmhtechnology.eventuate.tools.test.EventuallyWithDefaultTiming
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.defaultLogFilter
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.withBidirectionalReplicationEndpoints
import com.rbmhtechnology.eventuate.tools.test.TestTimings
import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletConfig

import scala.collection.JavaConverters.mapAsScalaMapConverter

class ReplicationEndpointGaugeSpec extends WordSpec with Matchers with EventuallyWithDefaultTiming {

  import ReplicationEndpointGaugeSpec._

  "ReplicationEndpointGauge" when {
    "when two replication endpoints with local filters replicate" must {
      "record ReplicationEndpointMetrics" in
        withBidirectionalReplicationEndpoints(aFilters = defaultLogFilter(evenFilter), bFilters = defaultLogFilter(oddFilter)) {
          (endpointA, endpointB) =>
            val logAId = logId(endpointA)
            val logBId = logId(endpointB)

            val metricsA = new MetricRegistry
            metricsA.register(endpointA.id, new ReplicationEndpointGauge(endpointA, TestTimings.timeout.duration))

            val metricsB = new MetricRegistry
            metricsB.register(endpointB.id, new ReplicationEndpointGauge(endpointB, TestTimings.timeout.duration))

            eventInspector(endpointA).emitAndWait(1 to 3)
            eventually {
              getGauge(metricsA, endpointA.id) shouldBe replicationEndpointMetrics(
                localSeqNo = 3, Map(logAId -> 3), endpointB, replicationProgress = 1
              )
              getGauge(metricsB, endpointB.id) shouldBe replicationEndpointMetrics(
                localSeqNo = 1, Map(logAId -> 2), endpointA, replicationProgress = 3
              )
            }

            eventInspector(endpointB).emitAndWait(1 to 3)
            eventually {
              getGauge(metricsA, endpointA.id) shouldBe replicationEndpointMetrics(
                localSeqNo = 5, Map(logAId -> 3, logBId -> 4), endpointB, replicationProgress = 4
              )
              getGauge(metricsB, endpointB.id) shouldBe replicationEndpointMetrics(
                localSeqNo = 4, Map(logAId -> 2, logBId -> 4), endpointA, replicationProgress = 5
              )
            }
        }
    }
    "when two replication endpoints replicate" must {
      "record ReplicationEndpointMetrics exportable through MetricsServlet" in
        withBidirectionalReplicationEndpoints() { (localEndpoint, remoteEndpoint) =>
          val metrics = new MetricRegistry
          metrics.register(localEndpoint.id, new ReplicationEndpointGauge(localEndpoint, TestTimings.timeout.duration))

          eventInspector(localEndpoint).emitAndWait(1 to 3)

          val servlet = createMetricsServlet(metrics)

          eventually {
            val rootNode = getJsonMetrics(servlet)
              .get("gauges").get(localEndpoint.id).get("value").get("replicatedLogsMetrics").get(logName(localEndpoint))
            rootNode.get("localSequenceNo").longValue shouldBe 3
            rootNode.get("localVersionVector").get(logId(localEndpoint)).longValue shouldBe 3
            rootNode.get("replicationProgress").get(logId(remoteEndpoint)).longValue shouldBe 3
          }
        }
    }
  }
}

object ReplicationEndpointGaugeSpec {
  val evenFilter = new ReplicationFilter {
    override def apply(event: DurableEvent) = event.payload match {
      case i: Int => i % 2 == 0
      case _      => false
    }
  }
  val oddFilter = new ReplicationFilter {
    override def apply(event: DurableEvent) = event.payload match {
      case i: Int => i % 2 == 1
      case _      => false
    }
  }

  def logName(endpoint: ReplicationEndpoint): String = endpoint.logNames.head

  def logId(endpoint: ReplicationEndpoint): String = endpoint.logId(logName(endpoint))

  def replicationEndpointMetrics(localSeqNo: Long, versionVector: Map[String, Long], remoteEndpoint: ReplicationEndpoint, replicationProgress: Long) = {
    val log = logName(remoteEndpoint)
    ReplicationEndpointMetrics(
      Map(log -> ReplicatedLogMetrics(
        localSeqNo,
        versionVector,
        Map(remoteEndpoint.logId(log) -> replicationProgress)
      ))
    )
  }

  def getGauge(metrics: MetricRegistry, name: String) = metrics.getGauges.asScala(name).getValue

  private val mapper = new ObjectMapper()

  def createMetricsServlet(metrics: MetricRegistry): TestableMetricsServlet = {
    val servlet = new TestableMetricsServlet(metrics)
    servlet.init(new MockServletConfig())
    servlet
  }

  def getJsonMetrics(servlet: TestableMetricsServlet): JsonNode = {
    val request = new MockHttpServletRequest()
    request.setParameter("pretty", "true")
    val response = new MockHttpServletResponse()
    servlet.doGet(request, response)
    mapper.readTree(response.getContentAsString)
  }

  class TestableMetricsServlet(registry: MetricRegistry) extends MetricsServlet(registry) {
    // make accessible for test
    override def doGet(req: HttpServletRequest, resp: HttpServletResponse) = super.doGet(req, resp)
  }
}
