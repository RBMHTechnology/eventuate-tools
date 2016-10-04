package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import akka.actor.PoisonPill
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

/**
 * Monitors the connections of the [[ReplicationEndpoint]]
 * [[EventLog]]s to its persistence backend. The [[EventLog]] has to use a [[CircuitBreaker]] to
 * enable monitoring.
 *
 * The registry name for an [[EventLog]] is: `persistence-of.<log-id>`.
 * and it is optionally prefixed with `namePrefix.` if that is non-empty.
 */
class PersistenceHealthMonitor(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: Option[String] = None) {

  import PersistenceHealthMonitor._

  private implicit val availableHealthOps = new HealthRegistryName[ServiceNormal] {
    override def healthRegistryName(available: ServiceNormal): String = healthName(available.logId)
  }

  private implicit val unavailableHealthOps = new HealthRegistryName[ServiceFailed] with UnhealthyCause[ServiceFailed] {
    override def healthRegistryName(unavailable: ServiceFailed): String = healthName(unavailable.logId)
    override def unhealthyCause(unavailable: ServiceFailed): Throwable = unavailable.cause
  }

  private val monitorActor =
    monitorHealth[ServiceNormal, ServiceFailed](endpoint.system, healthRegistry, UnknownPersistenceStateException, namePrefix)

  /**
   * Stop monitoring persistence health and de-register health checks (asynchronously).
   */
  def stopMonitoring(): Unit = monitorActor ! PoisonPill
}

object PersistenceHealthMonitor {
  /**
   * Returns the registry name (without prefix) under which a [[HealthCheck.Result]]
   * for the connection to an [[EventLog]]'s persistence backend is registered.
   */
  def healthName(logId: String): String = s"persistence-of.$logId"

  object UnknownPersistenceStateException extends MonitorActorStoppedPrematurelyException("Persistence")
}
