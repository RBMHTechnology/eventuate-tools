package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.HealthCheckRegistries.optionallyPrefixed
import com.rbmhtechnology.eventuate.tools.test.EventuallyWithDefaultTiming
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.withBidirectionalReplicationEndpoints
import org.scalatest.Matchers
import org.scalatest.WordSpec

import scala.collection.JavaConverters._

class ReplicationEndpointHealthMonitorSpec extends WordSpec with Matchers with EventuallyWithDefaultTiming {

  "ReplicationEndpointHealthMonitorSpec.stopMonitoring" must {
    "de-register all health checks" in withBidirectionalReplicationEndpoints() { (monitoredEndpoint, remoteEndpoint) =>
      val healthRegistry = new HealthCheckRegistry
      val prefix = Some("prefix")
      val monitor = new ReplicationEndpointHealthMonitor(monitoredEndpoint, healthRegistry, prefix)

      val expectedHealthNames = monitoredEndpoint.logNames.flatMap { logName =>
        Set(ReplicationHealthMonitor.healthName(remoteEndpoint.id, logName), ActorHealthMonitor.logActorHealthName(monitoredEndpoint.logId(logName)))
      } + ActorHealthMonitor.acceptorActorHealthName
      eventually {
        healthRegistry.getNames.asScala shouldBe expectedHealthNames.map(optionallyPrefixed(_, prefix))
      }

      monitor.stopMonitoring()

      eventually {
        healthRegistry.getNames.asScala shouldBe Set.empty
      }
    }
  }
}
