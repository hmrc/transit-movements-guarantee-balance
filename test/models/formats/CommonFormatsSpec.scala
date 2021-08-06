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
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

class CommonFormatsSpec extends AnyFlatSpec with Matchers with CommonFormats {
  "CommonFormats.nonEmptyListFormat" should "write NonEmptyList as JSON array" in {
    Json.toJson(NonEmptyList.one(1)) shouldBe Json.arr(1)
    Json.toJson(NonEmptyList.of(1, 2, 3)) shouldBe Json.arr(1, 2, 3)
  }

  it should "read JSON array into NonEmptyList" in {
    Json.arr(1).as[NonEmptyList[Int]] shouldBe NonEmptyList.one(1)
    Json.arr(1, 2, 3).as[NonEmptyList[Int]] shouldBe NonEmptyList.of(1, 2, 3)
  }

  it should "throw exception when the JSON array is empty" in {
    intercept[IllegalArgumentException] {
      Json.arr().as[NonEmptyList[Int]]
    }
  }
}
