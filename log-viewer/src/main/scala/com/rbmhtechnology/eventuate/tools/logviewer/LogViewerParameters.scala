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

import java.lang.StringBuilder

import com.beust.jcommander.IParameterValidator
import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.rbmhtechnology.eventuate.ReplicationConnection.DefaultRemoteSystemName
import com.rbmhtechnology.eventuate.ReplicationEndpoint.DefaultLogName
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

class LogViewerParameters {

  import LogViewerParameters._

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
    description = "format string for the event. Is interpreted by the formatter given by -ef/--eventFormatter"
  )
  var eventFormatString = "%(localSequenceNr)s %(systemTimestamp)tFT%(systemTimestamp)tT.%(systemTimestamp)tL %(this)s"

  @Parameter(
    names = Array("--eventFormatter", "-ef"),
    description = "format template engine to be used for interpreting the format string given by '-e/--eventFormat'" +
      "Options are: CaseClass, Velocity (case insensitive). In case of CaseClass the format string " +
      "is a java.util.Formatter like string to format a DurableEvent-instance. " +
      "The format specifiers within the format string must be named according to the fields of DurableEvent. " +
      "For this the leading `%` of a format specifier is followed by a " +
      "field-name in parenthesis (similar to the mapping key in python's format-syntax). " +
      "A format specifier may also be named `this`. In this case the entire DurableEvent instance is " +
      "provided as argument instead of a single field. " +
      "In case of Velocity the format string is a valid Velocity template (http://velocity.apache.org/engine/1.7/user-guide.html) " +
      "with access to two variables: " +
      "$ev referencing a DurableEvent and $nl containing a newline. This allows not only to specify the output" +
      "format of an DurableEvent, but also to apply content based filtering." +
      "For example the format string '#if( $ev.payload() == ... )$ev.payload()$nl#end' ensures that " +
      "only those events that meet the given condition are printed. Note that to get each event in a separate line " +
      "a $nl needs to be at the end of the format string.",
    converter = classOf[FormatterTypeConverter],
    validateWith = classOf[FormatterTypeValidator]
  )
  var eventFormatter: FormatterType = CaseClass

  val scanLimit: Int = config.getInt("eventuate.log.replication.remote-scan-limit")
}

object LogViewerParameters {

  sealed trait FormatterType
  case object CaseClass extends FormatterType
  case object Velocity extends FormatterType

  private val formatterTypeByString: Map[String, FormatterType] =
    List(CaseClass, Velocity).map(t => t.toString.toLowerCase -> t).toMap

  private class FormatterTypeConverter extends IStringConverter[FormatterType] {
    override def convert(value: String): FormatterType = formatterTypeByString(value.toLowerCase())
  }

  private class FormatterTypeValidator extends IParameterValidator {
    override def validate(name: String, value: String): Unit =
      if (!formatterTypeByString.isDefinedAt(value.toLowerCase))
        throw new ParameterException(s"'$name' must be one of ${formatterTypeByString.keys.mkString(",")}")
  }

  def parseCommandLineArgs(programName: String, args: Array[String]): Either[(String, Int), LogViewerParameters] = {
    val jCommander = new JCommander()
    jCommander.setProgramName(programName)
    val params = new LogViewerParameters()
    jCommander.addObject(params)
    try {
      jCommander.parse(args: _*)
      if (params.help) Left((usage(jCommander), 0))
      else Right(params)
    } catch {
      case ex: ParameterException =>
        Left((s"${ex.getMessage}${System.lineSeparator()}${usage(jCommander)}", 1))
    }
  }

  private def usage(jCommander: JCommander): String = {
    val sb = new StringBuilder()
    jCommander.usage(sb)
    sb.toString
  }
}