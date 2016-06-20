package com.rbmhtechnology.eventuate.tools.metrics.dropwizard

import java.util

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetEventLogClock
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetEventLogClockSuccess
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetReplicationProgresses
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetReplicationProgressesFailure
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetReplicationProgressesSuccess

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt
import scala.collection.JavaConverters.mapAsJavaMapConverter

/**
 * The replication state of a local log
 *
 * @param localSequenceNo the current sequence number of the local log
 * @param localVersionVector the version-vector of the local log. This vector is used to filter
 *                           events to be replicated to the local log. Past events (i.e.
 *                           [[com.rbmhtechnology.eventuate.DurableEvent.vectorTimestamp]] <= localVersionVector)
 *                           are not replicated into the local log.
 * @param replicationProgress maps from remote log-id to the sequence number in the remote log
 *                            up to which events have already been replicated into the local log.
 *                            To check if replication is _complete_ this number has to be compared
 *                            with the localSequenceNo of the corresponding log on the corresponding
 *                            remote mode.
 */
case class ReplicatedLogMetrics(localSequenceNo: Long, localVersionVector: Map[String, Long], replicationProgress: Map[String, Long]) {
  // follow bean convention to make the attributes accessible for jackson's ObjectMapper
  // that is used by dropwizard's MetricsServlet
  def getLocalSequenceNo = localSequenceNo
  private lazy val jReplicationProgress: util.Map[String, Long] = replicationProgress.asJava
  def getReplicationProgress = jReplicationProgress
  private lazy val jLocalVersionVector: util.Map[String, Long] = localVersionVector.asJava
  def getLocalVersionVector = jLocalVersionVector
}

/**
 * The replication state of a local [[ReplicationEndpoint]]
 *
 * @param replicatedLogsMetrics maps log-names to [[ReplicatedLogMetrics]]
 */
case class ReplicationEndpointMetrics(replicatedLogsMetrics: Map[String, ReplicatedLogMetrics]) {
  // follow bean convention to make the attributes accessible for dropwizard's metrics lib
  private lazy val jReplicatedLogsMetrics: util.Map[String, ReplicatedLogMetrics] =
    replicatedLogsMetrics.asJava
  def getReplicatedLogsMetrics = jReplicatedLogsMetrics
}

/**
 * A [[Gauge]] that can be registered with a [[MetricRegistry]] to retrieve
 * the [[ReplicationEndpointMetrics]] of a given [[ReplicationEndpoint]]
 *
 * @param monitoredEndpoint the [[ReplicationEndpoint]] to be monitored
 * @param timeoutDuration   timeout until a local event-log-actor has to respond to queries, otherwise
 *                          [[ReplicationEndpointGauge.getValue]] throws an exception
 */
class ReplicationEndpointGauge(monitoredEndpoint: ReplicationEndpoint, timeoutDuration: FiniteDuration = 3.seconds) extends Gauge[ReplicationEndpointMetrics] {
  import monitoredEndpoint.system.dispatcher
  implicit private val timeout = Timeout(timeoutDuration)

  override def getValue = Await.result(endpointMetrics(monitoredEndpoint), timeoutDuration)

  private def endpointMetrics(endpoint: ReplicationEndpoint): Future[ReplicationEndpointMetrics] = {
    Future.traverse(endpoint.logs) {
      case (logName, logActor) =>
        logMetrics(logActor).map(logName -> _)
    }.map(logReplicationStates => ReplicationEndpointMetrics(logReplicationStates.toMap))
  }

  private def logMetrics(logActor: ActorRef): Future[ReplicatedLogMetrics] = {
    val eventLogClock = (logActor ? GetEventLogClock).mapTo[GetEventLogClockSuccess].map(_.clock)
    val getReplicationProgress = (logActor ? GetReplicationProgresses).flatMap {
      case GetReplicationProgressesSuccess(progresses) => Future.successful(progresses)
      case GetReplicationProgressesFailure(ex)         => Future.failed(ex)
    }
    for {
      clock <- eventLogClock
      replicationProgress <- getReplicationProgress
    } yield ReplicatedLogMetrics(clock.sequenceNr, clock.versionVector.value, replicationProgress)
  }
}