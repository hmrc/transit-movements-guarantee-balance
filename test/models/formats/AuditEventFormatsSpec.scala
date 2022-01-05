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

package models.formats

import cats.data.NonEmptyList
import models.audit.ErrorResponseEvent
import models.audit.InvalidResponseEvent
import models.audit.RequestEvent
import models.audit.RequestNotFoundEvent
import models.audit.SuccessResponseEvent
import models.request.AuthenticatedRequest
import models.request.BalanceRequest
import models.values.AccessCode
import models.values.BalanceId
import models.values.CurrencyCode
import models.values.GuaranteeReference
import models.values.InternalId
import models.values.TaxIdentifier
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json._
import play.api.test.FakeRequest

import java.util.UUID
import models.audit.ResponseEvent

class AuditEventFormatsSpec extends AnyFlatSpec with Matchers {
  val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
  val balanceId = BalanceId(uuid)

  "RequestEvent" should "serialise to JSON" in {
    val balanceRequest =
      AuthenticatedRequest(
        FakeRequest().withBody(
          BalanceRequest(
            TaxIdentifier("GB12345678900"),
            GuaranteeReference("05DE3300BE0001067A001017"),
            AccessCode("1234")
          )
        ),
        InternalId("ABC123")
      )

    val event = RequestEvent.fromRequest(balanceRequest, balanceId)

    Json.toJsObject(event) shouldBe Json.obj(
      "requestMessageId"   -> "22b9899e24ee48e6a18997d1",
      "userInternalId"     -> "ABC123",
      "eoriNumber"         -> "GB12345678900",
      "guaranteeReference" -> "05DE3300BE0001067A001017",
      "accessCode"         -> "1234"
    )
  }

  "RequestNotFoundEvent" should "serialise to JSON" in {
    val event = RequestNotFoundEvent(balanceId.messageIdentifier)

    Json.toJsObject(event) shouldBe Json.obj(
      "requestMessageId" -> "22b9899e24ee48e6a18997d1"
    )
  }

  "SuccessResponseEvent" should "serialise to JSON" in {
    val event: ResponseEvent = SuccessResponseEvent(
      balanceId.messageIdentifier,
      BigDecimal("12345678.90"),
      CurrencyCode("GBP")
    )

    Json.toJsObject(event) shouldBe Json.obj(
      "requestMessageId" -> "22b9899e24ee48e6a18997d1",
      "balance"          -> 12345678.9,
      "currency"         -> "GBP"
    )
  }

  "ErrorResponseEvent" should "serialise to JSON" in {
    val event: ResponseEvent = ErrorResponseEvent(
      balanceId.messageIdentifier,
      NonEmptyList.one("Incorrect Access Code")
    )

    Json.toJsObject(event) shouldBe Json.obj(
      "requestMessageId" -> "22b9899e24ee48e6a18997d1",
      "errors" -> Json.arr(
        "Incorrect Access Code"
      )
    )
  }

  "InvalidResponseEvent" should "serialise to JSON" in {
    val event: ResponseEvent = InvalidResponseEvent(
      balanceId.messageIdentifier,
      "<CD037A></CD037A>",
      "Unable to parse required values from IE037 message"
    )

    Json.toJsObject(event) shouldBe Json.obj(
      "requestMessageId" -> "22b9899e24ee48e6a18997d1",
      "responseMessage"  -> "<CD037A></CD037A>",
      "errorMessage"     -> "Unable to parse required values from IE037 message"
    )
  }
}
