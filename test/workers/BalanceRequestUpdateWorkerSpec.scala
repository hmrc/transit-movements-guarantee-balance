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

package workers

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.testkit.TestKit
import cats.effect.IO
import cats.effect.kernel.Deferred
import cats.effect.unsafe.implicits.global
import config.AppConfig
import models.BalanceRequestResponse
import models.BalanceRequestSuccess
import models.PendingBalanceRequest
import models.request.BalanceRequest
import models.values.AccessCode
import models.values.BalanceId
import models.values.CurrencyCode
import models.values.GuaranteeReference
import models.values.TaxIdentifier
import org.mockito.ArgumentMatchersSugar
import org.mockito.IdiomaticMockito
import org.mockito.captor.ArgCaptor
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import play.api.inject.DefaultApplicationLifecycle
import play.api.test.DefaultAwaitTimeout
import play.api.test.FutureAwaits
import repositories.BalanceRequestRepositoryImpl
import services.BalanceRequestCacheService
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.security.SecureRandom
import java.time.Clock
import java.time.ZoneOffset

class BalanceRequestUpdateWorkerSpec
  extends AnyFlatSpec
  with Matchers
  with FutureAwaits
  with DefaultAwaitTimeout
  with IdiomaticMockito
  with ArgumentMatchersSugar
  with BeforeAndAfterAll
  with DefaultPlayMongoRepositorySupport[PendingBalanceRequest] {

  implicit val system       = ActorSystem(suiteName)
  implicit val materializer = Materializer(system)
  implicit val ec           = materializer.executionContext

  val clock     = Clock.tickSeconds(ZoneOffset.UTC)
  val lifecycle = new DefaultApplicationLifecycle
  val random    = new SecureRandom

  override def afterAll() = {
    await(lifecycle.stop())
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  override lazy val repository = new BalanceRequestRepositoryImpl(
    mongoComponent,
    mkAppConfig(Configuration("mongodb.balance-requests.ttl" -> "5 minutes")),
    clock,
    random
  )

  def mkAppConfig(config: Configuration) = {
    val servicesConfig = new ServicesConfig(config)
    new AppConfig(config, servicesConfig)
  }

  val taxIdentifier      = TaxIdentifier("GB12345678900")
  val guaranteeReference = GuaranteeReference("05DE3300BE0001067A001017")
  val accessCode         = AccessCode("ABC1")

  val balanceRequest = BalanceRequest(
    taxIdentifier,
    guaranteeReference,
    accessCode
  )

  val balanceRequestSuccess = BalanceRequestSuccess(
    BigDecimal("1212211848.45"),
    CurrencyCode("GBP")
  )

  "BalanceRequestUpdateWorker" should "update the balance request cache when a request is updated" in {
    val putBalanceDeferred = Deferred.unsafe[IO, Unit]
    val completeDeferred   = putBalanceDeferred.complete(()).void

    val cacheService = mock[BalanceRequestCacheService]

    cacheService.putBalance(any[BalanceId], any[BalanceRequestResponse]) returns completeDeferred

    new BalanceRequestUpdateWorker(
      cacheService,
      repository,
      lifecycle
    )

    val balanceId = await {
      repository.insertBalanceRequest(balanceRequest, clock.instant()).unsafeToFuture()
    }

    val response = await {
      repository
        .updateBalanceRequest(balanceId.messageIdentifier, clock.instant(), balanceRequestSuccess)
        .unsafeToFuture()
    }

    response shouldBe a[Some[_]]

    await(putBalanceDeferred.get.unsafeToFuture())

    val idCaptor       = ArgCaptor[BalanceId]
    val responseCaptor = ArgCaptor[BalanceRequestResponse]

    cacheService.putBalance(idCaptor.capture, responseCaptor.capture) wasCalled once

    idCaptor.value shouldBe balanceId
    responseCaptor.value shouldBe balanceRequestSuccess
  }
}
