package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import akka.actor.Actor
import akka.actor.Props
import com.codahale.metrics.health.HealthCheck.Result
import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.log.CircuitBreaker
import com.rbmhtechnology.eventuate.log.CircuitBreaker.ServiceFailed
import com.rbmhtechnology.eventuate.log.CircuitBreaker.ServiceNormal
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.PersistenceHealthMonitor.healthName
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.withActorSystem
import com.rbmhtechnology.eventuate.tools.test.EventuallyWithDefaultTiming
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers
import org.scalatest.WordSpec

class PersistenceHealthMonitorSpec extends WordSpec with Matchers with EventuallyWithDefaultTiming {

  import PersistenceHealthMonitorSpec._

  "PersistenceHealthMonitor" when {
    "an EventLog's circuit breaker opens/closes" must {
      "report the event-log persistence as unhealthy/healthy" in
        withActorSystem(circuitBreakerOpenAfterRetriesConfig(CircuitBreakerOpenAfterRetries)) { implicit system =>
          val eventLogFactory = (logId: String) => Props(new CircuitBreaker(testEventLogProps, batching = true))
          val healthRegistry = new HealthCheckRegistry()
          val endpoint = ReplicationEndpoints.replicationEndpoint(logFactory = eventLogFactory)
          val (logName, eventLog) = endpoint.logs.head
          val logId = endpoint.logId(logName)
          new PersistenceHealthMonitor(endpoint, healthRegistry)

          eventLog ! ServiceFailed(logId, CircuitBreakerOpenAfterRetries, InitialCause)

          eventually {
            healthRegistry.runHealthCheck(healthName(logId)).getError shouldBe InitialCause
          }

          eventLog ! ServiceNormal(logId)

          eventually {
            healthRegistry.runHealthCheck(healthName(logId)) shouldBe Result.healthy()
          }
        }
    }
  }
}

private object PersistenceHealthMonitorSpec {

  val CircuitBreakerOpenAfterRetries = 1

  def circuitBreakerOpenAfterRetriesConfig(circuitBreakerOpenAfterRetries: Int) =
    ConfigFactory.parseString(
      s"eventuate.log.circuit-breaker.open-after-retries = $circuitBreakerOpenAfterRetries"
    )

  val InitialCause = new RuntimeException("Test Write Failure")

  def testEventLogProps = Props[TestEventLog]

  class TestEventLog extends Actor {
    override def receive: Receive = Actor.emptyBehavior
  }
}
