package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import akka.actor.PoisonPill
import com.codahale.metrics.health.HealthCheck.Result.healthy
import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.ActorHealthMonitor.AcceptorActorTerminatedException
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.ActorHealthMonitor.EventLogActorTerminatedException
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.ActorHealthMonitor.acceptorActorHealthName
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.ActorHealthMonitor.logActorHealthName
import com.rbmhtechnology.eventuate.tools.test.EventuallyWithDefaultTiming
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.withLevelDbReplicationEndpoint
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ActorHealthMonitorSpec extends WordSpec with Matchers with EventuallyWithDefaultTiming {

  import ActorHealthMonitorSpec._

  "ActorMonitor" when {
    "all EventLog actors are up" must {
      "Report healthy for each" in {
        withLevelDbReplicationEndpoint(LogNames) { endpoint =>
          val healthRegistry = new HealthCheckRegistry()
          new ActorHealthMonitor(endpoint, healthRegistry)
          LogNames.foreach { logName =>
            eventually {
              healthRegistry.runHealthCheck(logActorHealthName(endpoint.logId(logName))) shouldBe healthy()
            }
          }
        }
      }
    }
    "an EventLog actors terminate" must {
      "Report unhealthy for that and healthy for all others" in {
        withLevelDbReplicationEndpoint(LogNames) { endpoint =>
          val healthRegistry = new HealthCheckRegistry()
          new ActorHealthMonitor(endpoint, healthRegistry)
          endpoint.logs(LogNames.head) ! PoisonPill
          eventually {
            healthRegistry.runHealthCheck(logActorHealthName(endpoint.logId(LogNames.head))).getError shouldBe an[EventLogActorTerminatedException]
            LogNames.tail.foreach(logName => healthRegistry.runHealthCheck(logActorHealthName(endpoint.logId(logName))) shouldBe healthy())
          }
        }
      }
    }
    "the Acceptor actor is up" must {
      "Report healthy for that" in {
        withLevelDbReplicationEndpoint() { endpoint =>
          val healthRegistry = new HealthCheckRegistry()
          new ActorHealthMonitor(endpoint, healthRegistry)
          eventually {
            healthRegistry.runHealthCheck(acceptorActorHealthName) shouldBe healthy()
          }
        }
      }
    }
    "the Acceptor actor terminates" must {
      "Report unhealthy for that" in {
        withLevelDbReplicationEndpoint() { endpoint =>
          val healthRegistry = new HealthCheckRegistry()
          new ActorHealthMonitor(endpoint, healthRegistry)
          endpoint.acceptor ! PoisonPill
          eventually {
            healthRegistry.runHealthCheck(acceptorActorHealthName).getError shouldBe AcceptorActorTerminatedException
          }
        }
      }
    }
  }
}

private object ActorHealthMonitorSpec {
  val LogNames = Set("L1", "L2")
}
