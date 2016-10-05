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

import akka.actor.ActorPath
import akka.actor.ActorSelection
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.util.Timeout
import akka.pattern.ask
import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.ReplicationConnection
import com.rbmhtechnology.eventuate.ReplicationFilter.NoFilter
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetReplicationEndpointInfo
import com.rbmhtechnology.eventuate.ReplicationProtocol.GetReplicationEndpointInfoSuccess
import com.rbmhtechnology.eventuate.ReplicationProtocol.ReplicationEndpointInfo
import com.rbmhtechnology.eventuate.ReplicationProtocol.ReplicationRead
import com.rbmhtechnology.eventuate.ReplicationProtocol.ReplicationReadEnvelope
import com.rbmhtechnology.eventuate.ReplicationProtocol.ReplicationReadSuccess
import com.rbmhtechnology.eventuate.{ ApplicationVersion, VectorTime }
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.Exception.handling
import scala.concurrent.duration.DurationInt

object RemoteEventReader {
  implicit val timeout = Timeout(15.seconds)

  /**
   * Returns an [[ActorSystem]] that binds locally to the given address and port.
   * @param address the address to bind to. If empty `InetAddress.getLocalHost.getHostAddress` is used.
   * @param port the local port to bind to. 0 means a random available port
   */
  def actorSystemBindingTo(address: String, port: Int) =
    ActorSystem(
      ReplicationConnection.DefaultRemoteSystemName,
      ConfigFactory.parseString(
        s"""
           |akka {
           |  remote.netty.tcp {
           |    hostname = "$address"
           |    port = $port
           |  }
           |  actor.provider = akka.remote.RemoteActorRefProvider
           |  loglevel = WARNING
           |}
      """.stripMargin
      ).withFallback(ConfigFactory.load())
    )

  /**
   * Returns the [[ActorSelection]] of the acceptor of the remote Eventuate based system
   * with the given address details.
   */
  def acceptorOf(remoteHost: String, remotePort: Int, remoteSystemName: String)(implicit system: ActorSystem): ActorSelection =
    system.actorSelection(remoteActorPath(
      akkaProtocol(system),
      ReplicationConnection(remoteHost, remotePort, remoteSystemName),
      "acceptor"
    ))

  /**
   * Requests `maxEvents` of the remote log with name `logName` from `fromSequenceNo` on from the remote `acceptor`.
   * Events are requested in batches of size `batchSize` respecting the given `scanLimit`
   */
  def readEventsAndDo(
    acceptor: ActorSelection,
    logName: String,
    fromSequenceNo: Long,
    maxEvents: Long,
    batchSize: Int,
    scanLimit: Int
  )(handleEvent: DurableEvent => Unit)(handleFailure: Throwable => Unit)(terminate: => Unit)(implicit system: ActorSystem): Unit = {

    import system.dispatcher

    val handleFailureAndTerminate = handleFailure.andThen(_ => terminate)

    def replicate(fromSeqNo: Long, replicationCnt: Long): Unit = {
      val cappedBatchSize = batchSize.toLong.min(maxEvents - replicationCnt).toInt
      val replicationCommand = replicationReadRequest(logName, fromSeqNo, cappedBatchSize, scanLimit)
      (acceptor ? replicationCommand).onComplete(handleResponse(replicationCnt))
    }

    def handleResponse(replicationCnt: Long): PartialFunction[Try[Any], Unit] = {
      case Success(ReplicationReadSuccess(events, fromSequenceNr, progresss, _, _)) =>
        handling(classOf[Exception]).by(handleFailureAndTerminate)(events.foreach(handleEvent))
        val newReplicationCnt = replicationCnt + events.size
        if (newReplicationCnt < maxEvents && progresss >= fromSequenceNr)
          replicate(progresss + 1, newReplicationCnt)
        else
          terminate

      case Failure(ex) => handleFailureAndTerminate(ex)
      case response    => handleFailureAndTerminate(new IllegalStateException(s"Unhandled response: $response"))
    }

    readReplicationEndpointInfo(acceptor).onComplete {
      case Success(info) if info.logNames.contains(logName) => replicate(fromSequenceNo, 0)
      case Success(info)                                    => handleFailureAndTerminate(new UnknownLogNameException(logName, info.logNames))
      case Failure(ex)                                      => handleFailureAndTerminate(ex)
    }
  }

  class UnknownLogNameException(logName: String, knownLogNames: Set[String])
    extends IllegalArgumentException(s"Unknown log-name $logName, known: ${knownLogNames.mkString(",")}")

  private def replicationReadRequest(logName: String, fromSeqNo: Long, cappedBatchSize: Int, scanLimit: Int)(implicit system: ActorSystem): Any = {
    ReplicationReadEnvelope(
      ReplicationRead(
        fromSeqNo,
        cappedBatchSize,
        scanLimit,
        NoFilter, "", system.deadLetters, VectorTime.Zero
      ),
      logName,
      s"${LogViewerBuildInfo.organization}.${LogViewerBuildInfo.name}",
      ApplicationVersion(LogViewerBuildInfo.version.takeWhile(c => c.isDigit || c == '.'))
    )
  }

  private def readReplicationEndpointInfo(acceptor: ActorSelection)(implicit system: ActorSystem): Future[ReplicationEndpointInfo] = {
    import system.dispatcher
    (acceptor ? GetReplicationEndpointInfo).mapTo[GetReplicationEndpointInfoSuccess].map(_.info)
  }

  private[logviewer] def remoteActorPath(protocol: String, connectionInfo: ReplicationConnection, actorName: String): ActorPath =
    ActorPath.fromString(s"$protocol://${connectionInfo.name}@${connectionInfo.host}:${connectionInfo.port}/user/$actorName")

  /**
   * Return the protocol used by the given ActorSystem,
   * if the ActorSystem is an ExtendedActorSystem and "akka.tcp" as default otherwise.
   */
  private[logviewer] def akkaProtocol(system: ActorSystem): String = system match {
    case sys: ExtendedActorSystem => sys.provider.getDefaultAddress.protocol
    case _                        => "akka.tcp"
  }
}
