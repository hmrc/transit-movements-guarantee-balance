import play.core.PlayVersion
import sbt._

object AppDependencies {
  val catsVersion       = "2.6.1"
  val catsEffectVersion = "3.2.9"
  val catsRetryVersion  = "3.1.0"
  val log4catsVersion   = "2.1.1"
  val bootstrapVersion  = "5.14.0"
  val hmrcMongoVersion  = "0.55.0"

  val compile = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-play-28"        % hmrcMongoVersion,
    "uk.gov.hmrc"            %% "play-json-union-formatter" % "1.15.0-play-28",
    "io.lemonlabs"           %% "scala-uri"                 % "3.5.0",
    "org.typelevel"          %% "cats-core"                 % catsVersion,
    "org.typelevel"          %% "cats-effect"               % catsEffectVersion,
    "org.typelevel"          %% "log4cats-slf4j"            % log4catsVersion,
    "com.github.cb372"       %% "cats-retry"                % catsRetryVersion,
    "com.github.blemale"     %% "scaffeine"                 % "4.1.0",
    "org.scala-lang.modules" %% "scala-java8-compat"        % "1.0.1",
    "io.dropwizard.metrics"   % "metrics-caffeine"          % "4.2.0",
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    "org.reactivemongo"      %% "play2-reactivemongo"       % "0.20.13-play28"
  )

  val test = Seq(
    "org.scalatest"         %% "scalatest"                % "3.2.10",
    "org.scalatestplus"     %% "scalacheck-1-15"          % "3.2.10.0",
    "uk.gov.hmrc"           %% "bootstrap-test-play-28"   % bootstrapVersion,
    "uk.gov.hmrc.mongo"     %% "hmrc-mongo-test-play-28"  % hmrcMongoVersion,
    "com.typesafe.akka"     %% "akka-testkit"             % PlayVersion.akkaVersion,
    "org.mockito"           %% "mockito-scala"            % "1.16.42",
    "com.github.tomakehurst" % "wiremock-jre8-standalone" % "2.31.0",
    "com.vladsch.flexmark"   % "flexmark-all"             % "0.62.2"
  ).map(_ % s"$Test, $IntegrationTest")

  val dependencySchemes = Seq(
    "org.scala-lang.modules" %% "scala-java8-compat" % "always"
  )
}
