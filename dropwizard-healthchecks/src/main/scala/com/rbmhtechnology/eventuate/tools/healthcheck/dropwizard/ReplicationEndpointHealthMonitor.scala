package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.ReplicationEndpoint

/**
 * Combines [[ActorHealthMonitor]], [[CircuitBreakerHealthMonitor]], [[ReplicationHealthMonitor]] to monitor
 * all components of a [[ReplicationEndpoint]].
 */
class ReplicationEndpointHealthMonitor(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: Option[String] = None) {
  private val actorMonitor = new ActorHealthMonitor(endpoint, healthRegistry, namePrefix)
  private val circuitBreakerMonitor = new CircuitBreakerHealthMonitor(endpoint, healthRegistry, namePrefix)
  private val replicationMonitor = new ReplicationHealthMonitor(endpoint, healthRegistry, namePrefix)

  /**
   * Stop monitoring [[ReplicationEndpoint]] and de-register health checks (asynchronously).
   */
  def stopMonitoring(): Unit = {
    actorMonitor.stopMonitoring()
    circuitBreakerMonitor.stopMonitoring()
    replicationMonitor.stopMonitoring()
  }
}

object ReplicationEndpointHealthMonitor {

  /**
   * Create a [[ReplicationEndpointHealthMonitor]] with the given parameters.
   */
  def create(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: String) =
    new ReplicationEndpointHealthMonitor(endpoint, healthRegistry, Option(namePrefix))

  /**
   * Create a [[ReplicationEndpointHealthMonitor]] with the given parameters.
   */
  def create(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry) =
    new ReplicationEndpointHealthMonitor(endpoint, healthRegistry)
}
