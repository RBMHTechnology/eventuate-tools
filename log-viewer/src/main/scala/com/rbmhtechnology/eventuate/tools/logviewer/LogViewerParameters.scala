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

import com.beust.jcommander.Parameter
import com.rbmhtechnology.eventuate.ReplicationConnection.DefaultRemoteSystemName
import com.rbmhtechnology.eventuate.ReplicationEndpoint.DefaultLogName
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

class LogViewerParameters {

  private val config: Config = ConfigFactory.load()

  @Parameter(
    names = Array("--help", "-h"),
    description = "Display this help-message and exit",
    help = true
  )
  var help: Boolean = false

  @Parameter(
    names = Array("--systemName", "-n"),
    description = "Name of the akka-system of the eventuate application"
  )
  var remoteSystemName: String = DefaultRemoteSystemName

  @Parameter(
    names = Array("--remoteHost", "-rh"),
    description = "Remote host running the eventuate application"
  )
  var remoteHost: String = "localhost"

  @Parameter(
    names = Array("--logName", "-log"),
    description = "Name of the log to be viewed"
  )
  var logName: String = DefaultLogName

  @Parameter(
    names = Array("--remotePort", "-r"),
    description = "akka-port of the remote host"
  )
  var remotePort: Int = config.getInt("akka.remote.netty.tcp.port")

  @Parameter(
    names = Array("--localPort", "-l"),
    description = "akka-port of the log-viewer (0 means random port)"
  )
  var localPort: Int = 0

  @Parameter(
    names = Array("--localBindAddress", "-lh"),
    description = "akka-bind-address of the log-viewer (empty means InetAddress.getLocalHost.getHostAddress)"
  )
  var localAddress: String = ""

  @Parameter(
    names = Array("--fromSeqNr", "-f"),
    description = "from sequence number"
  )
  var fromSequenceNr: Long = 0

  @Parameter(
    names = Array("--maxEvents", "-m"),
    description = "maximal number of events to view"
  )
  var maxEvents: Long = 1000

  @Parameter(
    names = Array("--batchSize", "-b"),
    description = "maximal number of events to replicate at once"
  )
  var batchSize: Int = config.getInt("eventuate.log.write-batch-size")

  @Parameter(
    names = Array("--eventFormat", "-e"),
    description = "format string for the event. " +
      "This is a java.util.Formatter like string to format a DurableEvent-instance. " +
      "The format specifiers within the format string must be named according to the fields of DurableEvent. " +
      "For this the leading `%` of a format specifier is followed by a " +
      "field-name in parenthesis (similar to the mapping key in python's format-syntax). " +
      "A format specifier may also be named `this`. In this case the entire DurableEvent instance is " +
      "provided as argument instead of a single field. " +
      "See\nhttp://rbmhtechnology.github.io/eventuate/latest/api/index.html#com.rbmhtechnology.eventuate.DurableEvent for valid field-names."
  )
  var eventFormatString = "%(localSequenceNr)s %(systemTimestamp)tFT%(systemTimestamp)tT.%(systemTimestamp)tL %(this)s"

  val scanLimit: Int = config.getInt("eventuate.log.replication.remote-scan-limit")
}
