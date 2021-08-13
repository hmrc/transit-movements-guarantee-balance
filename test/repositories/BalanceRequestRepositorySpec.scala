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

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import config.AppConfig
import models.BalanceRequestResponse
import models.ModelGenerators
import models.PendingBalanceRequest
import models.values.BalanceId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.Configuration
import play.api.test.DefaultAwaitTimeout
import play.api.test.FutureAwaits
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoField
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class BalanceRequestRepositorySpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckPropertyChecks
    with ModelGenerators
    with FutureAwaits
    with DefaultAwaitTimeout
    with DefaultPlayMongoRepositorySupport[PendingBalanceRequest] {

  implicit val ec    = ExecutionContext.global
  override val clock = Clock.tickSeconds(ZoneOffset.UTC)

  override lazy val repository = new BalanceRequestRepositoryImpl(
    mongoComponent,
    mkAppConfig(Configuration("mongodb.balance-requests.ttl" -> "5 minutes")),
    clock
  )

  def mkAppConfig(config: Configuration) = {
    val servicesConfig = new ServicesConfig(config)
    new AppConfig(config, servicesConfig)
  }

  "BalanceRequestRepository" should "have the correct name" in {
    repository.collectionName shouldBe "balance-requests"
  }

  it should "round trip pending balance requests" in forAll { requestedAt: Instant =>
    val assertion = for {
      id <- repository.insertBalanceRequest(requestedAt)

      expected = PendingBalanceRequest(
        balanceId = id,
        requestedAt = requestedAt,
        completedAt = None,
        response = None
      )

      actual <- repository.getBalanceRequest(id)

    } yield actual should contain(expected)

    await(assertion.unsafeToFuture())
  }

  it should "update balance requests with responses" in forAll {
    (requestedAt: Instant, response: BalanceRequestResponse) =>
      val assertion = for {
        id <- repository.insertBalanceRequest(requestedAt)

        completedAt = clock.instant().`with`(ChronoField.NANO_OF_SECOND, 0)

        expected = PendingBalanceRequest(
          balanceId = id,
          requestedAt = requestedAt,
          completedAt = Some(completedAt),
          response = Some(response)
        )

        actual <- repository.updateBalanceRequest(
          id.messageSender,
          completedAt,
          response
        )

        _ <-
          if (actual.isEmpty) IO(fail("The balance request to update was not found")) else IO.unit

      } yield actual should contain(expected)

      await(assertion.unsafeToFuture())
  }

  it should "handle concurrent calls without producing duplicate IDs" in {
    val now          = clock.instant()
    val requests     = for (_ <- 1 to 100) yield repository.insertBalanceRequest(now).unsafeToFuture()
    val results      = Future.sequence(requests).futureValue
    val original     = results.toList.sortBy(_.value)
    val deduplicated = results.toSet[BalanceId].toList.sortBy(_.value)
    deduplicated shouldBe original
  }
}
