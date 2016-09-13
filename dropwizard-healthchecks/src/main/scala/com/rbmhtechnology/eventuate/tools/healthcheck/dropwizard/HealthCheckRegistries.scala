package com.rbmhtechnology.eventuate.tools.healthcheck.dropwizard

import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheck.Result
import com.codahale.metrics.health.HealthCheckRegistry

private object HealthCheckRegistries {

  private val healthy: HealthCheck = new HealthCheck {
    override def check() = Result.healthy()
  }

  implicit class RichHealthCheckRegistry(val registry: HealthCheckRegistry) extends AnyVal {
    private def register(name: String, check: HealthCheck): Unit = {
      registry.unregister(name)
      registry.register(name, check)
    }

    def registerHealthy(name: String): Unit = register(name, healthy)

    def registerUnhealthy(name: String, cause: Throwable): Unit = {
      register(name, new HealthCheck {
        override def check() = Result.unhealthy(cause)
      })
    }
  }

  def optionallyPrefixed(name: String, prefix: Option[String] = None): String = {
    val optionalPrefix = prefix.map(_ + ".").getOrElse("")
    s"$optionalPrefix$name"
  }
}
