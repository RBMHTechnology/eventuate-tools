package com.rbmhtechnology.eventuate.tools.test

import akka.util.Timeout
import org.scalatest.concurrent.Eventually
import org.scalatest.time.Millis
import org.scalatest.time.Span

import scala.concurrent.duration.DurationInt

object TestTimings {
  implicit val timeout = Timeout(10.seconds)
}

trait EventuallyWithDefaultTiming extends Eventually {
  override implicit def patienceConfig =
    PatienceConfig(Span(TestTimings.timeout.duration.toMillis, Millis), Span(100, Millis))
}
