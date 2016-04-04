import ReleaseTransformations._

lazy val logViewer = subProject("log-viewer")

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

