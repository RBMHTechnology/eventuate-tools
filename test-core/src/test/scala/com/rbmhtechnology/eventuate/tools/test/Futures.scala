package com.rbmhtechnology.eventuate.tools.test

import scala.concurrent.Await
import scala.concurrent.Awaitable

object Futures {
  implicit class AwaitHelper[T](awaitable: Awaitable[T]) {
    def await: T = Await.result(awaitable, TestTimings.timeout.duration)
  }
}
