package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import akka.actor.PoisonPill
import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.log.EventLog
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.ReplicationEndpoint.Available
import com.rbmhtechnology.eventuate.ReplicationEndpoint.Unavailable
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.AvailabilityMonitor.HealthRegistryName
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.AvailabilityMonitor.UnhealthyCause

/**
 * Monitors the replication connections of the [[EventLog]]s of the [[ReplicationEndpoint]]
 * to their remote source-logs.
 *
 * The registry name for the replication from a certain log: `replication-from.<remote-endpoint-id>.<log-name>`.
 * and it is optionally prefixed with `namePrefix.` if that is non-empty.
 */
class ReplicationHealthMonitor(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: Option[String] = None) {

  import ReplicationHealthMonitor._

  private implicit val availableHealthOps = new HealthRegistryName[Available] {
    override def healthRegistryName(available: Available): String = healthName(available.endpointId, available.logName)
  }

  private implicit val unavailableHealthOps = new HealthRegistryName[Unavailable] with UnhealthyCause[Unavailable] {
    override def healthRegistryName(unavailable: Unavailable): String = healthName(unavailable.endpointId, unavailable.logName)
    override def unhealthyCause(unavailable: Unavailable): Throwable = new ReplicationUnhealthyException(unavailable)
  }

  private val monitorActor =
    AvailabilityMonitor.monitorHealth[Available, Unavailable](endpoint.system, healthRegistry, namePrefix)

  /**
   * Stop monitoring replication health and de-register health checks (asynchronously).
   */
  def stopMonitoring(): Unit = monitorActor ! PoisonPill
}

object ReplicationHealthMonitor {
  /**
   * Returns the registry name (without prefix) under which a [[HealthCheck.Result]]
   * for the replication from a remote source-log is registered.
   *
   * @param endpointId the id of the remote [[ReplicationEndpoint]]
   * @param logName the name of the log (of the remote [[ReplicationEndpoint]]
   */
  def healthName(endpointId: String, logName: String): String = s"replication-from.$endpointId.$logName"

  /**
   * The exception of an unhealthy [[HealthCheck.Result]] for a broken replication connection.
   */
  class ReplicationUnhealthyException(endpointId: String, logName: String, causes: Seq[Throwable])
      extends IllegalStateException(
        s"Replication of log $logName to endpoint $endpointId failed for the following reasons: ${causes.mkString("\n- ", "\n- ", "\n")}",
        causes.headOption.orNull
      ) {
    def this(unavailable: Unavailable) = this(unavailable.endpointId, unavailable.logName, unavailable.causes)
  }
}
