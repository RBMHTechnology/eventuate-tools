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

package com.rbmhtechnology.eventuate.tools

import java.nio.file.{ Files, Path }

import akka.actor.{ Actor, ActorRef, ActorSelection, ActorSystem, ExtendedActorSystem, Props }
import akka.testkit.TestProbe
import akka.util.Timeout
import com.rbmhtechnology.eventuate.ReplicationEndpoint.DefaultLogName
import com.rbmhtechnology.eventuate.tools.logviewer.LogViewer._
import com.rbmhtechnology.eventuate.{ EventsourcedView, ReplicationConnection, ReplicationEndpoint }
import com.typesafe.config.{ Config, ConfigFactory }
import org.apache.commons.io.FileUtils

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, Awaitable }

package object logviewer {
  object Futures {
    implicit val timeout = Timeout(10.seconds)

    implicit class AwaitHelper[T](awaitable: Awaitable[T]) {
      def await: T = Await.result(awaitable, timeout.duration)
    }
  }

  object AkkaSystems {

    def loggingConfig: Config = ConfigFactory.parseString("akka.loglevel = ERROR")

    def akkaRemotingConfig: Config = ConfigFactory.parseString(
      """
        |akka.actor.provider = akka.remote.RemoteActorRefProvider
        |akka.remote.netty.tcp.port = 0
      """.stripMargin
    )

    def withActorSystem[A](config: Config)(f: ActorSystem => A): A = {
      import Futures.AwaitHelper
      val system = ActorSystem("default", config.withFallback(loggingConfig))
      try {
        f(system)
      } finally {
        system.terminate().await
      }
    }
  }

  object Eventuate {

    def withTempDir[A](f: Path => A): A = {
      val tmpDir = Files.createTempDirectory("tmp-test")
      try {
        f(tmpDir)
      } finally {
        FileUtils.deleteDirectory(tmpDir.toFile)
      }
    }

    def withLevelDbLogConfig[A](f: Config => A): A = withTempDir { tmpDir =>
      val config = ConfigFactory.parseString(s"eventuate.log.leveldb.dir=${tmpDir.toAbsolutePath}")
      f(config)
    }

    def eventListener(replicationEndpoint: ReplicationEndpoint): EventListener =
      new EventListener(replicationEndpoint.logs(DefaultLogName))(replicationEndpoint.system)

    class EventListener(eventLog: ActorRef)(implicit system: ActorSystem) extends TestProbe(system, s"EventListener") { listener =>
      private class EventListenerView extends EventsourcedView {

        override val id = testActorName

        override val eventLog = listener.eventLog

        override def onCommand = Actor.emptyBehavior

        override def onEvent = {
          case event => ref ! event
        }
      }

      system.actorOf(Props(new EventListenerView))

      def waitForMessage(msg: Any): Any =
        fishForMessage(hint = msg.toString) {
          case `msg` => true
          case _     => false
        }
    }

    def acceptorOf(system: ActorSystem): ActorSelection = {
      system.actorSelection(remoteActorPath(
        akkaProtocol(system),
        replicationConnectionFor(system),
        "acceptor"
      ))
    }

    def replicationConnectionFor(system: ActorSystem): ReplicationConnection = system match {
      case sys: ExtendedActorSystem =>
        val address = sys.provider.getDefaultAddress
        ReplicationConnection(address.host.get, address.port.get, system.name)
    }
  }
}
