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

import models.BalanceRequestSuccess
import models.PendingBalanceRequest
import models.values._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MongoFormatsSpec extends AnyFlatSpec with Matchers with MongoFormats {
  val clock = Clock.fixed(Instant.now, ZoneOffset.UTC)

  val pendingBalanceRequest = PendingBalanceRequest(
    RequestId(1),
    clock.instant().minusSeconds(60),
    InternalId("1234567"),
    EnrolmentId("GB12345678900"),
    TaxIdentifier("GB12345678900"),
    GuaranteeReference("05DE3300BE0001067A001017"),
    completedAt = None,
    response = None
  )

  val pendingBalanceRequestJson = Json.obj(
    "_id"                -> 1,
    "requestedAt"        -> clock.instant().minusSeconds(60).toString,
    "userInternalId"     -> "1234567",
    "userEnrolmentId"    -> "GB12345678900",
    "taxIdentifier"      -> "GB12345678900",
    "guaranteeReference" -> "05DE3300BE0001067A001017"
  )

  val successfulBalanceRequest = pendingBalanceRequest.copy(
    completedAt = Some(clock.instant()),
    response = Some(BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP")))
  )

  val successfulBalanceRequestJson = pendingBalanceRequestJson ++ Json.obj(
    "completedAt" -> clock.instant().toString,
    "response" -> Json.obj(
      "status"   -> "SUCCESS",
      "balance"  -> BigDecimal("12345678.90"),
      "currency" -> "GBP"
    )
  )

  "MongoFormats.pendingBalanceRequestFormat" should "write a pending balance request" in {
    Json.toJson(pendingBalanceRequest) shouldBe pendingBalanceRequestJson
  }

  it should "read a pending balance request" in {
    pendingBalanceRequestJson.as[PendingBalanceRequest] shouldBe pendingBalanceRequest
  }

  it should "round trip a pending balance request" in {
    Json.toJson(pendingBalanceRequest).as[PendingBalanceRequest] shouldBe pendingBalanceRequest
  }

  it should "write a successful balance request" in {
    Json.toJson(successfulBalanceRequest) shouldBe successfulBalanceRequestJson
  }

  it should "read a successful balance request" in {
    successfulBalanceRequestJson.as[PendingBalanceRequest] shouldBe successfulBalanceRequest
  }

  it should "round trip a successful balance request" in {
    Json
      .toJson(successfulBalanceRequest)
      .as[PendingBalanceRequest] shouldBe successfulBalanceRequest
  }
}