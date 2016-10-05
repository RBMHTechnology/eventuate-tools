package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import akka.actor.Actor
import akka.actor.Props
import com.codahale.metrics.health.HealthCheck.Result
import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.log.CircuitBreaker
import com.rbmhtechnology.eventuate.log.CircuitBreaker.ServiceFailed
import com.rbmhtechnology.eventuate.log.CircuitBreaker.ServiceNormal
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.CircuitBreakerHealthMonitor.healthName
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.withActorSystem
import com.rbmhtechnology.eventuate.tools.test.EventuallyWithDefaultTiming
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.replicationEndpoint
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers
import org.scalatest.WordSpec

class CircuitBreakerHealthMonitorSpec extends WordSpec with Matchers with EventuallyWithDefaultTiming {

  import CircuitBreakerHealthMonitorSpec._

  "CircuitBreakerHealthMonitor" when {
    "an EventLog's circuit breaker opens/closes" must {
      "report the circuit breaker as unhealthy/healthy" in
        withActorSystem(circuitBreakerOpenAfterRetriesConfig(CircuitBreakerOpenAfterRetries)) { implicit system =>
          val healthRegistry = new HealthCheckRegistry()
          val endpoint = replicationEndpoint(logFactory = _ => testEventLogWithCircuitBreakerProps)
          val (logName, eventLog) = endpoint.logs.head
          val logId = endpoint.logId(logName)
          new CircuitBreakerHealthMonitor(endpoint, healthRegistry)

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
    "initialized" must {
      "report the circuit breaker as healthy" in withActorSystem() { implicit system =>
        val healthRegistry = new HealthCheckRegistry()
        val endpoint = replicationEndpoint(logFactory = _ => testEventLogWithCircuitBreakerProps)

        new CircuitBreakerHealthMonitor(endpoint, healthRegistry)

        val logId = endpoint.logId(endpoint.logNames.head)
        eventually {
          healthRegistry.runHealthCheck(healthName(logId)) shouldBe Result.healthy()
        }
      }
    }
  }
}

private object CircuitBreakerHealthMonitorSpec {

  val CircuitBreakerOpenAfterRetries = 1

  def circuitBreakerOpenAfterRetriesConfig(circuitBreakerOpenAfterRetries: Int) =
    ConfigFactory.parseString(
      s"eventuate.log.circuit-breaker.open-after-retries = $circuitBreakerOpenAfterRetries"
    )

  val InitialCause = new RuntimeException("Test Write Failure")

  def testEventLogWithCircuitBreakerProps = Props(new CircuitBreaker(Props[TestEventLog], batching = true))

  class TestEventLog extends Actor {
    override def receive: Receive = Actor.emptyBehavior
  }
}
