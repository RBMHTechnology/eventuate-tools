package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.HealthCheckRegistries.optionallyPrefixed
import com.rbmhtechnology.eventuate.tools.test.EventuallyWithDefaultTiming
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.withBidirectionalReplicationEndpoints
import org.scalatest.Inspectors
import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures

import scala.collection.JavaConverters._

class ReplicationEndpointHealthMonitorSpec extends WordSpec with Matchers with EventuallyWithDefaultTiming with ScalaFutures with Inspectors {

  import ReplicationEndpointHealthMonitorSpec._

  "ReplicationEndpointHealthMonitor.stopMonitoring" must {
    "de-register all health checks" in withBidirectionalReplicationEndpoints() { (monitoredEndpoint, remoteEndpoint) =>
      val healthRegistry = new HealthCheckRegistry
      val prefix = Some("prefix")
      val monitor = new ReplicationEndpointHealthMonitor(monitoredEndpoint, healthRegistry, prefix)

      val expectedHealthNames = actorHealthNames(monitoredEndpoint) ++
        circuitBreakerHealthNames(monitoredEndpoint) ++
        replicationHealthNames(remoteEndpoint)
      eventually {
        healthRegistry.getNames.asScala shouldBe expectedHealthNames.map(optionallyPrefixed(_, prefix))
      }

      monitor.stopMonitoring()

      eventually {
        healthRegistry.getNames.asScala shouldBe Set.empty
      }
    }
  }
  "ReplicationEndpointHealthMonitor" when {
    "ActorSystem stopps prematurely" must {
      "report unhealthy for all monitored items" in withBidirectionalReplicationEndpoints() { (monitoredEndpoint, remoteEndpoint) =>
        val healthRegistry = new HealthCheckRegistry
        val monitor = new ReplicationEndpointHealthMonitor(monitoredEndpoint, healthRegistry)

        val expectedHealthNames = actorHealthNames(monitoredEndpoint) ++
          circuitBreakerHealthNames(monitoredEndpoint) ++
          replicationHealthNames(remoteEndpoint)
        eventually {
          healthRegistry.getNames.asScala shouldBe expectedHealthNames
        }

        whenReady(monitoredEndpoint.system.terminate()) { _ =>
          eventually {
            healthRegistry.getNames.asScala shouldBe expectedHealthNames
            forAll(healthRegistry.runHealthChecks().asScala) {
              case (_, result) =>
                result shouldNot be('isHealthy)
            }
          }
        }
      }
    }
  }
}

object ReplicationEndpointHealthMonitorSpec {
  def actorHealthNames(endpoint: ReplicationEndpoint): Set[String] =
    endpoint.logNames.map(logName => ActorHealthMonitor.logActorHealthName(endpoint.logId(logName))) + ActorHealthMonitor.acceptorActorHealthName

  def replicationHealthNames(endpoint: ReplicationEndpoint): Set[String] =
    endpoint.logNames.map(logName => ReplicationHealthMonitor.healthName(endpoint.id, logName))

  def circuitBreakerHealthNames(endpoint: ReplicationEndpoint): Set[String] =
    endpoint.logNames.map(logName => CircuitBreakerHealthMonitor.healthName(endpoint.logId(logName)))
}
