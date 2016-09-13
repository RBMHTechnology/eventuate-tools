package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.ReplicationEndpoint

class ReplicationEndpointHealthMonitor(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: Option[String] = None) {
  private val actorMonitor = new ActorHealthMonitor(endpoint, healthRegistry, namePrefix)
  private val persistenceMonitor = new PersistenceHealthMonitor(endpoint, healthRegistry, namePrefix)
  private val replicationMonitor = new ReplicationHealthMonitor(endpoint, healthRegistry, namePrefix)

  /**
   * Stop monitoring [[ReplicationEndpoint]] and de-register health checks (asynchronously).
   */
  def stopMonitoring(): Unit = {
    actorMonitor.stopMonitoring()
    persistenceMonitor.stopMonitoring()
    replicationMonitor.stopMonitoring()
  }
}
