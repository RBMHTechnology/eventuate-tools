package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import akka.actor.Props
import com.codahale.metrics.health.HealthCheck.Result
import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.Acceptor
import com.rbmhtechnology.eventuate.ReplicationEndpoint.DefaultLogName
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.ReplicationHealthMonitor.ReplicationConnectionNotYetEstablishedException
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.ReplicationHealthMonitor.ReplicationUnhealthyException
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.ReplicationHealthMonitor.healthName
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.akkaRemotingConfig
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.withActorSystems
import com.rbmhtechnology.eventuate.tools.test.EventLogs.withLevelDbLogConfigs
import com.rbmhtechnology.eventuate.tools.test.EventuallyWithDefaultTiming
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.replicationConnectionFor
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.replicationEndpoint
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
    "no replication connection can be established to a remote log" must {
      "report the replication connection as unhealthy until is it established" in
        withLevelDbLogConfigs(2) { configs =>
          withActorSystems(configs.map(_.withFallback(akkaRemotingConfig))) {
            case Seq(systemA, systemB) =>
              val connectionToA = replicationConnectionFor(systemA)
              val connectionToB = replicationConnectionFor(systemB)
              val endpointA = replicationEndpoint(connections = Set(connectionToB))(systemA)
              val healthRegistry = new HealthCheckRegistry()
              val endpointBId = systemB.name

              new ReplicationHealthMonitor(endpointA, healthRegistry, initiallyUnhealthy = Set(LogId(endpointBId, DefaultLogName)))
              eventually {
                val healthStatus = healthRegistry.runHealthCheck(healthName(endpointBId, DefaultLogName))
                healthStatus.getError shouldBe a[ReplicationConnectionNotYetEstablishedException]
              }
              val endpointB = replicationEndpoint(connections = Set(connectionToA))(systemB)
              eventually {
                healthRegistry.runHealthCheck(healthName(endpointBId, DefaultLogName)) shouldBe Result.healthy()
              }
          }
        }
    }
  }
}
