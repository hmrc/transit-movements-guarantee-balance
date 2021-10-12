/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package config

import io.lemonlabs.uri.AbsoluteUrl
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration._

class AppConfigSpec extends AnyFlatSpec with Matchers {

  def mkAppConfig(config: Configuration) = {
    val servicesConfig = new ServicesConfig(config)
    new AppConfig(config, servicesConfig)
  }

  "AppConfig" should "deserialize eis-router config" in {
    val appConfig = mkAppConfig(
      Configuration(
        "microservice.services.eis-router.protocol"                                   -> "https",
        "microservice.services.eis-router.host"                                       -> "foo",
        "microservice.services.eis-router.port"                                       -> "101010",
        "microservice.services.eis-router.path"                                       -> "/bar/baz/quu",
        "microservice.services.eis-router.circuit-breaker.max-failures"               -> "5",
        "microservice.services.eis-router.circuit-breaker.call-timeout"               -> "20 seconds",
        "microservice.services.eis-router.circuit-breaker.reset-timeout"              -> "1 second",
        "microservice.services.eis-router.circuit-breaker.max-reset-timeout"          -> "30 seconds",
        "microservice.services.eis-router.circuit-breaker.exponential-backoff-factor" -> "1.5",
        "microservice.services.eis-router.circuit-breaker.random-factor"              -> "0.1"
      )
    )

    appConfig.eisRouterUrl shouldBe AbsoluteUrl.parse("https://foo:101010/bar/baz/quu")

    appConfig.eisRouterCircuitBreakerConfig shouldBe CircuitBreakerConfig(
      maxFailures = 5,
      callTimeout = 20.seconds,
      resetTimeout = 1.second,
      maxResetTimeout = 30.seconds,
      exponentialBackoffFactor = 1.5,
      randomFactor = 0.1
    )
  }

  it should "deserialize balance-request-cache TTL config" in {
    val appConfig = mkAppConfig(Configuration("balance-request-cache.ttl" -> "60 seconds"))
    appConfig.balanceRequestCacheTtl shouldBe 60.seconds
  }

  it should "deserialize balance-request-cache request timeout config" in {
    val appConfig =
      mkAppConfig(Configuration("balance-request-cache.request-timeout" -> "20 seconds"))
    appConfig.balanceRequestTimeout shouldBe 20.seconds
  }

  it should "deserialize Mongo balance-requests collection TTL config" in {
    val appConfig = mkAppConfig(Configuration("mongodb.balance-requests.ttl" -> "2 hours"))
    appConfig.mongoBalanceRequestTtl shouldBe 2.hours
  }

  it should "deserialize features config" in {
    val appConfig = mkAppConfig(
      Configuration(
        "features.fooBar" -> "true",
        "features.bazQuu" -> "false"
      )
    )

    appConfig.features shouldBe Map("fooBar" -> true, "bazQuu" -> false)
  }

  it should "recognise self-check feature config" in {
    val appConfigCheckEnabled = mkAppConfig(Configuration("features.self-check" -> "true"))
    appConfigCheckEnabled.selfCheck shouldBe true

    val appConfigCheckDisabled = mkAppConfig(Configuration("features.self-check" -> "false"))
    appConfigCheckDisabled.selfCheck shouldBe false

    val appConfigCheckImplicitlyDisabled = mkAppConfig(Configuration())
    appConfigCheckImplicitlyDisabled.selfCheck shouldBe false
  }
}
