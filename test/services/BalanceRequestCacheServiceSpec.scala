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
import cats.effect.unsafe.implicits.global
import config.AppConfig
import connectors.FakeEisRouterConnector
import metrics.FakeMetrics
import models.BalanceRequestFunctionalError
import models.BalanceRequestSuccess
import models.MessageType
import models.PendingBalanceRequest
import models.errors.FunctionalError
import models.errors.InternalServiceError
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
import scala.util.Random
import scala.xml.Utility

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
    updateBalanceRequestResponse: IO[Option[PendingBalanceRequest]] = IO.stub,
    validator: XmlValidationService = new XmlValidationServiceImpl,
    parser: XmlParsingService = new XmlParsingServiceImpl,
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
        getBalanceRequestResponse = getBalanceRequestResponse,
        updateBalanceRequestResponse = updateBalanceRequestResponse
      ),
      new XmlFormattingServiceImpl,
      validator,
      parser,
      FakeEisRouterConnector(sendMessageResponse),
      appConfig,
      Clock.systemUTC(),
      new FakeMetrics
    )

    new BalanceRequestCacheServiceImpl(
      appConfig,
      new FakeMetrics,
      balanceRequestService
    )
  }

  val uuid       = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
  val internalId = InternalId("internalId")
  val balanceId  = BalanceId(uuid)

  val balanceRequest = BalanceRequest(
    TaxIdentifier("GB12345678900"),
    GuaranteeReference("05DE3300BE0001067A001017"),
    AccessCode("1234")
  )

  val pendingBalanceRequest = PendingBalanceRequest(
    balanceId,
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

  "BalanceRequestCacheService.getBalance" should "return balance request success response when everything is successful" in {
    val cacheService = service(
      insertBalanceRequestResponse = IO.pure(balanceId),
      sendMessageResponse = IO.unit.map(Right.apply)
    )

    val assertion = for {
      getBalanceFiber <- cacheService.submitBalanceRequest(balanceRequest).start

      _ <- cacheService.putBalance(balanceId, balanceRequestSuccess)

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
      getBalanceFiber <- cacheService.submitBalanceRequest(balanceRequest).start

      _ <- cacheService.putBalance(
        balanceId,
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
      getBalanceFiber <- cacheService.submitBalanceRequest(balanceRequest).start

      _ <- IO.sleep(250.millis)

      _ <- cacheService.putBalance(
        balanceId,
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
      .submitBalanceRequest(balanceRequest)
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
      .submitBalanceRequest(balanceRequest)
      .attempt
      .map {
        _ shouldBe Left(exception)
      }
      .unsafeToFuture()
  }

  it should "return upstream timeout error when cached responses time out" in {
    val cacheService = service(
      insertBalanceRequestResponse = IO.pure(balanceId),
      getBalanceRequestResponse = IO.pure(Some(pendingBalanceRequest)),
      sendMessageResponse = IO.unit.map(Right.apply)
    )

    val assertion = for {
      fiber <- cacheService.submitBalanceRequest(balanceRequest).start

      _ <- IO.sleep(250.millis)

      _ <- cacheService.putBalance(
        balanceId,
        balanceRequestSuccess
      )

      response <- fiber.join

    } yield response shouldBe Outcome.Succeeded(IO.pure(Left(UpstreamTimeoutError(balanceId))))

    assertion.unsafeToFuture()
  }

  "BalanceRequestCacheService.updateBalance" should "return no errors when all goes well" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val balanceRequestSuccess =
      BalanceRequestSuccess(BigDecimal("1212211848.45"), CurrencyCode("GBP"))

    val updatedBalanceRequest = PendingBalanceRequest(
      balanceId,
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("21GB3300BE0001067A001017"),
      Instant.now.minusSeconds(5),
      completedAt = Some(Instant.now),
      Some(balanceRequestSuccess)
    )

    val validResponseXml = Utility
      .trim(
        <CD037A>
          <SynIdeMES1>UNOC</SynIdeMES1>
          <SynVerNumMES2>3</SynVerNumMES2>
          <MesSenMES3>NTA.GB</MesSenMES3>
          <MesRecMES6>MDTP-GUA-00000000000000000000001-01</MesRecMES6>
          <DatOfPreMES9>20210806</DatOfPreMES9>
          <TimOfPreMES10>1505</TimOfPreMES10>
          <IntConRefMES11>{Random.alphanumeric.take(14).mkString}</IntConRefMES11>
          <MesIdeMES19>{Random.alphanumeric.take(14).mkString}</MesIdeMES19>
          <MesTypMES20>GB037A</MesTypMES20>
          <TRAPRIRC1>
            <TINRC159>GB12345678900</TINRC159>
          </TRAPRIRC1>
          <CUSTOFFGUARNT>
            <RefNumRNT1>GB000001</RefNumRNT1>
          </CUSTOFFGUARNT>
          <GUAREF2>
            <GuaRefNumGRNREF21>21GB3300BE0001067A001017</GuaRefNumGRNREF21>
            <AccDatREF24>20210114</AccDatREF24>
            <GuaTypREF22>4</GuaTypREF22>
            <GuaMonCodREF23>1</GuaMonCodREF23>
            <GUAQUE>
              <QueIdeQUE1>2</QueIdeQUE1>
            </GUAQUE>
            <EXPEXP>
              <ExpEXP1>2751.95</ExpEXP1>
              <ExpCouEXP2>2448</ExpCouEXP2>
              <BalEXP3>1212211848.45</BalEXP3>
              <CurEXP4>GBP</CurEXP4>
            </EXPEXP>
          </GUAREF2>
        </CD037A>
      )
      .toString

    val cacheService = service(
      updateBalanceRequestResponse = IO.some(updatedBalanceRequest)
    )

    cacheService
      .updateBalance(
        balanceId.messageIdentifier,
        MessageType.ResponseQueryOnGuarantees,
        validResponseXml
      )
      .map {
        _ shouldBe Right(())
      }
      .unsafeToFuture()
  }

  it should "return internal service error if the underlying balance request is not updated with the response" in {

    val validResponseXml = Utility
      .trim(
        <CD037A>
          <SynIdeMES1>UNOC</SynIdeMES1>
          <SynVerNumMES2>3</SynVerNumMES2>
          <MesSenMES3>NTA.GB</MesSenMES3>
          <MesRecMES6>MDTP-GUA-00000000000000000000001-01</MesRecMES6>
          <DatOfPreMES9>20210806</DatOfPreMES9>
          <TimOfPreMES10>1505</TimOfPreMES10>
          <IntConRefMES11>{Random.alphanumeric.take(14).mkString}</IntConRefMES11>
          <MesIdeMES19>{Random.alphanumeric.take(14).mkString}</MesIdeMES19>
          <MesTypMES20>GB037A</MesTypMES20>
          <TRAPRIRC1>
            <TINRC159>GB12345678900</TINRC159>
          </TRAPRIRC1>
          <CUSTOFFGUARNT>
            <RefNumRNT1>GB000001</RefNumRNT1>
          </CUSTOFFGUARNT>
          <GUAREF2>
            <GuaRefNumGRNREF21>21GB3300BE0001067A001017</GuaRefNumGRNREF21>
            <AccDatREF24>20210114</AccDatREF24>
            <GuaTypREF22>4</GuaTypREF22>
            <GuaMonCodREF23>1</GuaMonCodREF23>
            <GUAQUE>
              <QueIdeQUE1>2</QueIdeQUE1>
            </GUAQUE>
            <EXPEXP>
              <ExpEXP1>2751.95</ExpEXP1>
              <ExpCouEXP2>2448</ExpCouEXP2>
              <BalEXP3>1212211848.45</BalEXP3>
              <CurEXP4>GBP</CurEXP4>
            </EXPEXP>
          </GUAREF2>
        </CD037A>
      )
      .toString

    val cacheService = service(
      updateBalanceRequestResponse = IO.some(pendingBalanceRequest)
    )

    cacheService
      .updateBalance(
        balanceId.messageIdentifier,
        MessageType.ResponseQueryOnGuarantees,
        validResponseXml.toString
      )
      .map {
        _ shouldBe Left(InternalServiceError())
      }
      .unsafeToFuture()
  }

  "BalanceRequestCacheService.getBalance by ID" should "delegate to underlying repository" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val pendingBalanceRequest = PendingBalanceRequest(
      balanceId,
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      Instant.now.minusSeconds(5),
      completedAt = None,
      response = None
    )

    val cacheService = service(
      getBalanceRequestResponse = IO.some(pendingBalanceRequest)
    )

    cacheService
      .getBalance(balanceId)
      .map {
        _ shouldBe Some(pendingBalanceRequest)
      }
      .unsafeToFuture()
  }
}
