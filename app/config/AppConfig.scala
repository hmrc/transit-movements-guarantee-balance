/*
 * Copyright 2022 HM Revenue & Customs
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
import io.lemonlabs.uri.UrlPath
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.duration.FiniteDuration

@Singleton
class AppConfig @Inject() (config: Configuration, servicesConfig: ServicesConfig) {

  private lazy val eisRouterBaseUrl: AbsoluteUrl =
    AbsoluteUrl.parse(servicesConfig.baseUrl("eis-router"))
  private lazy val eisRouterPath: UrlPath =
    UrlPath.parse(config.get[String]("microservice.services.eis-router.path"))
  lazy val eisRouterUrl: AbsoluteUrl =
    eisRouterBaseUrl.withPath(eisRouterPath)

  lazy val balanceRequestTimeout: FiniteDuration =
    config.get[FiniteDuration]("balance-request-cache.request-timeout")
  lazy val balanceRequestCacheTtl: FiniteDuration =
    config.get[FiniteDuration]("balance-request-cache.ttl")

  lazy val mongoBalanceRequestTtl: FiniteDuration =
    config.get[FiniteDuration]("mongodb.balance-requests.ttl")

  lazy val features = config.getOptional[Map[String, Boolean]]("features").getOrElse(Map.empty)

  lazy val selfCheck = features.get("self-check").getOrElse(false)

  lazy val eisRouterCircuitBreakerConfig =
    CircuitBreakerConfig.fromServicesConfig("eis-router", config)
}
