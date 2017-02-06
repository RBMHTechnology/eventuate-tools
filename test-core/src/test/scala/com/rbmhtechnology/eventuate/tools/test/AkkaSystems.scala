package com.rbmhtechnology.eventuate.tools.test

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.ExtendedActorSystem
import com.rbmhtechnology.eventuate.ReplicationConnection.DefaultRemoteSystemName
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import scala.collection.immutable.Seq

object AkkaSystems {

  private val akkaSystemCounter = new AtomicInteger(0)

  def akkaRemotingConfig: Config = ConfigFactory.parseString(
    """
      |akka.actor.provider = akka.remote.RemoteActorRefProvider
      |akka.remote.netty.tcp.port = 0
    """.stripMargin
  )

  def akkaTestTimeoutConfig: Config = ConfigFactory.parseString(
    s"akka.test.single-expect-default=${TestTimings.timeout.duration.toMillis}ms"
  )

  def withActorSystem[A](overrideConfig: Config = ConfigFactory.empty())(f: ActorSystem => A): A =
    withActorSystems(List(overrideConfig))(systems => f(systems.head))

  def withActorSystems[A](overrideConfigs: Seq[Config])(f: Seq[ActorSystem] => A): A = {
    import Futures.AwaitHelper

    var systems = Vector.empty[ActorSystem]
    try {
      overrideConfigs.foreach { overrideConfig =>
        val config = overrideConfig
          .withFallback(akkaTestTimeoutConfig)
          .withFallback(ConfigFactory.parseResourcesAnySyntax("application.conf"))
          .withFallback(ConfigFactory.load("test-core.conf"))
        systems = systems :+ ActorSystem(newUniqueSystemName, config)
      }
      f(systems)
    } finally {
      systems.foreach(_.terminate().await)
    }
  }

  def newUniqueSystemName: String = s"$DefaultRemoteSystemName${akkaSystemCounter.getAndIncrement()}"

  def akkaAddress(system: ActorSystem): Address = system match {
    case sys: ExtendedActorSystem => sys.provider.getDefaultAddress
  }
}
