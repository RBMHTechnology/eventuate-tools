package com.rbmhtechnology.eventuate.tools.metrics.dropwizard

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.ReceiveTimeout
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.MetricRegistry.name
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.ReplicationFilter
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetEventLogClock
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetEventLogClockSuccess
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetReplicationProgresses
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetReplicationProgressesFailure
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetReplicationProgressesSuccess
import com.rbmhtechnology.eventuate.ReplicationProtocol.ReplicationDue
import com.rbmhtechnology.eventuate.ReplicationProtocol.ReplicationRead
import com.rbmhtechnology.eventuate.VectorTime
import com.rbmhtechnology.eventuate.log.EventLogClock
import com.rbmhtechnology.eventuate.tools.metrics.dropwizard.MetricRegistries.RichMetricRegistry
import com.rbmhtechnology.eventuate.tools.metrics.dropwizard.DropwizardReplicationMetricsRecorder.replicationProgressName
import com.rbmhtechnology.eventuate.tools.metrics.dropwizard.DropwizardReplicationMetricsRecorder.sequenceNoName
import com.rbmhtechnology.eventuate.tools.metrics.dropwizard.DropwizardReplicationMetricsRecorder.versionVectorName

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt

/**
 * Provides metrics for a [[ReplicationEndpoint]] in form of dropwizard gauges.
 *
 * It registers for each event-log managed by the `endpoint` under the name `namePrefix`.`logId`
 * the following set of gauges:
 *
 * - `sequenceNo`: The sequence number of the local log.
 * - `localVersionVector.process-id`: The entire version vector of the local log.
 * - For each remote replica (`remote-log-id`) of the local log:
 *   - `replicationProgress.remote-log-id`: The replication progress in form of the sequence number
 *     in the remote log up to which events have been replicated to the local log.
 *
 * @param endpoint            the [[ReplicationEndpoint]] whose logs are monitored
 * @param metricRegistry      the [[MetricRegistry]] where the gauges are registered
 * @param namePrefix          the prefix of the entity-id metrics are recorded under (suffix is the log-id)
 * @param pollMetricsMinDelay The metrics are requested from an [[com.rbmhtechnology.eventuate.log.EventLog]]-actor each time
 *                            a corresponding update notification is sent by the event-log,
 *                            but only if at least the given time-span has passed after the last
 *                            request.
 */
class DropwizardReplicationMetricsRecorder(
    endpoint: ReplicationEndpoint,
    metricRegistry: MetricRegistry,
    namePrefix: Option[String] = None,
    pollMetricsMinDelay: FiniteDuration = 1.second
) {

  private val recorderActors: Map[String, ActorRef] =
    endpoint.logs.map {
      case (logName, logActor) =>
        val logId = endpoint.logId(logName)
        logName -> endpoint.system.actorOf(
          DropwizardEventLogMetricsRecorder.props(metricRegistry, prefixed(logId), logActor, pollMetricsMinDelay),
          s"EventLogMetricRecorder_$logId"
        )
    }

  /**
   * Stop recording of metrics.
   */
  def stopRecording(): Unit =
    recorderActors.values.foreach(_ ! PoisonPill)

  private def prefixed(suffix: String): String =
    namePrefix.map(name(_, suffix)).getOrElse(suffix)
}

object DropwizardReplicationMetricsRecorder {

  /**
   * Java API for creating a [[DropwizardReplicationMetricsRecorder]]
   */
  def create(endpoint: ReplicationEndpoint, metricRegistry: MetricRegistry): DropwizardReplicationMetricsRecorder =
    new DropwizardReplicationMetricsRecorder(endpoint, metricRegistry)

  /**
   * Java API for creating a [[DropwizardReplicationMetricsRecorder]]
   */
  def create(endpoint: ReplicationEndpoint, metricRegistry: MetricRegistry, namePrefix: String): DropwizardReplicationMetricsRecorder =
    new DropwizardReplicationMetricsRecorder(endpoint, metricRegistry, Some(namePrefix))

