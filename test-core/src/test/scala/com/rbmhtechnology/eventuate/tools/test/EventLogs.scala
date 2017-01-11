package com.rbmhtechnology.eventuate.tools.test

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.TestProbe
import com.rbmhtechnology.eventuate.EventsourcedActor
import com.rbmhtechnology.eventuate.ReplicationEndpoint
import com.rbmhtechnology.eventuate.ReplicationEndpoint._
import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog
import com.rbmhtechnology.eventuate.tools.test.AkkaSystems.withActorSystem
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils

import scala.collection.immutable.Seq
import scala.util.control.Exception.ignoring

object EventLogs {

  private val logIdCounter = new AtomicInteger(0)

  def withTempDir[A](f: Path => A): A = withTempDirs(1)(paths => f(paths.head))

  def withTempDirs[A](n: Int)(f: Seq[Path] => A): A = {
    val tmpDirs = (1 to n).map(i => Files.createTempDirectory(s"tmp-test$i"))
    try {
      f(tmpDirs)
    } finally {
      tmpDirs.foreach(dir => ignoring(classOf[Exception])(FileUtils.deleteDirectory(dir.toFile)))
    }
  }

  def withLevelDbLogConfig[A](f: Config => A): A = withLevelDbLogConfigs(1)(configs => f(configs.head))

  def withLevelDbLogConfigs[A](n: Int)(f: Seq[Config] => A): A = withTempDirs(n) { tmpDirs =>
    val configs = tmpDirs.map(dir => ConfigFactory.parseString(s"eventuate.log.leveldb.dir=${dir.toAbsolutePath}"))
    f(configs)
  }

  def withLevelDbEventLog[A](id: String = uniqueLogId)(f: (ActorSystem, ActorRef) => A): A =
    withLevelDbLogConfig { config =>
      withActorSystem(config) { system =>
        f(system, system.actorOf(LeveldbEventLog.props(id)))
      }
    }

  private def uniqueLogId: String = ReplicationEndpoint.DefaultLogName + logIdCounter.incrementAndGet()

  def eventInspector(replicationEndpoint: ReplicationEndpoint, logName: String = DefaultLogName): EventInspector =
    new EventInspector(replicationEndpoint.logs(logName))(replicationEndpoint.system)

  class EventInspector(eventLog: ActorRef)(implicit system: ActorSystem) extends TestProbe(system, "EventListener") { listener =>
    private class EventInspectorActor extends EventsourcedActor {

      override val id = testActorName

      override val eventLog = listener.eventLog

      override def onCommand = {
        case event => persist(event)(_.get)
      }

      override def onEvent = {
        case event => ref ! event
      }
    }

    private val inspectorActor = system.actorOf(Props(new EventInspectorActor))

    def emit(events: Traversable[Any]): Unit = events.foreach(inspectorActor ! _)

    def emitAndWait(events: Traversable[Any]): Unit = {
      emit(events)
      events.foreach(waitForMessage)
    }

    def waitForMessage(msg: Any): Any =
      fishForMessage(hint = msg.toString) {
        case `msg` => true
        case _     => false
      }
  }
}
