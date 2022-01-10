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
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.util.Random

class UniqueReferenceSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  val MaxValue = BigInt("z" * 14, 36)

  "UniqueReference.next" should "generate a random reference number" in {
    val random    = new Random(0)
    val reference = UniqueReference.next(random).unsafeRunSync
    reference.base36String shouldBe "jh8v8a7z7hzmq3"
  }

  "UniqueReference.base36String" should "pad the reference number to 14 chars" in {
    UniqueReference(BigInt(0)).base36String shouldBe "00000000000000"

    UniqueReference(BigInt(1)).base36String shouldBe "00000000000001"

    forAll(Gen.choose(BigInt(0), MaxValue)) { num =>
      UniqueReference(num).base36String.length shouldBe 14
    }
  }
}