  /**
   * Java API for creating a [[DropwizardReplicationMetricsRecorder]]
   */
  def create(endpoint: ReplicationEndpoint, metricRegistry: MetricRegistry, pollMetricsMinDelay: FiniteDuration): DropwizardReplicationMetricsRecorder =
    new DropwizardReplicationMetricsRecorder(endpoint, metricRegistry, None, pollMetricsMinDelay)

  /**
   * Java API for creating a [[DropwizardReplicationMetricsRecorder]]
   */
  def create(endpoint: ReplicationEndpoint, metricRegistry: MetricRegistry, namePrefix: String, pollMetricsMinDelay: FiniteDuration): DropwizardReplicationMetricsRecorder =
    new DropwizardReplicationMetricsRecorder(endpoint, metricRegistry, Some(namePrefix), pollMetricsMinDelay)

  /** The name of the sequence number metric */
  val sequenceNoName = "sequenceNo"
  /** The name of the version vector metric for `processId`. */
  def versionVectorName(processId: String) = name("localVersionVector", processId)
  /** The name of the replication progress metrics for the remote log with id `logId`. */
  def replicationProgressName(logId: String) = name("replicationProgress", logId)
}

private class DropwizardEventLogMetricsRecorder private (metricRegistry: MetricRegistry, namePrefix: String, logActor: ActorRef, pollMetricsMinDelay: FiniteDuration) extends Actor {

  private var lastMetricsUpdate = 0L

  override def preStart(): Unit = {
    super.preStart()
    subscribeToReplicationUpdates(logActor)
    context.setReceiveTimeout(pollMetricsMinDelay)
  }

  override def receive: Receive = receiveNotification

  private def receiveNotification: Receive = {
    case _@ (ReplicationDue | ReceiveTimeout) =>
      val now = System.nanoTime()
      if (pollDelayExceeded(now)) {
        lastMetricsUpdate = now
        requestMetrics(logActor)
      }
  }

  private def receiveMetrics(receivedClock: Boolean = false, receivedReplicationProgress: Boolean = false): Receive = {

    case GetEventLogClockSuccess(EventLogClock(seqNo, versionVector)) =>
      metricRegistry.setGaugeValue(name(namePrefix, sequenceNoName), seqNo)
      versionVector.value.foreach {
        case (processId, time) =>
          metricRegistry.setGaugeValue(name(namePrefix, versionVectorName(processId)), time)
      }
      switchContext(receivedClock = true, receivedReplicationProgress)

    case GetReplicationProgressesSuccess(progresses) =>
      progresses.foreach {
        case (remoteLogId, progress) =>
          metricRegistry.setGaugeValue(name(namePrefix, replicationProgressName(remoteLogId)), progress)
      }
      switchContext(receivedClock, receivedReplicationProgress = true)

    case GetReplicationProgressesFailure(ex) =>
      switchContext(receivedClock, receivedReplicationProgress = true)
  }

  private def subscribeToReplicationUpdates(logActor: ActorRef): Unit = {
    logActor ! ReplicationRead(0, 0, 0, ReplicationFilter.NoFilter, s"MetricsRecorder_$namePrefix", self, VectorTime.Zero)
  }

  private def pollDelayExceeded(now: Long): Boolean =
    now - lastMetricsUpdate > pollMetricsMinDelay.toNanos

  private def requestMetrics(logActor: ActorRef): Unit = {
    logActor ! GetEventLogClock
    logActor ! GetReplicationProgresses
    context.become(receiveMetrics())
  }

  private def switchContext(receivedClock: Boolean, receivedReplicationProgress: Boolean): Unit = {
    val nextReceive = if (receivedClock && receivedReplicationProgress)
      receiveNotification
    else
      receiveMetrics(receivedClock, receivedReplicationProgress)
    context.become(nextReceive)
  }
}

private object DropwizardEventLogMetricsRecorder {
  def props(metricRegistry: MetricRegistry, namePrefix: String, logActor: ActorRef, pollMetricsMinDelay: FiniteDuration) =
    Props(new DropwizardEventLogMetricsRecorder(metricRegistry, namePrefix, logActor, pollMetricsMinDelay))
}
