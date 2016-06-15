package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import akka.actor.Props
import com.codahale.metrics.health.HealthCheck.Result
import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.Acceptor
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.ReplicationHealthMonitor.ReplicationUnhealthyException
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.ReplicationHealthMonitor.healthName
import com.rbmhtechnology.eventuate.tools.test.EventuallyWithDefaultTiming
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.withBidirectionalReplicationEndpoints
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ReplicationHealthMonitorSpec extends WordSpec with Matchers with EventuallyWithDefaultTiming {
  "ReplicationHealthMonitor" when {
    "a Replicator can/cannot replicate from a remote log" must {
      "report the replication connection as healthy/unhealthy" in
        withBidirectionalReplicationEndpoints(logNames = Set("log1", "log2")) {
          (monitoredEndpoint, remoteEndpoint) =>
            val healthRegistry = new HealthCheckRegistry()
            new ReplicationHealthMonitor(monitoredEndpoint, healthRegistry)
            eventually {
              monitoredEndpoint.logNames.foreach { logName =>
                healthRegistry.runHealthCheck(healthName(remoteEndpoint.id, logName)) shouldBe Result.healthy()
              }
            }

            remoteEndpoint.system.stop(remoteEndpoint.acceptor)

            eventually {
              monitoredEndpoint.logNames.foreach { logName =>
                val healthStatus = healthRegistry.runHealthCheck(healthName(remoteEndpoint.id, logName))
                healthStatus.getError shouldBe a[ReplicationUnhealthyException]
              }
            }

            val restarted = remoteEndpoint.system.actorOf(Props(new Acceptor(remoteEndpoint)), name = Acceptor.Name)
            restarted ! Acceptor.Process

            eventually {
              monitoredEndpoint.logNames.foreach { logName =>
                healthRegistry.runHealthCheck(healthName(remoteEndpoint.id, logName)) shouldBe Result.healthy()
              }
            }
        }
    }
  }
}
