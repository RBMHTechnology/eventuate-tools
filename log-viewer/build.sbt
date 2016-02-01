import Dependencies._
import com.typesafe.sbt.packager.SettingsHelper.makeDeploymentSettings

fork in run := true

libraryDependencies ++=
  eventuate ++
    Seq("com.beust" % "jcommander" % "1.48")

enablePlugins(AutomateHeaderPlugin)

// Settings for building and publishing zip-artifact

enablePlugins(JavaAppPackaging)

scriptClasspath += "../ext/*"

publishArtifact in (Compile, packageDoc) := false

makeDeploymentSettings(Universal, packageBin in Universal, "zip")

publish <<= publish dependsOn (publish in Universal)

publishLocal <<= publishLocal dependsOn (publishLocal in Universal)
