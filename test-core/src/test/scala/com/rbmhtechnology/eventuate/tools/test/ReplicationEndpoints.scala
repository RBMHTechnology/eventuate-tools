package com.rbmhtechnology.eventuate.tools.test

import akka.actor.ActorPath
import akka.actor.ActorSelection
import akka.actor.ActorSystem
import akka.actor.Props
import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.EndpointFilters
import com.rbmhtechnology.eventuate.EndpointFilters.NoFilters
import com.rbmhtechnology.eventuate.ReplicationConnection
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.ReplicationEndpoint._
import com.rbmhtechnology.eventuate.ReplicationFilter
import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.akkaAddress
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.akkaRemotingConfig
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.withActorSystem
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.withActorSystems
import com.rbmhtechnology.eventuate.tools.test.EventLogs.withLevelDbLogConfig
import com.rbmhtechnology.eventuate.tools.test.EventLogs.withLevelDbLogConfigs

object ReplicationEndpoints {

  def withBidirectionalReplicationEndpoints[A](
    logNames: Set[String] = Set(DefaultLogName),
    logFactory: String => Props = LeveldbEventLog.props(_),
    aFilters: EndpointFilters = NoFilters,
    bFilters: EndpointFilters = NoFilters
  )(f: (ReplicationEndpoint, ReplicationEndpoint) => A): A = {
    withLevelDbLogConfigs(2) { configs =>
      withActorSystems(configs.map(_.withFallback(akkaRemotingConfig))) {
        case Seq(systemA, systemB) =>
          val connectionToA = replicationConnectionFor(systemA)
          val connectionToB = replicationConnectionFor(systemB)
          val endpointA = replicationEndpoint(logNames, logFactory, Set(connectionToB), aFilters)(systemA)
          val endpointB = replicationEndpoint(logNames, logFactory, Set(connectionToA), bFilters)(systemB)
          f(endpointA, endpointB)
      }
    }
  }

  val evenFilter = new ReplicationFilter {
    override def apply(event: DurableEvent) = event.payload match {
      case i: Int => i % 2 == 0
      case _      => false
    }
  }
  val oddFilter = new ReplicationFilter {
    override def apply(event: DurableEvent) = event.payload match {
      case i: Int => i % 2 == 1
      case _      => false
    }
  }

  def defaultLogFilter(replicationFilter: ReplicationFilter): EndpointFilters =
    EndpointFilters.sourceFilters(Map(DefaultLogName -> replicationFilter))

  def defaultLogName(endpoint: ReplicationEndpoint): String = endpoint.logNames.head
  def defaultLogId(endpoint: ReplicationEndpoint): String = endpoint.logId(defaultLogName(endpoint))

  def withLevelDbReplicationEndpoint[A](
    logNames: Set[String] = Set(DefaultLogName),
    connections: Set[ReplicationConnection] = Set.empty
  )(f: ReplicationEndpoint => A): A = {

    withLevelDbLogConfig { config =>
      withActorSystem(config.withFallback(akkaRemotingConfig)) { implicit system =>
        val endpoint = replicationEndpoint(logNames, connections = connections)
        f(endpoint)
      }
    }
  }

  def replicationEndpoint(
    logNames: Set[String] = Set(DefaultLogName),
    logFactory: String => Props = LeveldbEventLog.props(_),
    connections: Set[ReplicationConnection] = Set.empty,
    localFilters: EndpointFilters = NoFilters
  )(implicit system: ActorSystem): ReplicationEndpoint =
    activatedReplicationEndpoint(system.name, logNames, logFactory, connections, localFilters)

  private def activatedReplicationEndpoint(
    endpointId: String,
    logNames: Set[String],
    logFactory: String => Props = LeveldbEventLog.props(_),
    connections: Set[ReplicationConnection],
    localFilters: EndpointFilters
  )(implicit system: ActorSystem): ReplicationEndpoint = {
    val endpoint = new ReplicationEndpoint(endpointId, logNames, logFactory, connections, localFilters)
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
