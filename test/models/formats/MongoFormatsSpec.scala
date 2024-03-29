/*
 * Copyright 2022 HM Revenue & Customs
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
import java.time.temporal.ChronoUnit
import java.util.UUID

class MongoFormatsSpec extends AnyFlatSpec with Matchers with MongoFormats {
  val clock = Clock.fixed(Instant.now, ZoneOffset.UTC)

  val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
  val balanceId = BalanceId(uuid)

  val taxIdentifier      = TaxIdentifier("GB12345678900")
  val guaranteeReference = GuaranteeReference("05DE3300BE0001067A001017")

  val pendingBalanceRequest = PendingBalanceRequest(
    balanceId,
    taxIdentifier,
    guaranteeReference,
    clock.instant().minusSeconds(60).truncatedTo(ChronoUnit.MILLIS),
    completedAt = None,
    response = None
  )

  def date(inst: Instant) =
    Json.obj(s"$$date" -> Json.obj("$numberLong" -> inst.toEpochMilli.toString))

  def binary(base64: String, subType: String = "00") =
    Json.obj(s"$$binary" -> Json.obj("base64" -> base64, "subType" -> subType))

  def uuid(base64: String) =
    binary(base64, subType = "04")

  val pendingBalanceRequestJson = Json.obj(
    "_id"                -> uuid(base64 = "IrmJniTuSOahiZfR9FORxA=="),
    "messageIdentifier"  -> binary(base64 = "IrmJniTuSOahiZfR"),
    "taxIdentifier"      -> taxIdentifier.value,
    "guaranteeReference" -> guaranteeReference.value,
    "requestedAt"        -> date(clock.instant().minusSeconds(60))
  )

  val successfulBalanceRequest = pendingBalanceRequest.copy(
    completedAt = Some(clock.instant().truncatedTo(ChronoUnit.MILLIS)),
    response = Some(BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP")))
  )

  val successfulBalanceRequestJson = pendingBalanceRequestJson ++ Json.obj(
    "completedAt" -> date(clock.instant()),
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
