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

package services

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Outcome
import cats.effect.kernel.Ref
import cats.effect.unsafe.implicits.global
import config.AppConfig
import connectors.FakeNCTSMessageConnector
import metrics.FakeMetrics
import models.BalanceRequestFunctionalError
import models.BalanceRequestSuccess
import models.PendingBalanceRequest
import models.errors.FunctionalError
import models.errors.UpstreamTimeoutError
import models.request.BalanceRequest
import models.values._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import repositories.FakeBalanceRequestRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Clock
import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class BalanceRequestCacheServiceSpec extends AsyncFlatSpec with Matchers {

  implicit val ec = ExecutionContext.global
  implicit val hc = HeaderCarrier()

  def mkAppConfig(config: Configuration) = {
    val servicesConfig = new ServicesConfig(config)
    new AppConfig(config, servicesConfig)
  }

  def service(
    insertBalanceRequestResponse: IO[BalanceId] = IO.stub,
    sendMessageResponse: IO[Either[UpstreamErrorResponse, Unit]] = IO.stub,
    getBalanceRequestResponse: IO[Option[PendingBalanceRequest]] = IO.stub,
    appConfig: AppConfig = mkAppConfig(
      Configuration(
        "balance-request-cache.ttl"             -> "60 seconds",
        "balance-request-cache.request-timeout" -> "200 milliseconds"
      )
    )
  ) = {
    val balanceRequestService = new BalanceRequestService(
      FakeBalanceRequestRepository(
        insertBalanceRequestResponse = insertBalanceRequestResponse,
        getBalanceRequestResponse = getBalanceRequestResponse
      ),
      new XmlFormattingServiceImpl,
      new XmlValidationService,
      FakeNCTSMessageConnector(sendMessageResponse),
      appConfig,
      Clock.systemUTC()
    )

    new BalanceRequestCacheServiceImpl(
      appConfig,
      new FakeMetrics,
      balanceRequestService
    )
  }

  val uuid        = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
  val internalId  = InternalId("internalId")
  val enrolmentId = EnrolmentId("12345678ABC")
  val balanceId   = BalanceId(uuid)

  val balanceRequest = BalanceRequest(
    TaxIdentifier("GB12345678900"),
    GuaranteeReference("05DE3300BE0001067A001017"),
    AccessCode("1234")
  )

  val pendingBalanceRequest = PendingBalanceRequest(
    balanceId,
    enrolmentId,
    balanceRequest.taxIdentifier,
    balanceRequest.guaranteeReference,
    Instant.now,
    completedAt = None,
    response = None
  )

  val balanceRequestSuccess =
    BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP"))

  val balanceRequestFunctionalError =
    BalanceRequestFunctionalError(
      NonEmptyList.one(FunctionalError(ErrorType(14), "Foo.Bar(1).Baz", None))
    )

  "BalanceRequestCacheService" should "return balance request success response when everything is successful" in {
    val cacheService = service(
      insertBalanceRequestResponse = IO.pure(balanceId),
      sendMessageResponse = IO.unit.map(Right.apply)
    )

    val assertion = for {
      getBalanceFiber <- cacheService.getBalance(enrolmentId, balanceRequest).start

      _ <- cacheService.putBalance(
        enrolmentId,
        balanceRequest.taxIdentifier,
        balanceRequest.guaranteeReference,
        balanceRequestSuccess
      )

      outcome <- getBalanceFiber.join

    } yield outcome shouldBe Outcome.Succeeded(IO.pure(Right(balanceRequestSuccess)))

    assertion.unsafeToFuture()
  }

  it should "return balance request error response when a functional error is returned" in {
    val cacheService = service(
      insertBalanceRequestResponse = IO.pure(balanceId),
      sendMessageResponse = IO.unit.map(Right.apply)
    )

    val assertion = for {
      getBalanceFiber <- cacheService.getBalance(enrolmentId, balanceRequest).start

      _ <- cacheService.putBalance(
        enrolmentId,
        balanceRequest.taxIdentifier,
        balanceRequest.guaranteeReference,
        balanceRequestFunctionalError
      )

      outcome <- getBalanceFiber.join

    } yield outcome shouldBe Outcome.Succeeded(IO.pure(Right(balanceRequestFunctionalError)))

    assertion.unsafeToFuture()
  }

  it should "return an upstream timeout error when the response takes longer than the request timeout" in {
    val cacheService = service(
      insertBalanceRequestResponse = IO.pure(balanceId),
      sendMessageResponse = IO.unit.map(Right.apply)
    )

    val assertion = for {
      getBalanceFiber <- cacheService.getBalance(enrolmentId, balanceRequest).start

      _ <- IO.sleep(250.millis)

      _ <- cacheService.putBalance(
        enrolmentId,
        balanceRequest.taxIdentifier,
        balanceRequest.guaranteeReference,
        balanceRequestSuccess
      )

      outcome <- getBalanceFiber.join

    } yield outcome shouldBe Outcome.Succeeded(IO.pure(Left(UpstreamTimeoutError(balanceId))))

    assertion.unsafeToFuture()
  }

  it should "propagate runtime exception when inserting into the database" in {
    val exception = new RuntimeException

    val cacheService = service(
      insertBalanceRequestResponse = IO.raiseError(exception)
    )

    cacheService
      .getBalance(enrolmentId, balanceRequest)
      .attempt
      .map {
        _ shouldBe Left(exception)
      }
      .unsafeToFuture()
  }

  it should "propagate runtime exception when sending the message to NCTS" in {
    val exception = new RuntimeException

    val cacheService = service(
      insertBalanceRequestResponse = IO.pure(balanceId),
      sendMessageResponse = IO.raiseError(exception)
    )

    cacheService
      .getBalance(enrolmentId, balanceRequest)
      .attempt
      .map {
        _ shouldBe Left(exception)
      }
      .unsafeToFuture()
  }

  it should "not call the underlying services when a result is already cached" in {
    val cacheService = service(
      insertBalanceRequestResponse = IO.raiseError(new RuntimeException),
      getBalanceRequestResponse = IO.pure(Some(pendingBalanceRequest)),
      sendMessageResponse = IO.raiseError(new RuntimeException)
    )

    val assertion = for {
      _ <- cacheService.putBalance(
        enrolmentId,
        balanceRequest.taxIdentifier,
        balanceRequest.guaranteeReference,
        balanceRequestSuccess
      )

      response <- cacheService.getBalance(enrolmentId, balanceRequest)

    } yield response shouldBe Right(balanceRequestSuccess)

    assertion.unsafeToFuture()
  }

  it should "not call the underlying services when there is already a pending request" in {
    val insertRef = Ref.unsafe[IO, Boolean](false)
    val sendRef   = Ref.unsafe[IO, Boolean](false)

    val cacheService = service(
      getBalanceRequestResponse = IO.pure(Some(pendingBalanceRequest)),
      insertBalanceRequestResponse = for {
        pending  <- insertRef.getAndUpdate(_ => true)
        response <- if (pending) IO.raiseError(new RuntimeException) else IO.pure(balanceId)
      } yield response,
      sendMessageResponse = for {
        pending  <- sendRef.getAndUpdate(_ => true)
        response <- if (pending) IO.raiseError(new RuntimeException) else IO.unit.map(Right.apply)
      } yield response
    )

    val assertion = for {
      fiber1 <- cacheService.getBalance(enrolmentId, balanceRequest).start

      fiber2 <- cacheService.getBalance(enrolmentId, balanceRequest).start

      _ <- cacheService.putBalance(
        enrolmentId,
        balanceRequest.taxIdentifier,
        balanceRequest.guaranteeReference,
        balanceRequestSuccess
      )

      response1 <- fiber1.join

      response2 <- fiber2.join

    } yield (response1, response2).shouldBe(
      (
        Outcome.Succeeded(IO.pure(Right(balanceRequestSuccess))),
        Outcome.Succeeded(IO.pure(Right(balanceRequestSuccess)))
      )
    )

    assertion.unsafeToFuture()
  }

  it should "return upstream timeout error when cached responses time out" in {
    val cacheService = service(
      insertBalanceRequestResponse = IO.pure(balanceId),
      getBalanceRequestResponse = IO.pure(Some(pendingBalanceRequest)),
      sendMessageResponse = IO.unit.map(Right.apply)
    )

    val assertion = for {
      fiber1 <- cacheService.getBalance(enrolmentId, balanceRequest).start

      fiber2 <- cacheService.getBalance(enrolmentId, balanceRequest).start

      _ <- IO.sleep(250.millis)

      _ <- cacheService.putBalance(
        enrolmentId,
        balanceRequest.taxIdentifier,
        balanceRequest.guaranteeReference,
        balanceRequestSuccess
      )

      response1 <- fiber1.join

      response2 <- fiber2.join

    } yield (response1, response2).shouldBe(
      (
        Outcome.Succeeded(IO.pure(Left(UpstreamTimeoutError(balanceId)))),
        Outcome.Succeeded(IO.pure(Left(UpstreamTimeoutError(balanceId))))
      )
    )

    assertion.unsafeToFuture()
  }
}
