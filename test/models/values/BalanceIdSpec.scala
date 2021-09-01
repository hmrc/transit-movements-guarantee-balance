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

package models.values

import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Random
import java.util.UUID

class BalanceIdSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {
  "BalanceId.messageSender" should "extract the first 24 hex characters of the UUID" in forAll {
    uuid: UUID =>
      val uuidHex   = uuid.toString.replaceAll("-", "")
      val balanceId = BalanceId(uuid)
      balanceId.messageSender.hexString shouldBe uuidHex.take(24)
  }

  "BalanceId.next" should "generate a sequential UUID based upon the system time and a random UUID" in {
    val date      = LocalDateTime.of(2021, 9, 1, 9, 32, 31).toInstant(ZoneOffset.UTC)
    val clock     = Clock.fixed(date, ZoneOffset.UTC)
    val random    = new Random(0)
    val balanceId = BalanceId.next(clock, random).unsafeRunSync
    balanceId shouldBe BalanceId(UUID.fromString("612f48af-d4d9-4138-bd93-cb799b3970be"))
    balanceId.value.variant shouldBe 2
    balanceId.value.version shouldBe 4
  }

  "BalanceId.create" should "replace the first 8 hex characters of the UUID with the Unix epoch second" in forAll {
    uuid: UUID =>
      val dateTime     = LocalDateTime.of(2021, 8, 11, 14, 53, 26)
      val instant      = dateTime.toInstant(ZoneOffset.UTC)
      val uuidHex      = uuid.toString.replaceAll("-", "")
      val balanceId    = BalanceId.create(instant, uuid)
      val balanceIdHex = balanceId.value.toString.replaceAll("-", "")
      balanceIdHex.take(8) shouldBe instant.getEpochSecond.toHexString
      balanceIdHex.drop(8) shouldBe uuidHex.drop(8)
  }
}
