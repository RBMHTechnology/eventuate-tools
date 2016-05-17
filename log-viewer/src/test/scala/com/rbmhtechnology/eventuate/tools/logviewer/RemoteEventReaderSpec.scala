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

package com.rbmhtechnology.eventuate.tools.logviewer

import java.util.concurrent.CyclicBarrier

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.ReplicationEndpoint.DefaultLogName
import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog
import com.rbmhtechnology.eventuate.tools.logviewer.RemoteEventReaderSpec.withLevelDbReplicationEndpoint
import com.rbmhtechnology.eventuate.tools.logviewer.AkkaSystems.withActorSystem
import com.rbmhtechnology.eventuate.tools.logviewer.AkkaSystems.akkaRemotingConfig
import com.rbmhtechnology.eventuate.tools.logviewer.Eventuate.{ acceptorOf, eventListener, withLevelDbLogConfig }
import com.rbmhtechnology.eventuate.tools.logviewer.RemoteEventReaderSpec.emit
import com.rbmhtechnology.eventuate.tools.logviewer.RemoteEventReaderSpec.storeErrors
import com.rbmhtechnology.eventuate.tools.logviewer.RemoteEventReaderSpec.storeEvents
import com.rbmhtechnology.eventuate.{ EventsourcedActor, ReplicationEndpoint }
import org.scalatest.{ Matchers, WordSpec }

import scala.collection.mutable.ListBuffer

class RemoteEventReaderSpec extends WordSpec with Matchers {
  "A remote event-sourced system with events" when {
    "RemoteEventReader.readEventsAndDo is invoked with a valid event-range" must {
      "read these events from the remote acceptor" in withLevelDbReplicationEndpoint { implicit endpoint =>
        implicit val system = endpoint.system
        emit(1 to 20)

        val events = ListBuffer.empty[Int]
        val errors = ListBuffer.empty[Throwable]
        val barrier = new CyclicBarrier(2)
        val from = 5
        val max = 10
        RemoteEventReader.readEventsAndDo(
          acceptorOf(system), DefaultLogName, from, max, batchSize = 2, scanLimit = 1000
        )(storeEvents(events))(storeErrors(errors))(barrier.await())
        barrier.await()

        events shouldBe (from until from + max)
        errors shouldBe 'empty
      }
    }
    "RemoteEventReader.readEventsAndDo is invoked with an event-range exceeding existing events" must {
      "read all events after fromSequenceNo from the remote acceptor" in withLevelDbReplicationEndpoint { implicit endpoint =>
        implicit val system = endpoint.system
        val totalEventCnt = 20
        emit(1 to totalEventCnt)

        val events = ListBuffer.empty[Int]
        val errors = ListBuffer.empty[Throwable]
        val barrier = new CyclicBarrier(2)
        val from = 5
        RemoteEventReader.readEventsAndDo(
          acceptorOf(system), DefaultLogName, from, totalEventCnt, batchSize = 2, scanLimit = 1000
        )(storeEvents(events))(storeErrors(errors))(barrier.await())
        barrier.await()

        events shouldBe (from to totalEventCnt)
        errors shouldBe 'empty
      }
    }
    "RemoteEventReader.readEventsAndDo is invoked with an event-range after existing events" must {
      "read no events from the remote acceptor" in withLevelDbReplicationEndpoint { implicit endpoint =>
        implicit val system = endpoint.system
        val totalEventCnt = 20
        emit(1 to totalEventCnt)

        val events = ListBuffer.empty[Int]
        val errors = ListBuffer.empty[Throwable]
        val barrier = new CyclicBarrier(2)
        val from = totalEventCnt + 1
        RemoteEventReader.readEventsAndDo(
          acceptorOf(system), DefaultLogName, from, maxEvents = 10, batchSize = 2, scanLimit = 1000
        )(storeEvents(events))(storeErrors(errors))(barrier.await())
        barrier.await()

        events shouldBe 'empty
        errors shouldBe 'empty
      }
    }
  }
}

object RemoteEventReaderSpec {

  def storeEvents(events: ListBuffer[Int]): DurableEvent => Unit =
    events += _.payload.asInstanceOf[Int]

  def storeErrors(errors: ListBuffer[Throwable]): Throwable => Unit = errors += _

  def withLevelDbReplicationEndpoint[A](f: ReplicationEndpoint => A): A = {
    withLevelDbLogConfig { config =>
      withActorSystem(config.withFallback(akkaRemotingConfig)) { implicit system =>
        val endpoint = replicationEndpoint
        f(endpoint)
      }
    }
  }

  private def replicationEndpoint(implicit system: ActorSystem): ReplicationEndpoint = {
    val endpoint = new ReplicationEndpoint(system.name, Set(DefaultLogName), LeveldbEventLog.props(_), Set.empty)
    endpoint.activate()
    endpoint
  }

  def emit(events: Traversable[Any])(implicit endpoint: ReplicationEndpoint): Any = {
    val listener = eventListener(endpoint)
    val actor = startEchoEventsourcedActor
    events.foreach(actor ! _)
    listener.waitForMessage(events.last)
  }

  private def startEchoEventsourcedActor(implicit endpoint: ReplicationEndpoint): ActorRef =
    endpoint.system.actorOf(EchoEventsourcedActor.props(endpoint.logs(DefaultLogName)))

  private class EchoEventsourcedActor(val eventLog: ActorRef) extends EventsourcedActor {
    override def id = getClass.getSimpleName

    override def onEvent = Actor.emptyBehavior

    override def onCommand = {
      case event => persist(event)(_.get)
    }
  }

  private object EchoEventsourcedActor {
    def props(eventLog: ActorRef) = Props(new EchoEventsourcedActor(eventLog))
  }
}
