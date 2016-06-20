val dropWizardMetricsVersion = "3.1.0"
val springVersion = "4.2.6.RELEASE"

libraryDependencies ++= Seq(
  "io.dropwizard.metrics" % "metrics-core" % dropWizardMetricsVersion
) ++ Seq (
  "io.dropwizard.metrics" % "metrics-servlets" % dropWizardMetricsVersion,
  "javax.servlet" % "javax.servlet-api" % "3.1.0",
  "org.springframework" % "spring-test" % springVersion,
  "org.springframework" % "spring-web" % springVersion
).map(_ % Test)
