import Dependencies._

val springVersion = "4.2.6.RELEASE"

libraryDependencies ++= dropWizardMetrics ++
  (
    dropWizardMetricsServlet ++
      Seq (
        "javax.servlet" % "javax.servlet-api" % "3.1.0",
        "org.springframework" % "spring-test" % springVersion,
        "org.springframework" % "spring-web" % springVersion
      )
  ).map(_ % Test)
