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

package models

import models.request.AuthenticatedRequest
import models.request.BalanceRequest
import models.values._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.test.FakeRequest

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PendingBalanceRequestSpec extends AnyFlatSpec with Matchers {
  val clock = Clock.fixed(Instant.now, ZoneOffset.UTC)

  val requestId   = RequestId(1)
  val internalId  = InternalId("1234567")
  val enrolmentId = EnrolmentId("GB12345678900")

  val taxIdentifier      = TaxIdentifier("GB12345678900")
  val guaranteeReference = GuaranteeReference("05DE3300BE0001067A001017")
  val accessCode         = AccessCode("1234")

  val balanceRequest = BalanceRequest(
    taxIdentifier,
    guaranteeReference,
    accessCode
  )

  val request = AuthenticatedRequest(
    FakeRequest().withBody(balanceRequest),
    internalId,
    enrolmentId
  )

  val pendingBalanceRequest = PendingBalanceRequest(
    requestId,
    clock.instant(),
    internalId,
    enrolmentId,
    taxIdentifier,
    guaranteeReference,
    completedAt = None,
    response = None
  )

  "PendingBalanceRequest.fromRequest" should "populate balance request with the correct data" in {
    PendingBalanceRequest.fromRequest(
      requestId,
      clock.instant(),
      request
    ) shouldBe pendingBalanceRequest
  }
}
