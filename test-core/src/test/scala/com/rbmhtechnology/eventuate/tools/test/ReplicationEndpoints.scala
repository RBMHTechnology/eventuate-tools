package com.rbmhtechnology.eventuate.tools.test

import akka.actor.ActorPath
import akka.actor.ActorSelection
import akka.actor.ActorSystem
import com.rbmhtechnology.eventuate.ReplicationConnection
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.ReplicationEndpoint._
import com.rbmhtechnology.eventuate.ReplicationFilter
import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.akkaAddress
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.akkaRemotingConfig
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.withActorSystem
import com.rbmhtechnology.eventuate.tools.test.EventLogs.withLevelDbLogConfig

object ReplicationEndpoints {

  def withBidirectionalReplicationEndpoints[A](
    logNames: Set[String] = Set(DefaultLogName),
    aFilters: Map[String, ReplicationFilter] = Map.empty,
    bFilters: Map[String, ReplicationFilter] = Map.empty
  )(f: (ReplicationEndpoint, ReplicationEndpoint) => A): A = {
    withLevelDbLogConfig { configA =>
      withActorSystem(configA.withFallback(akkaRemotingConfig)) { systemA =>
        withLevelDbLogConfig { configB =>
          withActorSystem(configB.withFallback(akkaRemotingConfig)) { systemB =>
            val connectionToA = replicationConnectionFor(systemA)
            val connectionToB = replicationConnectionFor(systemB)
            val endpointA = replicationEndpoint(logNames, Set(connectionToB), aFilters)(systemA)
            val endpointB = replicationEndpoint(logNames, Set(connectionToA), bFilters)(systemB)
            f(endpointA, endpointB)
          }
        }
      }
    }
  }

  def defaultLogFilter(replicationFilter: ReplicationFilter): Map[String, ReplicationFilter] =
    Map(DefaultLogName -> replicationFilter)

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

  private def replicationEndpoint(
    logNames: Set[String] = Set(DefaultLogName),
    connections: Set[ReplicationConnection] = Set.empty,
    localFilters: Map[String, ReplicationFilter] = Map.empty
  )(implicit system: ActorSystem): ReplicationEndpoint =
    replicationEndpoint(system.name, logNames, connections, localFilters)

  private def replicationEndpoint(
    endpointId: String,
    logNames: Set[String],
    connections: Set[ReplicationConnection],
    localFilters: Map[String, ReplicationFilter]
  )(implicit system: ActorSystem): ReplicationEndpoint = {
    val endpoint = new ReplicationEndpoint(endpointId, logNames, LeveldbEventLog.props(_), connections, localFilters)
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
