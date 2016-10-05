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

import java.io.StringWriter

import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.tools.logviewer.LogViewerParameters.CaseClass
import com.rbmhtechnology.eventuate.tools.logviewer.LogViewerParameters.FormatterType
import com.rbmhtechnology.eventuate.tools.logviewer.LogViewerParameters.Velocity
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine

/**
 * Typeclass for returning a formatted string of an object
 */
trait Formatter[A] {
  def format(a: A): String
}

object DurableEventFormatter {
  /**
   * Returns a [[Formatter]] instance for formatting a [[DurableEvent]]
   */
  def apply(formatter: FormatterType, formatString: String): Formatter[DurableEvent] = formatter match {
    case CaseClass => new CaseClassFormatter[DurableEvent](formatString)
    case Velocity  => new VelocityFormatter[DurableEvent](formatString, "ev")
  }
}

/**
 * Takes a {{java.util.Formatter}} like string to format a `case class`-instance.
 *
 * The format specifiers within the format string must be named according to the fields of the
 * `case class` to be formatted. For this the leading `%` of a format specifier is followed by a
 * field-name in parenthesis (similar to the mapping key in python's format-syntax):
 *
 * {{{
 *   case class A(fieldA: String, fieldB: Int)
 *
 *   new CaseClassFormatter[A]("A: %(fieldA)-5s, B: %(fieldB)03d").format(A("Hi", 42))
 *   // "A: Hi    B: 042"
 * }}}
 *
 * A format specifier may also be named `this`. In this case the entire `case class` instance is
 * provided as argument instead of a single field.
 *
 * @tparam A Must be a `case class`. The implementation relies on the fact that
 *           A's `productIterator` returns the values of A's _fields_ in the same order as
 *           `getDeclaredFields` of A's class.
 */
class CaseClassFormatter[A <: Product](extendedFormatString: String) extends Formatter[A] {

  private val FieldNameGroup = "fieldName"

  private val fieldNameRegEx = """\%\((\p{Alnum}+)\)""".r(FieldNameGroup)

  private val plainFormatString: String =
    fieldNameRegEx.replaceAllIn(extendedFormatString, "%")

  def format(arg: A): String = plainFormatString.format(toFormatArguments(arg): _*) + System.lineSeparator()

  private def toFormatArguments(arg: A): Seq[Any] = {
    val fieldValueMap = arg.getClass.getDeclaredFields.map(_.getName).zip(arg.productIterator.to).toMap.withDefault {
      case "this"    => arg
      case fieldName => s"[unknown field: $fieldName]"
    }
    fieldNameRegEx.findAllMatchIn(extendedFormatString).map(_.group(FieldNameGroup)).map(fieldValueMap).toList
  }
}

/**
 * A generic [[Formatter]] that formats based on a velocity template. As velocity supports conditional expressions this
 * could also be used as a ''filter'' if `format` returns an empty string.
 *
 * The template may reference two variables:
 * - the object to be formatted is available under the name as given by the `name` parameter
 * - a newline can be referenced as `nl`
 *
 * @param template a valid velocity template (see also: http://velocity.apache.org/engine/1.7/user-guide.html).
 * @param name name of the variable that can be used in the template to access the object to be formatted
 */
class VelocityFormatter[A](template: String, name: String) extends Formatter[A] {

  private val engine = new VelocityEngine()
  engine.init()

  override def format(a: A): String = {
    val context = new VelocityContext()
    context.put(name, a)
    context.put("nl", System.lineSeparator())
    val stringWriter = new StringWriter()
    engine.evaluate(context, stringWriter, "inline", template)
    stringWriter.toString
  }
}
