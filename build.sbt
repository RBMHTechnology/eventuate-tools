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

// do not package/publish from root

Keys.`package` := {
  (Keys.`package` in (logViewer, Compile)).value
}

publish := {}

publishLocal := {}

def subProject(id: String): Project = Project(id, file(id))

