lazy val logViewer = subProject("log-viewer")

// do not package/publish from root

Keys.`package` := {
  (Keys.`package` in (logViewer, Compile)).value
}

publish := {}

publishLocal := {}

def subProject(id: String): Project = Project(id, file(id))

