import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

val appName = "transit-movements-guarantee-balance"

lazy val microservice = Project(appName, file("."))
  .configs(IntegrationTest)
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(SbtDistributablesPlugin.publishingSettings)
  .settings(DefaultBuildSettings.integrationTestSettings())
  .settings(inThisBuild(buildSettings))
  .settings(inConfig(IntegrationTest)(ScalafmtPlugin.scalafmtConfigSettings))
  .settings(inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest)))
  .settings(scalacSettings)
  .settings(scoverageSettings)
  .settings(
    majorVersion := 0,
    scalaVersion := "2.12.14",
    resolvers += Resolver.jcenterRepo,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test
  )

lazy val buildSettings = Def.settings(
  scalafixDependencies ++= Seq(
    "com.github.liancheng" %% "organize-imports" % "0.5.0"
  )
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
