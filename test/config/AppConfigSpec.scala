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
        "microservice.services.eis-router.protocol" -> "https",
        "microservice.services.eis-router.host"     -> "foo",
        "microservice.services.eis-router.port"     -> "101010",
        "microservice.services.eis-router.uri"      -> "bar"
      )
    )

    appConfig.eisRouterUrl shouldBe AbsoluteUrl.parse("https://foo:101010/bar")
  }

  it should "deserialize enrolment config" in {
    val appConfig = mkAppConfig(
      Configuration(
        "auth.enrolmentKey"        -> "HMRC-TEST-ORG",
        "auth.enrolmentIdentifier" -> "FooBarIdentifier"
      )
    )

    appConfig.enrolmentKey shouldBe "HMRC-TEST-ORG"
    appConfig.enrolmentIdentifier shouldBe "FooBarIdentifier"
  }

  it should "deserialize Mongo balance-requests collection TTL config" in {
    val appConfig = mkAppConfig(Configuration("mongodb.balance-requests.ttl" -> "2 hours"))
    appConfig.mongoBalanceRequestTtl shouldBe 2.hours
  }
}
