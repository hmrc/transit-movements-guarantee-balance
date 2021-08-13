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

package controllers

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import config.AppConfig
import connectors.FakeNCTSMessageConnector
import controllers.actions.FakeAuthActionProvider
import models.PendingBalanceRequest
import models.request.BalanceRequest
import models.values._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import play.api.http.ContentTypes
import play.api.http.MimeTypes
import play.api.libs.json.JsString
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers._
import repositories.FakeBalanceRequestRepository
import services.BalanceRequestService
import services.XmlFormattingServiceImpl
import services.XmlValidationService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Clock
import java.util.UUID

class BalanceRequestControllerSpec extends AnyFlatSpec with Matchers {

  def mkAppConfig(config: Configuration) = {
    val servicesConfig = new ServicesConfig(config)
    new AppConfig(config, servicesConfig)
  }

  def controller(
    getBalanceRequestResponse: IO[Option[PendingBalanceRequest]] = IO.stub,
    insertBalanceRequestResponse: IO[BalanceId] = IO.stub,
    updateBalanceRequestResponse: IO[Option[PendingBalanceRequest]] = IO.stub,
    sendMessageResponse: IO[Either[UpstreamErrorResponse, Unit]] = IO.stub
  ) = {
    val repository = FakeBalanceRequestRepository(
      getBalanceRequestResponse,
      insertBalanceRequestResponse,
      updateBalanceRequestResponse
    )

    val service = new BalanceRequestService(
      repository,
      new XmlFormattingServiceImpl,
      new XmlValidationService,
      FakeNCTSMessageConnector(sendMessageResponse),
      mkAppConfig(Configuration()),
      Clock.systemUTC()
    )

    new BalanceRequestController(
      FakeAuthActionProvider,
      service,
      Helpers.stubControllerComponents(),
      IORuntime.global
    )
  }

  "BalanceRequestController.submitBalanceRequest" should "return 202 when successful" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val result = controller(
      insertBalanceRequestResponse = IO.pure(balanceId),
      sendMessageResponse = IO.unit.map(Right.apply)
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe ACCEPTED
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe JsString("22b9899e-24ee-48e6-a189-97d1f45391c4")
  }

  it should "return 500 when there is an upstream server error" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val result = controller(
      insertBalanceRequestResponse = IO.pure(balanceId),
      sendMessageResponse = IO.pure(Left(UpstreamErrorResponse("Kaboom!!!", 502)))
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(MimeTypes.TEXT)
    contentAsString(result) shouldBe "Internal server error"
  }

  it should "return 500 when there is an upstream client error" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val result = controller(
      insertBalanceRequestResponse = IO.pure(balanceId),
      sendMessageResponse = IO.pure(Left(UpstreamErrorResponse("Arghhh!!!", 400)))
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(MimeTypes.TEXT)
    contentAsString(result) shouldBe "Internal server error"
  }

  it should "return 500 when there is an error inserting into the database" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val result = controller(
      insertBalanceRequestResponse = IO.raiseError(new RuntimeException)
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(MimeTypes.TEXT)
    contentAsString(result) shouldBe "Internal server error"
  }

  it should "return 500 when there is an error sending to NCTS" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val result = controller(
      insertBalanceRequestResponse = IO.pure(balanceId),
      sendMessageResponse = IO.raiseError(new RuntimeException)
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(MimeTypes.TEXT)
    contentAsString(result) shouldBe "Internal server error"
  }

  "BalanceRequestController.getBalanceRequest" should "return 404 when the balance request is not found" in {
    val result = controller().getBalanceRequest(BalanceId(UUID.randomUUID()))(FakeRequest())
    status(result) shouldBe NOT_FOUND
  }
}
