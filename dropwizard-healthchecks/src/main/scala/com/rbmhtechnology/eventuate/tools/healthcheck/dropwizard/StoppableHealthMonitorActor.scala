package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import akka.actor.Actor
import akka.actor.PoisonPill
import com.codahale.metrics.health.HealthCheck
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.StoppableHealthMonitorActor.StopMonitoring

abstract class StoppableHealthMonitorActor extends Actor {

  private var monitoringStopped = false

  override def postStop(): Unit = {
    if (!monitoringStopped) monitoringStoppedPrematurely()
    super.postStop()
  }

  protected def receiveStop: Receive = {
    case StopMonitoring =>
      monitoringStopped = true
      stopMonitoring()
      self ! PoisonPill
  }

  protected def monitoringStoppedPrematurely()

  protected def stopMonitoring(): Unit
}

object StoppableHealthMonitorActor {
  case object StopMonitoring

  /**
   * Exception of an unhealthy [[HealthCheck.Result]] for an unknown health state as the monitoring
   * system ended prematurely.
   */
  abstract class MonitorActorStoppedPrematurelyException(details: String)
    extends IllegalStateException(s"Monitor actor stopped prematurely. $details has unknown state")
}
