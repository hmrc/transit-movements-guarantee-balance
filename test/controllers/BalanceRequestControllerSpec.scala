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

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import config.AppConfig
import controllers.actions.FakeAuthActionProvider
import models.BalanceRequestFunctionalError
import models.BalanceRequestResponse
import models.BalanceRequestSuccess
import models.BalanceRequestXmlError
import models.errors.BalanceRequestError
import models.errors.FunctionalError
import models.errors.InternalServiceError
import models.errors.UpstreamServiceError
import models.errors.UpstreamTimeoutError
import models.errors.XmlError
import models.request.BalanceRequest
import models.values._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import play.api.http.ContentTypes
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers._
import services.FakeBalanceRequestCacheService
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.UUID

class BalanceRequestControllerSpec extends AnyFlatSpec with Matchers {

  def mkAppConfig(config: Configuration) = {
    val servicesConfig = new ServicesConfig(config)
    new AppConfig(config, servicesConfig)
  }

  def controller(
    getBalanceResponse: IO[Either[BalanceRequestError, BalanceRequestResponse]] = IO.stub,
    putBalanceResponse: IO[Unit] = IO.unit
  ) = {
    val service = FakeBalanceRequestCacheService(
      getBalanceResponse,
      putBalanceResponse
    )

    new BalanceRequestController(
      FakeAuthActionProvider,
      service,
      Helpers.stubControllerComponents(),
      IORuntime.global
    )
  }

  "BalanceRequestController.submitBalanceRequest" should "return 200 when successful" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val balanceRequestSuccess =
      BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP"))

    val result = controller(
      getBalanceResponse = IO.pure(Right(balanceRequestSuccess))
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe OK
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "status"   -> "SUCCESS",
      "balance"  -> 12345678.9,
      "currency" -> "GBP"
    )
  }

  it should "return 400 when NCTS returns a functional error" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val balanceRequestFunctionalError =
      BalanceRequestFunctionalError(
        NonEmptyList.one(FunctionalError(ErrorType(14), "Foo.Bar(1).Baz", None))
      )

    val result = controller(
      getBalanceResponse = IO.pure(Right(balanceRequestFunctionalError))
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe BAD_REQUEST
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "status" -> "FUNCTIONAL_ERROR",
      "errors" -> Json.arr(
        Json.obj(
          "errorType"    -> 14,
          "errorPointer" -> "Foo.Bar(1).Baz"
        )
      )
    )
  }

  it should "return 500 when NCTS returns an XML error" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val balanceRequestXmlError =
      BalanceRequestXmlError(
        NonEmptyList.one(XmlError(ErrorType(14), "Foo.Bar(1).Baz", None))
      )

    val result = controller(
      getBalanceResponse = IO.pure(Right(balanceRequestXmlError))
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  it should "return 202 and the balance ID to poll when the request times out" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val result = controller(
      getBalanceResponse = IO.pure(Left(UpstreamTimeoutError(balanceId)))
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe ACCEPTED
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe JsString("22b9899e-24ee-48e6-a189-97d1f45391c4")
  }

  it should "return 500 when there is an upstream service error" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val result = controller(
      getBalanceResponse = IO.pure(Left(UpstreamServiceError()))
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  it should "return 500 when there is an internal service error" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val result = controller(
      getBalanceResponse = IO.pure(Left(InternalServiceError()))
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  it should "return 500 when there is a runtime exception" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val result = controller(
      getBalanceResponse = IO.raiseError(new RuntimeException)
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  "BalanceRequestController.getBalanceRequest" should "return 404 when the balance request is not found" in {
    val result = controller().getBalanceRequest(BalanceId(UUID.randomUUID()))(FakeRequest())
    status(result) shouldBe NOT_FOUND
  }
}
