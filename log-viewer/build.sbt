import com.typesafe.sbt.packager.SettingsHelper.makeDeploymentSettings
import sbtbuildinfo.BuildInfoPlugin

fork in run := true

libraryDependencies ++= Seq(
    "com.beust" % "jcommander" % "1.48",
    "org.apache.velocity" % "velocity" % "1.7"
  )

enablePlugins(AutomateHeaderPlugin)

// include build-info

enablePlugins(BuildInfoPlugin)

buildInfoKeys := Seq[BuildInfoKey](name, organization, version)

buildInfoPackage := "com.rbmhtechnology.eventuate.tools.logviewer"

buildInfoObject := "LogViewerBuildInfo"

buildInfoUsePackageAsPath := true

// Settings for building and publishing zip-artifact

enablePlugins(JavaAppPackaging)

scriptClasspath += "../ext/*"

publishArtifact in (Compile, packageDoc) := false

publishArtifact in (Test, packageDoc) := false

makeDeploymentSettings(Universal, packageBin in Universal, "zip")

publish := {
  (publish in Universal).value
  publish.value
}

publishLocal := {
  (publishLocal in Universal).value
  publishLocal.value
}
