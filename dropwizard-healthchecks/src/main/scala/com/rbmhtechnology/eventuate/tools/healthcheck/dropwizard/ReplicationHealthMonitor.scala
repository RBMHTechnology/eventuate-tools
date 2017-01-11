package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.log.EventLog
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.ReplicationEndpoint.Available
import com.rbmhtechnology.eventuate.ReplicationEndpoint.Unavailable
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.AvailabilityMonitor.HealthRegistryName
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.AvailabilityMonitor.UnhealthyCause
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.HealthCheckRegistries.RichHealthCheckRegistry
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.StoppableHealthMonitorActor.MonitorActorStoppedPrematurelyException
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.StoppableHealthMonitorActor.StopMonitoring

import scala.collection.JavaConverters._

/**
 * Identifies a log
 */
case class LogId(endpointId: String, logName: String)

/**
 * Monitors the replication connections of the [[EventLog]]s of the [[ReplicationEndpoint]]
 * to their remote source-logs.
 *
 * The registry name for the replication from a certain log: `replication-from.<remote-endpoint-id>.<log-name>`.
 * and it is optionally prefixed with `namePrefix.` if that is non-empty.
 * All remote logs given in `initiallyUnhealthy` are initially reported
 * as unhealthy with cause [[ReplicationHealthMonitor#ReplicationConnectionNotYetEstablishedException]]
 * until the first [[Available]] or [[Unavailable]] is received.
 * For all other remote logs (not given in `initiallyUnhealthy`) no health state is reported until the first
 * [[Available]] or [[Unavailable]] is received. This means that no health state is reported
 * if no connection can be established to these logs (e.g. because the remote host is down when connecting).
 */
class ReplicationHealthMonitor(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: Option[String] = None, initiallyUnhealthy: Set[LogId] = Set.empty) {

  import ReplicationHealthMonitor._

  private implicit val availableHealthOps = new HealthRegistryName[Available] {
    override def healthRegistryName(available: Available): String = healthName(available.endpointId, available.logName)
  }

  private implicit val unavailableHealthOps = new HealthRegistryName[Unavailable] with UnhealthyCause[Unavailable] {
    override def healthRegistryName(unavailable: Unavailable): String = healthName(unavailable.endpointId, unavailable.logName)
    override def unhealthyCause(unavailable: Unavailable): Throwable = new ReplicationUnhealthyException(unavailable)
  }

  initiallyUnhealthy.foreach { logId =>
    healthRegistry.registerUnhealthy(
      healthName(logId.endpointId, logId.logName),
      new ReplicationConnectionNotYetEstablishedException(logId.endpointId, logId.logName)
    )
  }

  private val monitorActor =
    AvailabilityMonitor.monitorHealth[Available, Unavailable](endpoint.system, healthRegistry, UnknownReplicationStateException, namePrefix)

  /**
   * Stop monitoring replication health and de-register health checks (asynchronously).
   */
  def stopMonitoring(): Unit = monitorActor ! StopMonitoring
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
        s"Replication of log $logName from endpoint $endpointId failed for the following reasons: ${causes.mkString("\n- ", "\n- ", "\n")}",
        causes.headOption.orNull
      ) {
    def this(unavailable: Unavailable) = this(unavailable.endpointId, unavailable.logName, unavailable.causes)
  }

  /**
   * Exception of an unhealthy [[HealthCheck.Result]] that is reported for those logs that are explicitly
   * specified when constructing an [[ReplicationHealthMonitor]] until the first replication
   * read request to this log was successful.
   */
  class ReplicationConnectionNotYetEstablishedException(endpointId: String, logName: String)
    extends IllegalStateException(s"Replication of log $logName from endpoint $endpointId is not yet established")

  /**
   * The exception that is reported if the monitoring actor stops prematurely
   * (without being stopped through [[ReplicationHealthMonitor.stopMonitoring()]]).
   */
  object UnknownReplicationStateException extends MonitorActorStoppedPrematurelyException("Replication")

  /**
   * Create a [[ReplicationHealthMonitor]] with the given parameters.
   */
  def create(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: String) =
    new ReplicationHealthMonitor(endpoint, healthRegistry, Option(namePrefix))

  /**
   * Create a [[ReplicationHealthMonitor]] with the given parameters.
   */
  def create(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry) =
    new ReplicationHealthMonitor(endpoint, healthRegistry)

  /**
   * Create a [[ReplicationHealthMonitor]] with the given parameters.
   */
  def create(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: String, initiallyUnhealthy: java.util.Set[LogId]) =
    new ReplicationHealthMonitor(endpoint, healthRegistry, Option(namePrefix), initiallyUnhealthy.asScala.toSet)

  /**
   * Create a [[ReplicationHealthMonitor]] with the given parameters.
   */
  def create(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, initiallyUnhealthy: java.util.Set[LogId]) =
    new ReplicationHealthMonitor(endpoint, healthRegistry, initiallyUnhealthy = initiallyUnhealthy.asScala.toSet)
}
