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

import cats.effect.unsafe.IORuntime
import controllers.actions.FakeAuthActionProvider
import models.request.BalanceRequest
import models.values._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers._

import java.util.UUID

class BalanceRequestControllerSpec extends AnyFlatSpec with Matchers {

  val controller = new BalanceRequestController(
    FakeAuthActionProvider,
    Helpers.stubControllerComponents(),
    IORuntime.global
  )

  "BalanceRequestController.submitBalanceRequest" should "return 202 when successful" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val result = controller.submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe ACCEPTED
  }

  "BalanceRequestController.getBalanceRequest" should "return 404 when the balance request is not found" in {
    val result = controller.getBalanceRequest(BalanceId(UUID.randomUUID()))(FakeRequest())
    status(result) shouldBe NOT_FOUND
  }
}
