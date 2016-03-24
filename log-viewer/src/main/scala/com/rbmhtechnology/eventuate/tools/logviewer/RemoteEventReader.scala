/*
 * Copyright 2016 Red Bull Media House GmbH <http://www.redbullmediahouse.com> - all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rbmhtechnology.eventuate.tools.logviewer

import akka.actor.ActorSelection
import akka.actor.ActorSystem
import akka.util.Timeout
import akka.pattern.ask
import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.ReplicationFilter.NoFilter
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetReplicationEndpointInfo
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetReplicationEndpointInfoSuccess
import com.rbmhtechnology.eventuate.ReplicationProtocol.ReplicationEndpointInfo
import com.rbmhtechnology.eventuate.ReplicationProtocol.ReplicationRead
import com.rbmhtechnology.eventuate.ReplicationProtocol.ReplicationReadEnvelope
import com.rbmhtechnology.eventuate.ReplicationProtocol.ReplicationReadSuccess
import com.rbmhtechnology.eventuate.{ ApplicationVersion, VectorTime }

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.Exception.handling
import scala.concurrent.duration.DurationInt

object RemoteEventReader {
  implicit val timeout = Timeout(15.seconds)

  def readEventsAndDo(
    acceptor: ActorSelection,
    logName: String,
    fromSequenceNo: Long,
    maxEvents: Long,
    batchSize: Int
  )(handleEvent: DurableEvent => Unit)(handleFailure: Throwable => Unit)(terminate: => Unit)(implicit system: ActorSystem): Unit = {

    import system.dispatcher

    def replicate(fromSeqNo: Long, replicationCnt: Long): Unit = {
      val replicationCommand = new ReplicationReadEnvelope(
        new ReplicationRead(
          fromSeqNo,
          batchSize.toLong.min(maxEvents - replicationCnt).toInt,
          NoFilter, "", system.deadLetters, new VectorTime()
        ),
        logName,
        s"${LogViewerBuildInfo.organization}.${LogViewerBuildInfo.name}",
        ApplicationVersion(LogViewerBuildInfo.version.takeWhile(c => c.isDigit || c == '.'))
      )
      (acceptor ? replicationCommand).onComplete(handleResponse(replicationCnt))
    }

    def handleResponse(replicationCnt: Long): PartialFunction[Try[Any], Unit] = {
      case Success(ReplicationReadSuccess(events, progresss, _, _)) =>
        handling(classOf[Exception]).by(handleFailure.andThen(_ => terminate))(events.foreach(handleEvent))
        val newReplicationCnt = replicationCnt + events.size
        if (newReplicationCnt < maxEvents && events.nonEmpty)
          replicate(progresss + 1, newReplicationCnt)
        else
          terminate
      case Failure(ex) =>
        handleFailure(ex)
        terminate
      case response =>
        handleFailure(new IllegalStateException(s"Unhandled response: $response"))
        terminate
    }

    readReplicationEndpointInfo(acceptor).onComplete {
      case Success(info) if info.logNames.contains(logName) =>
        replicate(fromSequenceNo, 0)
      case Success(info) =>
        handleFailure(new IllegalArgumentException(s"Unknown log-name $logName, known: ${info.logNames.mkString(",")}"))
        terminate
      case Failure(ex) =>
        handleFailure(ex)
        terminate
    }
  }

  private def readReplicationEndpointInfo(acceptor: ActorSelection)(implicit system: ActorSystem): Future[ReplicationEndpointInfo] = {
    import system.dispatcher
    (acceptor ? GetReplicationEndpointInfo).mapTo[GetReplicationEndpointInfoSuccess].map(_.info)
  }
}
