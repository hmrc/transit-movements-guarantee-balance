import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

val appName = "transit-movements-guarantee-balance"

lazy val microservice = Project(appName, file("."))
  .configs(IntegrationTest)
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(SbtDistributablesPlugin.publishingSettings)
  .settings(DefaultBuildSettings.integrationTestSettings())
  .settings(scalacSettings)
  .settings(scoverageSettings)
  .settings(
    majorVersion := 0,
    scalaVersion := "2.12.13",
    resolvers += Resolver.jcenterRepo,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test
  )

lazy val scalacSettings = Def.settings(
  // Disable warnings arising from generated routing code
  scalacOptions += "-Wconf:src=routes/.*:silent",
  // Disable fatal warnings and warnings from discarding values
  scalacOptions ~= { opts =>
    opts.filterNot(Set("-Xfatal-warnings", "-Ywarn-value-discard"))
  }
)

lazy val scoverageSettings = Def.settings(
  Test / parallelExecution := false,
  ScoverageKeys.coverageMinimumStmtTotal := 90,
  ScoverageKeys.coverageFailOnMinimum := true,
  ScoverageKeys.coverageHighlighting := true,
  ScoverageKeys.coverageExcludedPackages := Seq(
    "<empty>",
    "Reverse.*",
    ".*(config|views.*)",
    ".*(BuildInfo|Routes).*"
  ).mkString(";"),
  ScoverageKeys.coverageExcludedFiles := Seq(
    "<empty>",
    "Reverse.*",
    ".*BuildInfo.*",
    ".*javascript.*",
    ".*Routes.*",
    ".*GuiceInjector"
  ).mkString(";")
)
