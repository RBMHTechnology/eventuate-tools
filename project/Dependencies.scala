import sbt._

object Dependencies {

  val eventuateVersion = "0.7.1"
  val akkaVersion = "2.4.4"

  lazy val eventuate = Seq(
    "com.rbmhtechnology" %% "eventuate-core" % eventuateVersion
  )
  lazy val eventuateLevelDb = Seq(
    "com.rbmhtechnology" %% "eventuate-log-leveldb" % eventuateVersion
  )
  lazy val akkaTestKit = Seq(
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion
  )
  lazy val scalaTest = Seq(
    "org.scalatest" %% "scalatest" % "2.2.6"
  )
}
