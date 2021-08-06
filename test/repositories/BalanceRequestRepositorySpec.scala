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

package repositories

import config.AppConfig
import models.PendingBalanceRequest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext

class BalanceRequestRepositorySpec
    extends AnyFlatSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[PendingBalanceRequest] {

  implicit val ec = ExecutionContext.global

  override lazy val repository = new BalanceRequestRepository(
    mongoComponent,
    mkAppConfig(Configuration("mongodb.balance-requests.ttl" -> "5 minutes"))
  )

  def mkAppConfig(config: Configuration) = {
    val servicesConfig = new ServicesConfig(config)
    new AppConfig(config, servicesConfig)
  }

  "BalanceRequestRepository" should "have the correct name" in {
    repository.collectionName shouldBe "balance-requests"
  }
}
