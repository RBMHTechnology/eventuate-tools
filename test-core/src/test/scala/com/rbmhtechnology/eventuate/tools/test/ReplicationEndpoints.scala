package com.rbmhtechnology.eventuate.tools.test

import akka.actor.ActorPath
import akka.actor.ActorSelection
import akka.actor.ActorSystem
import com.rbmhtechnology.eventuate.ReplicationConnection
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.ReplicationEndpoint._
import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.akkaAddress
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.akkaRemotingConfig
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.withActorSystem
import com.rbmhtechnology.eventuate.tools.test.EventLogs.withLevelDbLogConfig

object ReplicationEndpoints {
  def withLevelDbReplicationEndpoint[A](
    logNames: Set[String] = Set(DefaultLogName),
    connections: Set[ReplicationConnection] = Set.empty
  )(f: ReplicationEndpoint => A): A = {

    withLevelDbLogConfig { config =>
      withActorSystem(config.withFallback(akkaRemotingConfig)) { implicit system =>
        val endpoint = replicationEndpoint()
        f(endpoint)
      }
    }
  }

  private def replicationEndpoint(logNames: Set[String] = Set(DefaultLogName), connections: Set[ReplicationConnection] = Set.empty)(implicit system: ActorSystem): ReplicationEndpoint = {
    val endpoint = new ReplicationEndpoint(system.name, logNames, LeveldbEventLog.props(_), connections)
    endpoint.activate()
    endpoint
  }

  def acceptorOf(system: ActorSystem): ActorSelection = {
    system.actorSelection(remoteActorPath(
      akkaAddress(system).protocol,
      replicationConnectionFor(system),
      "acceptor"
    ))
  }

  private def remoteActorPath(protocol: String, connectionInfo: ReplicationConnection, actorName: String): ActorPath =
    ActorPath.fromString(s"$protocol://${connectionInfo.name}@${connectionInfo.host}:${connectionInfo.port}/user/$actorName")

  def replicationConnectionFor(system: ActorSystem): ReplicationConnection = {
    val address = akkaAddress(system)
    ReplicationConnection(address.host.get, address.port.get, system.name)
  }

}
