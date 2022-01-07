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

package models.request

import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class ChannelSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  val channelGen = Gen.oneOf(Channel.values)

  "Channel.withName" should "map channel names to a Channel value" in forAll(channelGen) {
    channel =>
      Channel.withName(channel.name) shouldBe Some(channel)
  }

  it should "return None for all other names" in forAll(
    Arbitrary.arbitrary[String].suchThat(!Channel.names.contains(_))
  ) { name =>
    Channel.withName(name) shouldBe None
  }
}
