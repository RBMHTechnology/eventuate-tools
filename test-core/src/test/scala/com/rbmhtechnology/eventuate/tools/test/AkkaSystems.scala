package com.rbmhtechnology.eventuate.tools.test

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.ExtendedActorSystem
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object AkkaSystems {

  private val akkaSystemCounter = new AtomicInteger(0)

  def akkaRemotingConfig: Config = ConfigFactory.parseString(
    """
      |akka.actor.provider = akka.remote.RemoteActorRefProvider
      |akka.remote.netty.tcp.port = 0
    """.stripMargin
  )

  def withActorSystem[A](overrideConfig: Config = ConfigFactory.empty())(f: ActorSystem => A): A = {
    import Futures.AwaitHelper
    val system = ActorSystem(newUniqueSystemName, overrideConfig.withFallback(ConfigFactory.load()))
    try {
      f(system)
    } finally {
      system.terminate().await
    }
  }

  def newUniqueSystemName: String = s"default${akkaSystemCounter.getAndIncrement()}"

  def akkaAddress(system: ActorSystem): Address = system match {
    case sys: ExtendedActorSystem => sys.provider.getDefaultAddress
  }
}
