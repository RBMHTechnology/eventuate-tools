import sbt._

object Dependencies {

  val eventuateVersion = "0.7.1"
  val akkaVersion = "2.4.4"
  val dropWizardMetricsVersion = "3.1.0"

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
  lazy val dropWizardMetrics = Seq(
    "io.dropwizard.metrics" % "metrics-core" % dropWizardMetricsVersion
  )
  lazy val dropWizardMetricsServlet = Seq(
    "io.dropwizard.metrics" % "metrics-servlets" % dropWizardMetricsVersion
  )
}
