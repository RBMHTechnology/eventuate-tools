package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import akka.actor.Actor
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.Terminated
import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.Acceptor
import com.rbmhtechnology.eventuate.log.EventLog
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.ActorHealthMonitor.AcceptorActorTerminatedException
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.ActorHealthMonitor.EventLogActorTerminatedException
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.ActorHealthMonitor.acceptorActorHealthName
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.ActorHealthMonitor.logActorHealthName
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.HealthCheckRegistries._

private class ActorHealthMonitorActor(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: Option[String]) extends Actor {

  override def preStart(): Unit = {
    super.preStart()
    endpoint.logs.foreach {
      case (logName, logActor) =>
        healthRegistry.registerHealthy(logHealthRegistryName(endpoint.logId(logName)))
        context.watch(logActor)
    }
    healthRegistry.registerHealthy(acceptorHealthRegistryName)
    context.watch(endpoint.acceptor)
  }

  override def postStop(): Unit = {
    endpoint.logNames.foreach(logName => healthRegistry.unregister(logHealthRegistryName(endpoint.logId(logName))))
    healthRegistry.unregister(acceptorHealthRegistryName)
    super.postStop()
  }

  override def receive: Receive = {
    case Terminated(actorRef) if actorRef == endpoint.acceptor =>
      healthRegistry.registerUnhealthy(acceptorHealthRegistryName, AcceptorActorTerminatedException)
    case Terminated(actorRef) =>
      endpoint.logs.collectFirst {
        case (logName, `actorRef`) =>
          val logId = endpoint.logId(logName)
          healthRegistry.registerUnhealthy(
            logHealthRegistryName(logId),
            new EventLogActorTerminatedException(logId)
          )
      }
  }

  private def logHealthRegistryName(logId: String): String =
    optionallyPrefixed(logActorHealthName(logId), namePrefix)

  private val acceptorHealthRegistryName: String = optionallyPrefixed(acceptorActorHealthName, namePrefix)
}

private object ActorHealthMonitorActor {
  def props(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: Option[String]) =
    Props(new ActorHealthMonitorActor(endpoint, healthRegistry, namePrefix))
}

/**
 * Monitors all [[EventLog]] actors and the acceptor
 * actor of the passed [[ReplicationEndpoint]] and registers _unhealthy_ [[HealthCheck.Result]]s
 * for each terminated actor.
 *
 * The registry name for log-actors is: `actor.eventlog.<log-id>` and the one for the acceptor is `actor.acceptor`.
 * These names are optionally prefixed with `namePrefix.` if that is non-empty.
 */
class ActorHealthMonitor(endpoint: ReplicationEndpoint, healthRegistry: HealthCheckRegistry, namePrefix: Option[String] = None) {
  private val monitorActor = endpoint.system.actorOf(
    ActorHealthMonitorActor.props(endpoint, healthRegistry, namePrefix)
  )

  /**
   * Stop monitoring actor health and de-register health checks (asynchronously).
   */
  def stopMonitoring(): Unit = monitorActor ! PoisonPill
}

object ActorHealthMonitor {

  /**
   * Returns the registry name (without prefix) under which a [[HealthCheck.Result]]
   * of [[EventLog]] actors is registered.
   */
  def logActorHealthName(logId: String) = s"actor.eventlog.$logId"

  /**
   * Returns the registry name (without prefix) under which a [[HealthCheck.Result]]
   * of an acceptor actors is registered.
   */
  def acceptorActorHealthName = s"actor.${Acceptor.Name}"

  /**
   * The exception of an unhealthy [[HealthCheck.Result]] for terminated [[EventLog]] actors.
   */
  class EventLogActorTerminatedException(logId: String)
    extends IllegalStateException(s"EventLogActor of log with id $logId terminated")

  /**
   * The exception of an unhealthy [[HealthCheck.Result]] for a terminated acceptor actors.
   */
  object AcceptorActorTerminatedException extends IllegalStateException("Acceptor actor terminated")
}
