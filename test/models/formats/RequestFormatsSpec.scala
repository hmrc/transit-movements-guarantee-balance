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

import models.request.BalanceRequest
import models.values.AccessCode
import models.values.GuaranteeReference
import models.values.TaxIdentifier
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json._

class RequestFormatsSpec extends AnyFlatSpec with Matchers {
  val validJson = Json.obj(
    "taxIdentifier"      -> "GB12345678900",
    "guaranteeReference" -> "05DE3300BE0001067A001017",
    "accessCode"         -> "1234"
  )
  val validResult = BalanceRequest(
    TaxIdentifier("GB12345678900"),
    GuaranteeReference("05DE3300BE0001067A001017"),
    AccessCode("1234")
  )

  val invalidJsonMissingProp = Json.obj(
    "taxIdentifier"      -> "GB12345678900",
    "guaranteeReference" -> "05DE3300BE0001067A001017"
  )

  val invalidJsonWrongDataType = Json.obj(
    "taxIdentifier"      -> "GB12345678900",
    "guaranteeReference" -> "05DE3300BE0001067A001017",
    "accessCode"         -> 1234
  )

  "BalanceRequest.balanceRequestReads" should "parse valid JSON as BalanceRequest" in {
    validJson.validate[BalanceRequest] shouldBe JsSuccess(validResult)
  }

  it should "return an error for JSON with a missing property" in {
    invalidJsonMissingProp
      .validate[BalanceRequest] shouldBe JsError(__ \ "accessCode", "error.path.missing")
  }

  it should "return an error for JSON using the wrong data type for a property" in {
    invalidJsonWrongDataType
      .validate[BalanceRequest] shouldBe JsError(__ \ "accessCode", "error.expected.jsstring")
  }
}
