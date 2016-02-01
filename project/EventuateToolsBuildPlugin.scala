import sbt._
import sbt.Keys._

import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys.preferences
import scalariform.formatter.preferences._

import de.heikoseeberger.sbtheader.HeaderPlugin
import de.heikoseeberger.sbtheader.HeaderKey.headers
import de.heikoseeberger.sbtheader.license.Apache2_0

object EventuateToolsBuildPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = HeaderPlugin && SbtScalariform

  override def projectSettings =
    artifactSettings ++
    compileSettings ++
    resolverSettings ++
    publishSettings ++
    formatSettings ++
    headerSettings

  val artifactSettings = Seq(
    organization := "com.rbmhtechnology.eventuate-tools",
    version := "0.1-SNAPSHOT"
  )

  val compileSettings = Seq(
    scalaVersion := "2.11.7",
    javacOptions += "-Xlint:unchecked",
    scalacOptions ++= Seq("-deprecation", "-feature", "-language:existentials", "-language:postfixOps"),
    autoAPIMappings := true
  )

  val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    preferences := preferences.value.setPreference(AlignSingleLineCaseStatements, true)
  )

  private val header = Apache2_0("2016", "Red Bull Media House GmbH <http://www.redbullmediahouse.com> - all rights reserved.")

  val headerSettings = Seq(
    headers := Map("scala" -> header)
  )

  private val jfrogHost = "oss.jfrog.org"
  private val jfrogUri = s"https://$jfrogHost"
  private val jfrogPublish = s"$jfrogUri/artifactory"
  private val jfrogSnapshots = "oss-snapshot-local"
  private val jfrogReleases = "oss-release-local"

  val publishSettings = Seq(
    credentials += Credentials(
      "Artifactory Realm",
      jfrogHost,
      sys.env.getOrElse("OSS_JFROG_USER", ""),
      sys.env.getOrElse("OSS_JFROG_PASS", "")
    ),
    publishTo := {
      if (isSnapshot.value)
        Some("Publish OJO Snapshots" at s"$jfrogPublish/$jfrogSnapshots")
      else
        Some("Publish OJO Releases" at s"$jfrogPublish/$jfrogReleases")
    }
  )

  val resolverSettings = Seq(
    resolvers += "OJO Snapshots" at s"$jfrogUri/$jfrogSnapshots"
  )
}
