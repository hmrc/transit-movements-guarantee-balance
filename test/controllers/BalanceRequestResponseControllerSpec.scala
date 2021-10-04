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
import config.Constants
import metrics.FakeMetrics
import models.MessageType
import models.SchemaValidationError
import models.errors.BalanceRequestError
import models.errors.UpstreamServiceError
import models.values.BalanceId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers._
import services.FakeBalanceRequestCacheService
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.util.UUID

class BalanceRequestResponseControllerSpec extends AnyFlatSpec with Matchers {

  def controller(updateBalanceResponse: IO[Either[BalanceRequestError, Unit]] = IO.stub) = {
    val service = FakeBalanceRequestCacheService(updateBalanceResponse = updateBalanceResponse)

    new BalanceRequestResponseController(
      service,
      Helpers.stubControllerComponents(),
      IORuntime.global,
      new FakeMetrics
    )
  }

  val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
  val balanceId = BalanceId(uuid)

  "BalanceRequestResponseController" should "return 200 when successful" in {
    val request = FakeRequest()
      .withBody("")
      .withHeaders(Constants.MessageTypeHeader -> MessageType.ResponseQueryOnGuarantees.code)

    val result = controller(
      updateBalanceResponse = IO.unit.map(Right.apply)
    ).updateBalanceRequest(balanceId.messageIdentifier)(request)

    status(result) shouldBe OK
  }

  it should "return 400 when the message type header is missing" in {
    val request = FakeRequest().withBody("")

    val result = controller(
      updateBalanceResponse = IO.raiseError(new Exception)
    ).updateBalanceRequest(balanceId.messageIdentifier)(request)

    status(result) shouldBe BAD_REQUEST
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "BAD_REQUEST",
      "message" -> "Missing or incorrect X-Message-Type header"
    )
  }

  it should "return 400 when there is an error in the request data" in {
    val request = FakeRequest()
      .withBody("")
      .withHeaders(Constants.MessageTypeHeader -> MessageType.ResponseQueryOnGuarantees.code)

    val error = BalanceRequestError.badRequestError(
      "Unable to parse required values from IE037 message"
    )

    val result = controller(
      updateBalanceResponse = IO.pure(Left(error))
    ).updateBalanceRequest(balanceId.messageIdentifier)(request)

    status(result) shouldBe BAD_REQUEST
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "BAD_REQUEST",
      "message" -> "Unable to parse required values from IE037 message"
    )
  }

  it should "return 400 when there is an error while validating the message against the XML schema" in {
    val request = FakeRequest()
      .withBody("")
      .withHeaders(Constants.MessageTypeHeader -> MessageType.ResponseQueryOnGuarantees.code)

    val error = BalanceRequestError.xmlValidationError(
      MessageType.ResponseQueryOnGuarantees,
      NonEmptyList.of(
        SchemaValidationError(0, 1, "Value 'ABC12345' is not facet-valid with respect to pattern"),
        SchemaValidationError(2, 3, "The value 'ABC12345' of element 'DatOfPreMES9' is not valid")
      )
    )

    val result = controller(
      updateBalanceResponse = IO.pure(Left(error))
    ).updateBalanceRequest(balanceId.messageIdentifier)(request)

    status(result) shouldBe BAD_REQUEST
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "SCHEMA_VALIDATION",
      "message" -> "Error while validating IE037 message",
      "errors" -> Json.arr(
        Json.obj(
          "lineNumber"   -> 0,
          "columnNumber" -> 1,
          "message"      -> "Value 'ABC12345' is not facet-valid with respect to pattern"
        ),
        Json.obj(
          "lineNumber"   -> 2,
          "columnNumber" -> 3,
          "message"      -> "The value 'ABC12345' of element 'DatOfPreMES9' is not valid"
        )
      )
    )
  }

  it should "return 404 when the balance request to update cannot be found" in {
    val request = FakeRequest()
      .withBody("")
      .withHeaders(Constants.MessageTypeHeader -> MessageType.ResponseQueryOnGuarantees.code)

    val error = BalanceRequestError.notFoundError(balanceId.messageIdentifier)

    val result = controller(
      updateBalanceResponse = IO.pure(Left(error))
    ).updateBalanceRequest(balanceId.messageIdentifier)(request)

    status(result) shouldBe NOT_FOUND
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "NOT_FOUND",
      "message" -> "The balance request with message identifier MDTP-GUA-22b9899e24ee48e6a18997d1 was not found"
    )
  }

  it should "return 500 when there is an internal service error" in {
    val request = FakeRequest()
      .withBody("")
      .withHeaders(Constants.MessageTypeHeader -> MessageType.ResponseQueryOnGuarantees.code)

    val error = BalanceRequestError.internalServiceError()

    val result = controller(
      updateBalanceResponse = IO.pure(Left(error))
    ).updateBalanceRequest(balanceId.messageIdentifier)(request)

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  it should "return 500 when there is some other unexpected error" in {
    val request = FakeRequest()
      .withBody("")
      .withHeaders(Constants.MessageTypeHeader -> MessageType.ResponseQueryOnGuarantees.code)

    val error = UpstreamServiceError.causedBy(UpstreamErrorResponse("", FORBIDDEN))

    val result = controller(
      updateBalanceResponse = IO.pure(Left(error))
    ).updateBalanceRequest(balanceId.messageIdentifier)(request)

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  it should "return 500 when there is an unhandled exception" in {
    val request = FakeRequest()
      .withBody("")
      .withHeaders(Constants.MessageTypeHeader -> MessageType.ResponseQueryOnGuarantees.code)

    val result = controller(
      updateBalanceResponse = IO.raiseError(new Exception("Kaboom!!!"))
    ).updateBalanceRequest(balanceId.messageIdentifier)(request)

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }
}
