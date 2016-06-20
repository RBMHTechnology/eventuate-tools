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

import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.ReplicationEndpoint.DefaultLogName
import com.rbmhtechnology.eventuate.tools.logviewer.RemoteEventReaderSpec.emit
import com.rbmhtechnology.eventuate.tools.logviewer.RemoteEventReaderSpec.storeErrors
import com.rbmhtechnology.eventuate.tools.logviewer.RemoteEventReaderSpec.storeEvents
import com.rbmhtechnology.eventuate.tools.test.EventLogs.eventInspector
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.acceptorOf
import com.rbmhtechnology.eventuate.tools.test.ReplicationEndpoints.withLevelDbReplicationEndpoint
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import org.scalatest.Matchers
import org.scalatest.WordSpec

import scala.collection.mutable.ListBuffer

class RemoteEventReaderSpec extends WordSpec with Matchers {
  "A remote event-sourced system with events" when {
    "RemoteEventReader.readEventsAndDo is invoked with a valid event-range" must {
      "read these events from the remote acceptor" in withLevelDbReplicationEndpoint() { implicit endpoint =>
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
      "read all events after fromSequenceNo from the remote acceptor" in withLevelDbReplicationEndpoint() { implicit endpoint =>
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
      "read no events from the remote acceptor" in withLevelDbReplicationEndpoint() { implicit endpoint =>
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

  def emit(events: Traversable[Any])(implicit endpoint: ReplicationEndpoint): Unit =
    eventInspector(endpoint).emitAndWait(events)
}
