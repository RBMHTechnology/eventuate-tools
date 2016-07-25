package com.rbmhtechnology.eventuate.tools.metrics.kamon

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.ReceiveTimeout
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
import com.rbmhtechnology.eventuate.log.EventLog
import com.rbmhtechnology.eventuate.log.EventLogClock
import com.rbmhtechnology.eventuate.tools.metrics.kamon.KamonLogMetrics.KamonLogSettings
import com.typesafe.config.Config
import kamon.Kamon

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.DurationLong
import scala.concurrent.duration.FiniteDuration

/**
 * Provides metrics for a [[ReplicationEndpoint]] in form of kamon-histograms.
 *
 * It will record for each event-log managed by the `monitoredEndpoint` under
 *
 * - the name `prefix` `logId` and
 * - the category `eventuate-replicated-log`
 *
 * the following set of histograms:
 *
 * - `sequenceNo`: The sequence number of the local log.
 * - `localVersionVector.process-id`: The entire version vector of the local log.
 * - For each remote replica (`remote-log.id`) of the local log:
 *   - `replicationProgress.remote-log-id`: The replication progress in form of the sequence number
 *     in the remote log up to which events have been replicated to the local log.
 *
 * As each one of the recorded values is only increasing the max-value of the histogram also represents
 * the latest recorded value.
 *
 * @param monitoredEndpoint the [[ReplicationEndpoint]] whose logs are monitored
 * @param entityNamePrefix the prefix of the entity-id metrics are recorded under (suffix is the log-id)
 * @param pollMetricsMinDelay The metrics are requested from an [[EventLog]]-actor each time
 *                            a corresponding update notification is sent by the event-log,
 *                            but only if at least the given time-span has passed after the last
 *                            request.
 */
class KamonReplicationEndpointMetrics(
    monitoredEndpoint: ReplicationEndpoint,
    entityNamePrefix: Option[String] = None,
    pollMetricsMinDelay: FiniteDuration = 1.second
) {

  private val monitorActors: Map[String, ActorRef] =
    monitoredEndpoint.logs.map {
      case (logName, logActor) =>
        val logId = monitoredEndpoint.logId(logName)
        logName -> monitoredEndpoint.system.actorOf(
          KamonLogMetrics.props(prefixed(logId), logActor, pollMetricsMinDelay),
          s"KamonMonitor_$logId"
        )
    }

  /**
   * Stop recording of metrics.
   */
  def stopRecording(): Unit =
    monitorActors.values.foreach(_ ! PoisonPill)

  private def prefixed(suffix: String): String =
    s"${entityNamePrefix.getOrElse("")}$suffix"
}

class KamonLogMetrics private (logId: String, logActor: ActorRef, pollMetricsMinDelay: FiniteDuration) extends Actor {

  private val EmptyVector = new VectorTime()
  private val settings = new KamonLogSettings(context.system.settings.config)

  private var lastMetricsUpdate = 0L

  override def preStart(): Unit = {
    super.preStart()
    subscribeToReplicationUpdates(logActor)
    context.setReceiveTimeout(settings.receiveTimeout)
  }

  override def receive: Receive = receiveNotification

  private def receiveNotification: Receive = {
    case m @ (ReceiveTimeout | ReplicationDue) =>
      val now = System.nanoTime()
      if (pollDelayExceeded(now)) {
        lastMetricsUpdate = now
        requestMetrics(logActor)
      }
  }

  private def receiveMetrics(receivedClock: Boolean = false, receivedReplicationProgress: Boolean = false): Receive = {

    case GetEventLogClockSuccess(EventLogClock(seqNo, versionVector)) =>
      val replicatedLogMetrics = Kamon.metrics.entity(ReplicatedLogMetrics, logId)
      replicatedLogMetrics.recordLocalSequenceNo(seqNo)
      replicatedLogMetrics.recordVersionVector(versionVector.value)
      switchContext(receivedClock = true, receivedReplicationProgress)

    case GetReplicationProgressesSuccess(progresses) =>
      val replicatedLogMetrics = Kamon.metrics.entity(ReplicatedLogMetrics, logId)
      progresses.foreach {
        case (remoteLogId, progress) =>
          replicatedLogMetrics.recordReplicationProgress(remoteLogId, progress)
      }
      switchContext(receivedClock, receivedReplicationProgress = true)

    case GetReplicationProgressesFailure(ex) =>
      switchContext(receivedClock, receivedReplicationProgress = true)
  }

  private def subscribeToReplicationUpdates(logActor: ActorRef): Unit = {
    logActor ! ReplicationRead(0, 0, 0, ReplicationFilter.NoFilter, s"KamonMetrics_$logId", self, EmptyVector)
  }

  private def pollDelayExceeded(now: Long): Boolean =
    now - lastMetricsUpdate > (pollMetricsMinDelay min settings.receiveTimeout).toNanos

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

object KamonLogMetrics {

  class KamonLogSettings(config: Config) {
    val receiveTimeout =
      ((config.getDuration("kamon.metric.tick-interval", TimeUnit.MILLISECONDS) * 8 / 10) max 1).millis
  }

  def props(entityId: String, logActor: ActorRef, pollMetricsMinDelay: FiniteDuration) =
    Props(new KamonLogMetrics(entityId, logActor, pollMetricsMinDelay))
}
