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
import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.ReplicationConnection
import com.rbmhtechnology.eventuate.tools.logviewer.RemoteEventReader.readEventsAndDo
import com.typesafe.config.ConfigFactory

object LogViewer extends App {

  private val params: LogViewerParameters = parseCommandLineArgs(args)
  import params._

  private implicit val system: ActorSystem = ActorSystem(
    ReplicationConnection.DefaultRemoteSystemName,
    ConfigFactory.parseString(
      s"""
        |akka {
        |  remote.netty.tcp {
        |    hostname = "$localAddress"
        |    port = $localPort
        |  }
        |  actor.provider = akka.remote.RemoteActorRefProvider
        |  loglevel = WARNING
        |}
      """.stripMargin
    ).withFallback(ConfigFactory.load())
  )

  private val acceptor: ActorSelection = system.actorSelection(remoteActorPath(
    akkaProtocol(system),
    ReplicationConnection(remoteHost, remotePort, remoteSystemName),
    "acceptor"
  ))

  private val eventFormatter = new CaseClassFormatter[DurableEvent](eventFormatString)

  readEventsAndDo(acceptor, logName, fromSequenceNr, maxEvents, batchSize) {
    event => println(eventFormatter.format(event))
  } {
    _.printStackTrace()
  } {
    system.terminate()
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

  private def parseCommandLineArgs(args: Array[String]): LogViewerParameters = {
    val jCommander = new JCommander()
    jCommander.setProgramName("log-viewer")
    val params = new LogViewerParameters()
    jCommander.addObject(params)
    try {
      jCommander.parse(args: _*)
      if (params.help) {
        jCommander.usage()
        sys.exit()
      }
      params
    } catch {
      case ex: ParameterException =>
        println(ex.getMessage)
        jCommander.usage()
        sys.exit(1)
    }
  }
}
