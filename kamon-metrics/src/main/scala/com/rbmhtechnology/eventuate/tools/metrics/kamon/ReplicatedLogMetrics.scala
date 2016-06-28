package com.rbmhtechnology.eventuate.tools.metrics.kamon

import kamon.metric.EntityRecorderFactory
import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.InstrumentFactory

/**
 * Kamon-Entity recorder for metrics of a replicated log.
 */
class ReplicatedLogMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  import ReplicatedLogMetrics._

  private val sequenceNo = histogram(sequenceNoName)
  private def versionVector(processId: String) = histogram(versionVectorName(processId))
  private def replicationProgress(logId: String) = histogram(replicationProgressName(logId))

  /**
   * Record the current local sequence no of the monitored log.
   */
  def recordLocalSequenceNo(seqNo: Long): Unit =
    sequenceNo.record(seqNo)

  /**
   * Record the current version vector of the monitored log.
   *
   * @param currentVector a map from ''processId'' to ''logical time''.
   */
  def recordVersionVector(currentVector: Map[String, Long]): Unit =
    currentVector.foreach { case (processId, time) => versionVector(processId).record(time) }

  /**
   * Record the sequence no `seqNo` in the remote log `logId` up to which events have already been
   * replicated into the local log. To check if replication is ''complete'' this number has to be compared
   * with the localSequenceNo of the corresponding log on the corresponding remote mode.
   */
  def recordReplicationProgress(logId: String, seqNo: Long): Unit =
    replicationProgress(logId).record(seqNo)
}

/**
 * [[EntityRecorderFactory]] for [[ReplicatedLogMetrics]]
 */
object ReplicatedLogMetrics extends EntityRecorderFactory[ReplicatedLogMetrics] {
  override def category = "eventuate-replicated-log"
  def sequenceNoName = "sequenceNo"
  def versionVectorName(processId: String) = s"localVersionVector.$processId"
  def replicationProgressName(logId: String) = s"replicationProgress.$logId"

  override def createRecorder(instrumentFactory: InstrumentFactory) =
    new ReplicatedLogMetrics(instrumentFactory)
}
