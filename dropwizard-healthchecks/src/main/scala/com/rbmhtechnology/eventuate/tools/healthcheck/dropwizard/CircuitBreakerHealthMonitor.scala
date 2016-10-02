package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.log.CircuitBreaker
import com.rbmhtechnology.eventuate.log.EventLog
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.log.CircuitBreaker.ServiceFailed
import com.rbmhtechnology.eventuate.log.CircuitBreaker.ServiceNormal
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.AvailabilityMonitor.HealthRegistryName
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.AvailabilityMonitor.UnhealthyCause
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.AvailabilityMonitor.monitorHealth
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.StoppableHealthMonitorActor.MonitorActorStoppedPrematurelyException
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.StoppableHealthMonitorActor.StopMonitoring

/**
 * Monitors the state of the [[CircuitBreaker]] used by [[EventLog]]s of the [[ReplicationEndpoint]].
 * If the event log does not use a [[CircuitBreaker]] healthy is always reported.
 *
 * The registry name for an [[EventLog]] is: `circuit-breaker-of.<log-id>`.
 * and it is optionally prefixed with `namePrefix.` if that is non-empty.
 *
 * @param endpoint the [[ReplicationEndpoint]] whose [[EventLog]] are monitored
 * @param healthRegistry the [[HealthCheckRegistry]] where health checks are registered
 * @param namePrefix an optional name prefix for the registry names
 * @param initiallyHealthy `true` when the [[CircuitBreaker]] should be reported as healthy initially. Otherwise it is first reported when
 *                        the state of the [[CircuitBreaker]] changes after the monitoring has started. Set this to `false` when the
 *                        [[CircuitBreakerHealthMonitor]] is initialized after application specific [[com.rbmhtechnology.eventuate.EventsourcedActor]]s or
 *                        [[com.rbmhtechnology.eventuate.EventsourcedProcessor]]s have been started.
 */
class CircuitBreakerHealthMonitor(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: Option[String] = None, initiallyHealthy: Boolean = true) {

  import CircuitBreakerHealthMonitor._

  private implicit val availableHealthOps = new HealthRegistryName[ServiceNormal] {
    override def healthRegistryName(available: ServiceNormal): String = healthName(available.logId)
  }

  private implicit val unavailableHealthOps = new HealthRegistryName[ServiceFailed] with UnhealthyCause[ServiceFailed] {
    override def healthRegistryName(unavailable: ServiceFailed): String = healthName(unavailable.logId)
    override def unhealthyCause(unavailable: ServiceFailed): Throwable = unavailable.cause
  }

  private val monitorActor =
    monitorHealth[ServiceNormal, ServiceFailed](endpoint.system, healthRegistry, UnknownCircuitBreakerStateException, namePrefix)
  if (initiallyHealthy)
    endpoint.logNames.foreach(logName => monitorActor ! ServiceNormal(endpoint.logId(logName)))

  /**
   * Stop monitoring circuit breaker health and de-register health checks (asynchronously).
   */
  def stopMonitoring(): Unit = monitorActor ! StopMonitoring
}

object CircuitBreakerHealthMonitor {
  /**
   * Returns the registry name (without prefix) under which a [[HealthCheck.Result]]
   * for the circuit breaker is registered.
   */
  def healthName(logId: String): String = s"circuit-breaker-of.$logId"

  /**
   * The exception that is reported if the monitoring actor stops prematurely
   * (without being stopped through [[CircuitBreakerHealthMonitor.stopMonitoring()]]).
   */
  object UnknownCircuitBreakerStateException extends MonitorActorStoppedPrematurelyException("Circuit Breaker")

  /**
   * Create a [[CircuitBreakerHealthMonitor]] with the given parameters.
   */
  def create(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: String, initiallyHealthy: Boolean) =
    new CircuitBreakerHealthMonitor(endpoint, healthRegistry, Option(namePrefix), initiallyHealthy)

  /**
   * Create a [[CircuitBreakerHealthMonitor]] with the given parameters.
   */
  def create(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, initiallyHealthy: Boolean) =
    new CircuitBreakerHealthMonitor(endpoint, healthRegistry, initiallyHealthy = initiallyHealthy)

  /**
   * Create a [[CircuitBreakerHealthMonitor]] with the given parameters.
   */
  def create(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: String) =
    new CircuitBreakerHealthMonitor(endpoint, healthRegistry, Option(namePrefix))

  /**
   * Create a [[CircuitBreakerHealthMonitor]] with the given parameters.
   */
  def create(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry) =
    new CircuitBreakerHealthMonitor(endpoint, healthRegistry)
}
