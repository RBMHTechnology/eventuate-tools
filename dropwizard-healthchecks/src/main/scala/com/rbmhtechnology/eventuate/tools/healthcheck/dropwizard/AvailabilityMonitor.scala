package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.HealthCheckRegistries.RichHealthCheckRegistry
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.HealthCheckRegistries.optionallyPrefixed
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.AvailabilityMonitor.HealthRegistryName
import com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard.AvailabilityMonitor.UnhealthyCause

import scala.reflect.ClassTag
import scala.reflect.classTag

private class AvailabilityMonitor[Available: ClassTag: HealthRegistryName, Unavailable: ClassTag: HealthRegistryName: UnhealthyCause](
    healthRegistry: HealthCheckRegistry, monitoringStoppedPrematurelyFailure: Exception, namePrefix: Option[String]
) extends StoppableHealthMonitorActor {

  import AvailabilityMonitor._

  var healthy: Map[String, Boolean] = Map.empty

  override def receive = receiveStop orElse {
    case unavailable: Unavailable =>
      healthRegistry.registerUnhealthy(optionallyPrefixed(unavailable.healthRegistryName, namePrefix), unavailable.unhealthyCause)
      healthy += (unavailable.healthRegistryName -> false)
    case available: Available if !healthy.getOrElse(available.healthRegistryName, false) =>
      healthy += (available.healthRegistryName -> true)
      healthRegistry.registerHealthy(optionallyPrefixed(available.healthRegistryName, namePrefix))
  }

  override protected def monitoringStoppedPrematurely(): Unit =
    healthy.keys.foreach(name => healthRegistry.registerUnhealthy(name, monitoringStoppedPrematurelyFailure))

  override protected def stopMonitoring(): Unit =
    healthy.keys.foreach(name => healthRegistry.unregister(optionallyPrefixed(name, namePrefix)))
}

object AvailabilityMonitor {

  /**
   * Typeclass for the registry name of a monitored available/unavailable event.
   */
  trait HealthRegistryName[A] {
    def healthRegistryName(a: A): String
  }

  private implicit class HealthNameSyntax[A](val a: A) extends AnyVal {
    def healthRegistryName(implicit healthRegistryName: HealthRegistryName[A]): String =
      healthRegistryName.healthRegistryName(a)
  }

  /**
   * Typeclass for the cause of a monitored unavailable event.
   */
  trait UnhealthyCause[A] {
    def unhealthyCause(a: A): Throwable
  }

  private implicit class UnhealthyCauseSyntax[A](val a: A) extends AnyVal {
    def unhealthyCause(implicit unhealthyCause: UnhealthyCause[A]): Throwable = unhealthyCause.unhealthyCause(a)
  }

  private def props[Available: ClassTag: HealthRegistryName, Unavailable: ClassTag: HealthRegistryName: UnhealthyCause](
    healthRegistry: HealthCheckRegistry,
    namePrefix: Option[String] = None,
    monitoringStoppedPrematurelyFailure: Exception
  ) = Props(new AvailabilityMonitor[Available, Unavailable](healthRegistry, monitoringStoppedPrematurelyFailure, namePrefix))

  /**
   * Install a generic monitor for (un)-available events published on [[ActorSystem.eventStream]].
   *
   * @tparam Available Type of event that indicates a healthy [[HealthCheck.Result]].
   *                   A type-class instance of [[HealthRegistryName]] has to be provided to determine
   *                   the registry name from the event.
   * @tparam Unavailable Type of event that indicates an unhealthy [[HealthCheck.Result]]
   *                     Type-class instances for [[HealthRegistryName]] and [[UnhealthyCause]] have to
   *                     be provided to determine the registry name and the cause for unhealthiness
   *                     from the event.
   */
  def monitorHealth[Available: ClassTag: HealthRegistryName, Unavailable: ClassTag: HealthRegistryName: UnhealthyCause](
    system: ActorSystem,
    healthRegistry: HealthCheckRegistry,
    monitoringStoppedPrematurelyFailure: Exception,
    namePrefix: Option[String] = None
  ): ActorRef = {
    val actorRef = system.actorOf(props[Available, Unavailable](healthRegistry, namePrefix, monitoringStoppedPrematurelyFailure))
    system.eventStream.subscribe(actorRef, classTag[Available].runtimeClass)
    system.eventStream.subscribe(actorRef, classTag[Unavailable].runtimeClass)
    actorRef
  }
}
