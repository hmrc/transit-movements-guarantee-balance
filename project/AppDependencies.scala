import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {
  val catsVersion       = "2.6.1"
  val catsEffectVersion = "3.2.1"
  val catsRetryVersion  = "3.0.0"
  val bootstrapVersion  = "5.9.0"
  val hmrcMongoVersion  = "0.52.0"

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "play-json-union-formatter" % "1.14.0-play-28",
    "io.lemonlabs"      %% "scala-uri"                 % "3.5.0",
    "org.typelevel"     %% "cats-core"                 % catsVersion,
    "org.typelevel"     %% "cats-effect"               % catsEffectVersion,
    "com.github.cb372"  %% "cats-retry"                % catsRetryVersion,
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )

  val test = Seq(
    "org.scalatest"        %% "scalatest"               % "3.2.9",
    "org.scalatestplus"    %% "scalacheck-1-15"         % "3.2.9.0",
    "uk.gov.hmrc"          %% "bootstrap-test-play-28"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"    %% "hmrc-mongo-test-play-28" % hmrcMongoVersion,
    "com.typesafe.akka"    %% "akka-testkit"            % PlayVersion.akkaVersion,
    "io.github.wolfendale" %% "scalacheck-gen-regexp"   % "0.1.3",
    "com.vladsch.flexmark"  % "flexmark-all"            % "0.36.8"
  ).map(_ % s"$Test, $IntegrationTest")
}
