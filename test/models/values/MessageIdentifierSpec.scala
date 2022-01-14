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

package models.values

import org.scalacheck.Gen
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.mvc.PathBindable

import java.security.SecureRandom

class MessageIdentifierSpec
  extends AnyFlatSpec
  with Matchers
  with EitherValues
  with ScalaCheckPropertyChecks {

  val random = new SecureRandom

  val binder = implicitly[PathBindable[MessageIdentifier]]

  val validIdGen = Gen.stringOfN(24, Gen.hexChar).map("MDTP-GUA-" + _)

  val invalidIdGen = Gen
    .stringOf(Gen.asciiPrintableChar)
    .suchThat(str => MessageIdentifier.MessageIdRegex.findFirstIn(str).isEmpty)

  val randomBytesGen = Gen.delay {
    val bytes = new Array[Byte](12)
    random.nextBytes(bytes)
    Gen.const(bytes)
  }

  "MessageIdentifier" should "be usable as a path parameter when given valid input" in forAll(
    validIdGen
  ) { id =>
    binder.bind("recipient", id).value.hexString shouldBe id.takeRight(24).toLowerCase
  }

  it should "round trip via bind and unbind" in forAll(randomBytesGen) { bytes =>
    val unbound = binder.unbind("recipient", MessageIdentifier(bytes))
    val bound   = binder.bind("recipient", unbound)
    bound.value.value shouldBe bytes
  }

  it should "return an error when given input missing the message sender prefix" in {
    binder.bind("recipient", "22b9899e24ee48e6a18997d1") shouldBe Left(
      "Cannot parse parameter recipient as a message identifier value"
    )
  }

  it should "return an error when given input containing invalid characters" in {
    binder.bind("recipient", "MDTP-GUA-X2b9899e24ee48e6a18997d1") shouldBe Left(
      "Cannot parse parameter recipient as a message identifier value"
    )
  }

  it should "return an error when given input that looks like an arrivals identifier" in {
    binder.bind("recipient", "MDTP-ARR-00000000000000000000001-01") shouldBe Left(
      "Cannot parse parameter recipient as a message identifier value"
    )
  }

  it should "return an error when given input that looks like a departures identifier" in {
    binder.bind("recipient", "MDTP-DEP-00000000000000000000001-01") shouldBe Left(
      "Cannot parse parameter recipient as a message identifier value"
    )
  }
}
