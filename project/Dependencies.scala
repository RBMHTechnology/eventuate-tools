import sbt._

object Dependencies {

  val eventuateVersion = "0.8-SNAPSHOT"
  val akkaVersion = "2.4.4"
  val dropWizardMetricsVersion = "3.1.0"

  val eventuateGroup = "com.rbmhtechnology"
  val dropwizardMetricsGroup = "io.dropwizard.metrics"

  lazy val eventuate = Seq(
    eventuateGroup %% "eventuate-core" % eventuateVersion
  )
  lazy val eventuateLevelDb = Seq(
    eventuateGroup %% "eventuate-log-leveldb" % eventuateVersion
  )
  lazy val akkaTestKit = Seq(
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion
  )
  lazy val scalaTest = Seq(
    "org.scalatest" %% "scalatest" % "2.2.6"
  )
  lazy val dropWizardMetrics = Seq(
    dropwizardMetricsGroup % "metrics-core" % dropWizardMetricsVersion
  )
  lazy val dropWizardMetricsServlet = Seq(
    dropwizardMetricsGroup % "metrics-servlets" % dropWizardMetricsVersion
  )
  lazy val dropWizardHealthChecks = Seq(
    dropwizardMetricsGroup % "metrics-healthchecks" % dropWizardMetricsVersion
  )
}
