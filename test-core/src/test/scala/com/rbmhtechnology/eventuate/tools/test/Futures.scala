package com.rbmhtechnology.eventuate.tools.test

import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.Awaitable
import scala.concurrent.duration.DurationInt

object Futures {
  implicit val timeout = Timeout(10.seconds)

  implicit class AwaitHelper[T](awaitable: Awaitable[T]) {
    def await: T = Await.result(awaitable, timeout.duration)
  }
}
