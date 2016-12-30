import ReleaseTransformations._

lazy val testCore = subProject("test-core")
lazy val logViewer = subProject("log-viewer").dependsOn(testCore % "test->test")
lazy val dropwizardMetrics = subProject("dropwizard-metrics").dependsOn(testCore % "test->test")
lazy val kamonMetrics = subProject("kamon-metrics").dependsOn(testCore % "test->test")
lazy val dropwizardHealth = subProject("dropwizard-healthchecks").dependsOn(testCore % "test->test")

// release
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  setNextVersion,
  commitNextVersion
)

releaseTagName := s"v-${if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value}"

// do not package/publish from root

Keys.`package` := {
  (Keys.`package` in (logViewer, Compile)).value
}

publish := {}

publishLocal := {}

def subProject(id: String): Project = Project(id, file(id))

